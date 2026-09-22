package com.cues.app.runtime

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.notification.ZenPolicy
import android.util.Log
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionState
import com.cues.core.model.OwnedResource
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome

/**
 * The only path from an approved cue to the device.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING. This module cannot be
 * compiled in the environment that wrote it — see CLEANUP.md CL-04 — so every
 * call here is a considered reading of the platform docs, not a tested
 * result. The event's first job is proving each of these against the real
 * OS and correcting what's wrong; CL-06 tracks exactly that.
 *
 * Open questions a real device answers, this code does not:
 *  - Whether an [AutomaticZenRule] this app owns coexists cleanly with
 *    another active quiet mode on OriginOS 7, and what interruption filter
 *    reads back correctly afterward.
 *  - Whether `canScheduleExactAlarms()` is granted by default on this build,
 *    and what the actual delivery jitter is under Doze.
 *  - Whether [SessionService] survives the OEM battery policy long enough to
 *    matter.
 *
 * Every branch that cannot be honestly claimed to work returns BLOCKED with
 * a reason instead of a guessed SUCCEEDED — a false success here is worse
 * than a stub, because it would make the receipt lie.
 */
class AndroidActionExecutor(
    private val context: Context,
) : ActionExecutor {

    private val notifications: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val alarms: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome = when (actionId) {
        ActionId.START_FOCUS_TIMER -> startFocusTimer(args, sessionId)
        ActionId.REQUEST_DND -> requestDnd(args, sessionId)
        ActionId.NOTIFY_RESULT -> notifyResult(args)
    }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome = when (resource) {
        OwnedResource.FOCUS_TIMER -> releaseFocusTimer(sessionId)
        OwnedResource.DND_CONTRIBUTION -> releaseDnd(sessionId)
    }

    // ------------------------------------------------------------- timer

    /**
     * Schedules the session's own exact alarm and starts the foreground
     * service that hosts the visible countdown.
     *
     * Uses [AlarmManager.setExactAndAllowWhileIdle] rather than an inexact
     * alarm or WorkManager: the FDD is explicit that a precise countdown must
     * not be claimed on top of periodic-execution machinery. If exact
     * scheduling is unavailable, this says so and does not fall back to a
     * looser mechanism that would misrepresent the deadline.
     */
    private fun startFocusTimer(args: ActionArgs, sessionId: String): ActionOutcome {
        val minutes = (args as? ActionArgs.FocusTimer)?.durationMinutes
            ?: return ActionOutcome(ActionState.FAILED, "No timer duration was supplied.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            return ActionOutcome(
                ActionState.BLOCKED,
                "Exact-alarm scheduling has not been granted, so a precise finish cannot be promised.",
            )
        }

        val deadlineMillis = System.currentTimeMillis() + minutes * 60_000L
        val pendingIntent = deadlinePendingIntent(sessionId)

        return try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMillis, pendingIntent)
            context.startForegroundService(
                Intent(context, SessionService::class.java)
                    .putExtra(SessionService.EXTRA_SESSION_ID, sessionId)
                    .putExtra(SessionService.EXTRA_DEADLINE_MILLIS, deadlineMillis),
            )
            ActionOutcome(ActionState.SUCCEEDED, "Timer set for $minutes minutes.")
        } catch (e: SecurityException) {
            // canScheduleExactAlarms() can race a revoke between the check and
            // the call; treat that race as the same honest BLOCKED outcome.
            ActionOutcome(ActionState.BLOCKED, "Exact-alarm scheduling was refused: ${e.message}.")
        }
    }

    private fun releaseFocusTimer(sessionId: String): ActionOutcome {
        alarms.cancel(deadlinePendingIntent(sessionId))
        context.stopService(Intent(context, SessionService::class.java))
        // Cancelling an alarm that already fired, or a service already
        // stopped, is not an error — the recovery path calls this
        // unconditionally and must not be punished for arriving late.
        return ActionOutcome(ActionState.SUCCEEDED)
    }

    private fun deadlinePendingIntent(sessionId: String): PendingIntent {
        val intent = Intent(context, DeadlineReceiver::class.java)
            .putExtra(DeadlineReceiver.EXTRA_SESSION_ID, sessionId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // Request code keyed on the session id so concurrent sessions (should
        // the core's one-session-per-routine rule ever loosen) never collide.
        return PendingIntent.getBroadcast(context, sessionId.hashCode(), intent, flags)
    }

    // --------------------------------------------------------------- DND

    /**
     * Applies the app's own [AutomaticZenRule] rather than the legacy global
     * setters. Android 15+ restricts direct global-state writes to a caller's
     * own rules — see the FDD's DND ownership section — which is also
     * exactly the ownership boundary this product wants: releasing later
     * disables only this rule, never a mode another app or the user set.
     */
    private fun requestDnd(args: ActionArgs, sessionId: String): ActionOutcome {
        if (!notifications.isNotificationPolicyAccessGranted) {
            return ActionOutcome(ActionState.BLOCKED, "Do Not Disturb access has not been granted.")
        }

        val allowPriority = (args as? ActionArgs.Dnd)?.allowPriority ?: true
        val policy = ZenPolicy.Builder()
            .apply {
                if (allowPriority) {
                    allowPriorityChannels(true)
                    allowReminders(true)
                } else {
                    allowPriorityChannels(false)
                }
            }
            .build()

        val rule = AutomaticZenRule.Builder("Cues focus session", zenRuleConditionUri(sessionId))
            .setZenPolicy(policy)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setOwner(ComponentName(context, AndroidActionExecutor::class.java))
            .setEnabled(true)
            .build()

        return try {
            val ruleId = notifications.addAutomaticZenRule(rule)
            zenRuleIds[sessionId] = ruleId
            ActionOutcome(ActionState.SUCCEEDED, "Quiet rule applied.")
        } catch (e: SecurityException) {
            ActionOutcome(ActionState.BLOCKED, "The system refused the quiet rule: ${e.message}.")
        }
    }

    private fun releaseDnd(sessionId: String): ActionOutcome {
        val ruleId = zenRuleIds.remove(sessionId)
            ?: return ActionOutcome(ActionState.SUCCEEDED, "No rule was held.")

        return try {
            notifications.removeAutomaticZenRule(ruleId)
            ActionOutcome(ActionState.SUCCEEDED)
        } catch (e: SecurityException) {
            ActionOutcome(ActionState.COMPENSATION_FAILED, "Could not release the quiet rule: ${e.message}.")
        }
    }

    private fun zenRuleConditionUri(sessionId: String): Uri =
        Uri.parse("condition://com.cues.android/session/$sessionId")

    // --------------------------------------------------------------- notify

    private fun notifyResult(args: ActionArgs): ActionOutcome {
        val message = (args as? ActionArgs.Notify)?.message
            ?: return ActionOutcome(ActionState.FAILED, "No message was supplied.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notifications.areNotificationsEnabled()
        ) {
            // Explicit per PRS AC-03: notification denial is itself a visible
            // condition, never silently swallowed.
            return ActionOutcome(ActionState.BLOCKED, "Notifications are disabled.")
        }

        Log.i(TAG, "result: $message")
        // The actual posted notification (channel, builder, id) belongs with
        // the rest of the notification UI work, not the action registry's
        // executor. Logged here so the outcome is truthful about what has and
        // has not happened yet.
        return ActionOutcome(ActionState.BLOCKED, "Result notifications are not wired up yet.")
    }

    companion object {
        private const val TAG = "CuesSession"

        // Zen rule ids returned by the platform aren't derivable from the
        // session id, so they're tracked here for release. Lost on process
        // death: a restart's onBoot() reconciliation is expected to fall back
        // to notifications.automaticZenRules and match by owner+name, which
        // is real device work tracked in CLEANUP.md CL-06, not implemented
        // here.
        private val zenRuleIds = mutableMapOf<String, String>()
    }
}
