package com.cues.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cues.app.CuesApplication
import com.cues.app.MainActivity
import com.cues.app.R
import com.cues.app.runtime.LiveSnapshot
import com.cues.core.review.forecastToday
import com.cues.core.session.isLive
import java.time.ZoneId

/** A passive glance surface: it refreshes from lifecycle events, never polling. */
class NowNextWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NowNextWidget::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val app = context.applicationContext as CuesApplication
            val active = app.store.allUnfinished().firstOrNull { it.state.isLive() }
            val next = forecastToday(app.store.all(), app.store.allPatches(), LiveSnapshot.current(context), ZoneId.systemDefault()).firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.now_next_widget).apply {
                setTextViewText(R.id.now_text, active?.let { "Now: cue running" } ?: "Now: no cue running")
                setTextViewText(R.id.next_text, next?.let { "Next: ${it.window}" } ?: "Next: no eligible cue today")
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
