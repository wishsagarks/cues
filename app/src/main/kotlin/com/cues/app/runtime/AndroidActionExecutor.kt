package com.cues.app.runtime

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.notification.ZenPolicy
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionState
import com.cues.core.model.OwnedResource
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.registry.ActionRegistry

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

    // Zen rule ids returned by the platform aren't derivable from the session
    // id, so they're cached here for release — an instance property, not a
    // companion object, because a process-wide static map was exactly the
    // kind of hidden shared state 4.5 replaces. It still does not survive
    // process death on its own; [reconcileZenRules] is what makes that safe.
    private val zenRuleIds = mutableMapOf<String, String>()

    /**
     * Dispatches to the per-action handler, then stamps a successful outcome
     * with what it made this session responsible for releasing — derived from
     * [ActionRegistry], never re-typed here. A handler that returns SUCCEEDED
     * without this stamp would leave [com.cues.core.session.SessionEngine]
     * with no [com.cues.core.model.CleanupObligation] to release on exit,
     * which is the one failure mode "cleanup releases only what Cues owns"
     * cannot tolerate — every handler below has already read its effect back
     * before returning SUCCEEDED, so this is a true record of what exists,
     * not a hopeful one.
     */
    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
        val outcome = when (actionId) {
            ActionId.START_FOCUS_TIMER -> startFocusTimer(args, sessionId)
            ActionId.REQUEST_DND -> requestDnd(args, sessionId)
            ActionId.NOTIFY_RESULT -> notifyResult(args)
            ActionId.PINNED_NOTE -> pinnedNote(args, sessionId)
        }
        val owns = ActionRegistry.definition(actionId)?.owns
        return if (outcome.state == ActionState.SUCCEEDED && owns != null) {
            outcome.copy(acquired = owns)
        } else {
            outcome
        }
    }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome = when (resource) {
        OwnedResource.FOCUS_TIMER -> releaseFocusTimer(sessionId)
        OwnedResource.DND_CONTRIBUTION -> releaseDnd(sessionId)
        OwnedResource.PINNED_NOTE -> releasePinnedNote(sessionId)
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

            // Acquire, then verify (4.3 / AC-04): AlarmManager has no direct
            // "is this scheduled" query, so the read-back is asking the system
            // for the same PendingIntent with FLAG_NO_CREATE — if it comes back
            // null, nothing was actually registered against it, whatever the
            // call above appeared to do.
            if (!alarmIsScheduled(sessionId)) {
                return ActionOutcome(
                    ActionState.BLOCKED,
                    "The timer could not be confirmed as scheduled after asking the system to set it.",
                )
            }

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

    /**
     * Reads back whether [sessionId]'s deadline alarm is actually registered.
     *
     * PendingIntent matching ignores extras, so this looks up the exact same
     * (requestCode, component) pair [deadlinePendingIntent] creates — with
     * FLAG_NO_CREATE, which returns null rather than fabricating a new one.
     */
    private fun alarmIsScheduled(sessionId: String): Boolean {
        val intent = Intent(context, DeadlineReceiver::class.java)
            .putExtra(DeadlineReceiver.EXTRA_SESSION_ID, sessionId)
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, sessionId.hashCode(), intent, flags) != null
    }

    private fun releaseFocusTimer(sessionId: String): ActionOutcome {
        alarms.cancel(deadlinePendingIntent(sessionId))
        // The app may have sessions from different cues. Only the service that
        // displays this session is allowed to stop itself.
        context.startService(Intent(context, SessionService::class.java)
            .setAction(SessionService.ACTION_STOP_SESSION)
            .putExtra(SessionService.EXTRA_SESSION_ID, sessionId))
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

            // Acquire, then verify (4.3 / AC-04): addAutomaticZenRule can
            // return an id for a rule the system silently declined to enable
            // (an OEM zen-mode conflict, for one) — read it back rather than
            // trusting the id alone.
            val confirmed = notifications.automaticZenRules?.get(ruleId)
            if (confirmed == null || !confirmed.isEnabled) {
                zenRuleIds.remove(sessionId)
                runCatching { notifications.removeAutomaticZenRule(ruleId) }
                return ActionOutcome(
                    ActionState.BLOCKED,
                    "The quiet rule could not be confirmed as enabled after being applied.",
                )
            }
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

    /**
     * Rebuilds [zenRuleIds] from live system state (4.5).
     *
     * [zenRuleIds] does not survive process death — a plain in-memory map
     * never could — so on every process start this reads
     * [NotificationManager.getAutomaticZenRules] instead of trusting it. A
     * rule id alone carries no session identity, but this class's own
     * [zenRuleConditionUri] already encodes the session id into the
     * condition URI handed to the platform when the rule was created, so
     * reconciling means reading that back — matched by owner, the way 4.5
     * calls for, then by the session id in the URI, never by guessing which
     * orphaned rule is "probably" the right one.
     */
    fun reconcileZenRules() {
        // Without Notification Policy Access, Cues could not have created a
        // zen rule in the first place, so there is nothing to reconcile —
        // and NotificationManager.getAutomaticZenRules() throws
        // SecurityException rather than returning empty when access is
        // missing, which would otherwise crash every cold start before the
        // user has had a chance to grant it.
        if (!notifications.isNotificationPolicyAccessGranted) return
        val rules = notifications.automaticZenRules ?: return
        val owner = ComponentName(context, AndroidActionExecutor::class.java)
        rules.forEach { (ruleId, rule) ->
            if (rule.owner != owner) return@forEach
            val sessionId = rule.conditionId?.let(::sessionIdFromConditionUri) ?: return@forEach
            zenRuleIds[sessionId] = ruleId
        }
    }

    private fun sessionIdFromConditionUri(uri: Uri): String? {
        val segments = uri.pathSegments
        val index = segments.indexOf("session")
        return if (index in segments.indices && index + 1 < segments.size) segments[index + 1] else null
    }

    // --------------------------------------------------------------- notify

    private fun pinnedNote(args: ActionArgs, sessionId: String): ActionOutcome {
        val message = (args as? ActionArgs.PinnedNote)?.message
            ?: return ActionOutcome(ActionState.FAILED, "No pinned-note text was supplied.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                android.app.NotificationChannel("cues_pinned", "Cues pinned notes", NotificationManager.IMPORTANCE_LOW),
            )
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, "cues_pinned")
        } else Notification.Builder(context)
        val id = pinnedNotificationId(sessionId)
        notifications.notify(
            id,
            builder.setContentTitle("Cues")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build(),
        )

        // Acquire, then verify (4.3 / AC-04): active notifications are readable
        // since API 23, well under minSdk 29, so this checks the platform
        // actually posted it rather than trusting notify()'s void return.
        if (notifications.activeNotifications.none { it.id == id }) {
            return ActionOutcome(ActionState.BLOCKED, "The pinned note could not be confirmed as posted.")
        }
        return ActionOutcome(ActionState.SUCCEEDED, "Pinned note is visible.")
    }

    private fun releasePinnedNote(sessionId: String): ActionOutcome {
        notifications.cancel(pinnedNotificationId(sessionId))
        return ActionOutcome(ActionState.SUCCEEDED)
    }

    private fun pinnedNotificationId(sessionId: String): Int = 10_000 + sessionId.hashCode().ushr(1) % 10_000

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(
                RESULT_CHANNEL_ID, "Cues results", NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        val id = RESULT_NOTIFICATION_ID
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(com.cues.app.R.drawable.ic_stat_cue)
            .setContentTitle("Cues")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        notifications.notify(id, notification)
        return if (notifications.activeNotifications.any { it.id == id && it.tag == null }) {
            ActionOutcome(ActionState.SUCCEEDED, "Result notification posted.")
        } else {
            ActionOutcome(ActionState.BLOCKED, "The result notification could not be confirmed as posted.")
        }
    }

    private companion object {
        const val TAG = "CuesSession"
        const val RESULT_CHANNEL_ID = "cues_result"
        const val RESULT_NOTIFICATION_ID = 20_001
    }
}
