package com.cues.app.runtime

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.pm.PackageInfoCompat
import com.cues.core.model.ScrollDirection
import com.cues.core.model.UiExpectation
import com.cues.core.model.UiMacro
import com.cues.core.model.UiSelector
import com.cues.core.model.UiStep
import com.cues.core.model.UtilityId
import com.cues.core.model.UtilityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * "Cues: iQOO utility bindings" — the accessibility service behind two
 * features, per the FDD's "Utility Bindings" section:
 *
 *  1. **One-shot screen read** ("Cue this screen"): a single, user-invoked
 *     read of the foreground window's visible text, never stored past the
 *     moment it is handed to the conversation as data.
 *  2. **Taught macro replay**: [com.cues.core.model.ActionId.USE_UTILITY]
 *     turns a cataloged utility on or off by replaying a [UiMacro] the user
 *     recorded themselves — never one a model generated. [com.cues.core.compile.MacroValidator]
 *     is the gate between "recorded" and "will ever be replayed"; every macro
 *     is validated before it is saved (see `UtilityBindingScreen`), so this
 *     class only ever replays one that has already passed it.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — same caveat as
 * [AndroidActionExecutor]; see CLEANUP.md. `performPlayback` blocks the
 * calling thread with bounded polling loops (at most ~2s per step, one retry)
 * exactly the way "acquire, then verify" already works elsewhere in this
 * codebase; callers must not invoke it from the main thread.
 */
class CuesAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        _instance = this
    }

    override fun onDestroy() {
        if (_instance === this) _instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (recordingTarget.value == null) return
        val node = event.source ?: return
        val step: UiStep? = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> UiStep.Click(selectorFor(node), expectationFor(node))
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text?.joinToString("").orEmpty()
                if (text.isBlank()) null else UiStep.SetText(selectorFor(node), text, expectationFor(node))
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> UiStep.Scroll(selectorFor(node))
            else -> null
        }
        if (step != null) {
            lastRecordedPackage = event.packageName?.toString() ?: lastRecordedPackage
            _recordedSteps.value = _recordedSteps.value + step
        }
        node.recycle()
    }

    // --------------------------------------------------------- teaching

    private var lastRecordedPackage: String? = null

    /** Builds a macro from whatever has been recorded since [startRecording], or null if nothing was taught. */
    fun buildRecordedMacro(label: String): UiMacro? {
        val steps = _recordedSteps.value
        val packageName = lastRecordedPackage
        if (steps.isEmpty() || packageName == null) return null
        val versionCode = runCatching {
            PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        }.getOrNull() ?: return null
        return UiMacro(
            id = "macro-" + UUID.randomUUID(),
            label = label,
            packageName = packageName,
            versionCode = versionCode,
            steps = steps,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    private fun selectorFor(node: AccessibilityNodeInfo): UiSelector = UiSelector(
        viewIdResourceName = node.viewIdResourceName,
        text = node.text?.toString()?.takeIf { it.isNotBlank() },
        contentDescription = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
        className = node.className?.toString(),
        isPassword = node.isPassword,
    )

    private fun expectationFor(node: AccessibilityNodeInfo): UiExpectation =
        UiExpectation(checked = if (node.isCheckable) node.isChecked else null)

    // ------------------------------------------------------------- replay

    /**
     * Replays [macro] step by step, checking each [UiStep.expect] before
     * moving on. Blocks the calling thread — see the class doc comment.
     */
    fun performPlayback(macro: UiMacro): MacroRunOutcome {
        val installed = try {
            packageManager.getPackageInfo(macro.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return MacroRunOutcome.Blocked("${macro.packageName} is not installed.")
        }
        if (PackageInfoCompat.getLongVersionCode(installed) != macro.versionCode) {
            return MacroRunOutcome.Blocked(
                "MACRO_APP_CHANGED: ${macro.packageName} has updated since this was taught; re-teach it.",
            )
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(macro.packageName)
            ?: return MacroRunOutcome.Blocked("${macro.packageName} has no launcher entry to open.")
        startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // No reliable "app is now in front" signal beyond polling for the
        // first step's own selector, which runStep already does.

        macro.steps.forEachIndexed { index, step ->
            if (!runStep(step)) {
                return MacroRunOutcome.Blocked("MACRO_STEP_FAILED at step ${index + 1} of ${macro.steps.size}.")
            }
        }
        return MacroRunOutcome.Succeeded
    }

    private fun runStep(step: UiStep): Boolean {
        repeat(MAX_ATTEMPTS_PER_STEP) {
            val deadline = SystemClock.elapsedRealtime() + STEP_TIMEOUT_MILLIS
            while (SystemClock.elapsedRealtime() < deadline) {
                val root = rootInActiveWindow
                val node = root?.let { findNode(it, step.selector) }
                if (node != null && act(node, step) && confirms(step)) return true
                SystemClock.sleep(POLL_INTERVAL_MILLIS)
            }
        }
        return false
    }

    private fun act(node: AccessibilityNodeInfo, step: UiStep): Boolean = when (step) {
        is UiStep.Click -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        is UiStep.SetText -> node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
            },
        )
        is UiStep.Scroll -> node.performAction(
            if (step.direction == ScrollDirection.FORWARD) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            },
        )
    }

    private fun confirms(step: UiStep): Boolean {
        SystemClock.sleep(SETTLE_MILLIS)
        val node = rootInActiveWindow?.let { findNode(it, step.selector) } ?: return false
        return node.matchesExpectation(step.expect)
    }

    private fun findNode(root: AccessibilityNodeInfo, selector: UiSelector): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.matchesSelector(selector)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun AccessibilityNodeInfo.matchesSelector(selector: UiSelector): Boolean {
        val wantId = selector.viewIdResourceName
        if (!wantId.isNullOrBlank() && viewIdResourceName != wantId) return false

        val wantText = selector.text
        if (!wantText.isNullOrBlank()) {
            val actual: String? = text?.toString()
            if (actual == null || !actual.contains(wantText, ignoreCase = true)) return false
        }

        val wantDescription = selector.contentDescription
        if (!wantDescription.isNullOrBlank()) {
            val actual: String? = contentDescription?.toString()
            if (actual == null || !actual.contains(wantDescription, ignoreCase = true)) return false
        }

        val wantClass = selector.className
        if (!wantClass.isNullOrBlank() && className?.toString() != wantClass) return false

        return true
    }

    private fun AccessibilityNodeInfo.matchesExpectation(expect: UiExpectation): Boolean {
        if (expect.checked != null && isChecked != expect.checked) return false

        val wantContains = expect.textContains
        if (wantContains != null) {
            val actual: String? = text?.toString()
            if (actual == null || !actual.contains(wantContains, ignoreCase = true)) return false
        }

        return true
    }

    // -------------------------------------------------- one-shot screen read

    /**
     * A single read of the current foreground window's text — never stored
     * past this call. Excludes password nodes explicitly; a `FLAG_SECURE`
     * window's content is not exposed to an accessibility service by the
     * platform in the first place, so there is nothing further to filter
     * for that case beyond treating an empty tree as "nothing readable"
     * rather than an error.
     */
    fun captureScreenText(): ScreenCapture {
        val window = windows.firstOrNull { it.isActive } ?: windows.firstOrNull()
            ?: return ScreenCapture.Refused("No window could be read.")
        val root = window.root ?: return ScreenCapture.Refused("This window's content is not readable.")
        val text = StringBuilder()
        collectText(root, text)
        val result = text.toString().trim()
        return if (result.isBlank()) ScreenCapture.Refused("No readable text was found on screen.") else ScreenCapture.Success(result)
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        if (node.isPassword) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, out) }
    }

    companion object {
        const val EXTRA_SCREEN_CAPTURE = "com.cues.app.EXTRA_SCREEN_CAPTURE"
        private const val STEP_TIMEOUT_MILLIS = 2_000L
        private const val POLL_INTERVAL_MILLIS = 150L
        private const val SETTLE_MILLIS = 150L
        private const val MAX_ATTEMPTS_PER_STEP = 2

        @Volatile private var _instance: CuesAccessibilityService? = null

        fun isRunning(): Boolean = _instance != null

        private val _recordedSteps = MutableStateFlow<List<UiStep>>(emptyList())
        val recordedSteps: StateFlow<List<UiStep>> = _recordedSteps.asStateFlow()

        private val _recordingTarget = MutableStateFlow<Pair<UtilityId, UtilityState>?>(null)
        val recordingTarget: StateFlow<Pair<UtilityId, UtilityState>?> = _recordingTarget.asStateFlow()

        fun startRecording(utilityId: UtilityId, state: UtilityState) {
            _instance?.lastRecordedPackage = null
            _recordedSteps.value = emptyList()
            _recordingTarget.value = utilityId to state
        }

        fun stopRecording() {
            _recordingTarget.value = null
        }

        /** Null when the service isn't running, or nothing was recorded. */
        fun finishRecording(label: String): UiMacro? {
            val macro = _instance?.buildRecordedMacro(label)
            _recordingTarget.value = null
            _recordedSteps.value = emptyList()
            return macro
        }

        fun captureScreenTextNow(): ScreenCapture = _instance?.captureScreenText() ?: ScreenCapture.Unavailable

        /** Blocks the calling thread — see the class doc comment. Never call this from the main thread. */
        fun playMacroNow(macro: UiMacro): MacroRunOutcome =
            _instance?.performPlayback(macro)
                ?: MacroRunOutcome.Blocked("The Cues utility-bindings accessibility service is not running.")
    }
}

sealed interface ScreenCapture {
    data class Success(val text: String) : ScreenCapture
    data class Refused(val reason: String) : ScreenCapture
    data object Unavailable : ScreenCapture
}

sealed interface MacroRunOutcome {
    data object Succeeded : MacroRunOutcome
    data class Blocked(val detail: String) : MacroRunOutcome
}
