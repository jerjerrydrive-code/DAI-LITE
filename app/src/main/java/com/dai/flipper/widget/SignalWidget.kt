package com.dai.flipper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dai.flipper.R

/**
 * Signal Quick-Launch Widget
 *
 * Home screen widget for rapid signal triggering:
 * - Quick access to favorite signals
 * - One-tap transmission
 * - Status indicator
 */
class SignalWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TRIGGER_SIGNAL -> {
                val signalId = intent.getStringExtra(EXTRA_SIGNAL_ID)
                val signalType = intent.getStringExtra(EXTRA_SIGNAL_TYPE)
                if (signalId != null && signalType != null) {
                    triggerSignal(context, signalId, signalType)
                }
            }
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, SignalWidget::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    private fun triggerSignal(context: Context, signalId: String, signalType: String) {
        // Send broadcast to BLE service to trigger the signal
        val triggerIntent = Intent(ACTION_EXECUTE_SIGNAL).apply {
            putExtra(EXTRA_SIGNAL_ID, signalId)
            putExtra(EXTRA_SIGNAL_TYPE, signalType)
            setPackage(context.packageName)
        }
        context.sendBroadcast(triggerIntent)

        // Update widget to show triggered state
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, SignalWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (id in appWidgetIds) {
            updateWidgetTriggered(context, appWidgetManager, id, signalId)
        }
    }

    private fun updateWidgetTriggered(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        triggeredSignalId: String
    ) {
        // This would update the widget to show a "triggered" animation/state
        // Implementation depends on the specific layout
    }

    companion object {
        const val ACTION_TRIGGER_SIGNAL = "com.dai.flipper.ACTION_TRIGGER_SIGNAL"
        const val ACTION_EXECUTE_SIGNAL = "com.dai.flipper.ACTION_EXECUTE_SIGNAL"
        const val ACTION_REFRESH = "com.dai.flipper.ACTION_REFRESH_WIDGET"
        const val EXTRA_SIGNAL_ID = "signal_id"
        const val EXTRA_SIGNAL_TYPE = "signal_type"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_signal)

            // Load saved signals for this widget
            val prefs = context.getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE)
            val savedSignals = loadWidgetSignals(prefs)

            // Set up click listeners for each signal slot
            savedSignals.forEachIndexed { index, signal ->
                val buttonId = getButtonId(index)
                if (buttonId != 0 && signal != null) {
                    val intent = Intent(context, SignalWidget::class.java).apply {
                        action = ACTION_TRIGGER_SIGNAL
                        putExtra(EXTRA_SIGNAL_ID, signal.id)
                        putExtra(EXTRA_SIGNAL_TYPE, signal.type)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + index,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(buttonId, pendingIntent)
                    views.setTextViewText(buttonId, signal.name)
                }
            }

            // Set up refresh button
            val refreshIntent = Intent(context, SignalWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getButtonId(index: Int): Int {
            return when (index) {
                0 -> R.id.btn_signal_1
                1 -> R.id.btn_signal_2
                2 -> R.id.btn_signal_3
                3 -> R.id.btn_signal_4
                else -> 0
            }
        }

        private fun loadWidgetSignals(prefs: android.content.SharedPreferences): List<WidgetSignal?> {
            return (0..3).map { index ->
                val id = prefs.getString("signal_${index}_id", null)
                val name = prefs.getString("signal_${index}_name", null)
                val type = prefs.getString("signal_${index}_type", null)
                if (id != null && name != null && type != null) {
                    WidgetSignal(id, name, type)
                } else {
                    null
                }
            }
        }
    }
}

data class WidgetSignal(
    val id: String,
    val name: String,
    val type: String // "subghz", "ir", "nfc", "rfid"
)
