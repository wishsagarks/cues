package com.cues.app.runtime

import android.app.NotificationManager
import android.content.Context
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
 * NOT YET IMPLEMENTED. Every method returns an honest BLOCKED rather than a
 * fake success, so the app can be run end to end today and will report
 * truthfully that nothing happened. Filling these in is the first job of the
 * event's Saturday spike, against the real OS on the real loaner phone.
 *
 * Things to establish on the device before writing the bodies:
 *
 *  - Whether NotificationManager.isNotificationPolicyAccessGranted is enough,
 *    and how an AutomaticZenRule behaves on OriginOS 6 when another mode is
 *    already quieting the phone.
 *  - Whether canScheduleExactAlarms is granted, and what the actual delivery
 *    delay is. If it is not exact, the UI must stop claiming a precise finish.
 *  - Whether the foreground service survives the OEM battery policy.
 *
 * None of those are knowable from here, and guessing them into code would
 * produce exactly the confident-and-wrong behaviour this project is about
 * avoiding.
 */
class AndroidActionExecutor(
    private val context: Context,
) : ActionExecutor {

    private val notifications by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
        Log.i(TAG, "execute $actionId for $sessionId")

        return when (actionId) {
            ActionId.START_FOCUS_TIMER -> notImplemented(
                "Starting the focus timer needs the exact-alarm and foreground-service work.",
            )

            ActionId.REQUEST_DND -> when {
                !notifications.isNotificationPolicyAccessGranted ->
                    // A real, expected outcome rather than a bug: the user has
                    // not granted policy access, and the receipt should say so.
                    ActionOutcome(ActionState.BLOCKED, "Do Not Disturb access has not been granted.")

                else -> notImplemented("The owned AutomaticZenRule is not built yet.")
            }

            ActionId.NOTIFY_RESULT -> notImplemented("Result notifications are not built yet.")
        }
    }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome {
        Log.i(TAG, "release $resource for $sessionId")

        // Releasing something that was never acquired is a success, not an
        // error. Recovery paths will legitimately try twice.
        return when (resource) {
            OwnedResource.FOCUS_TIMER -> ActionOutcome(ActionState.SUCCEEDED, "Nothing was running.")
            OwnedResource.DND_CONTRIBUTION -> ActionOutcome(ActionState.SUCCEEDED, "No rule was held.")
        }
    }

    private fun notImplemented(what: String) = ActionOutcome(ActionState.BLOCKED, what)

    private companion object {
        const val TAG = "CuesSession"
    }
}
