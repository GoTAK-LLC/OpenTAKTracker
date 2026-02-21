package com.opentak.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.opentak.tracker.data.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startOnBoot = context.dataStore.data.map {
                    it[booleanPreferencesKey("start_on_boot")] ?: false
                }.first()

                if (startOnBoot) {
                    Log.i("OTT/Boot", "Start on boot enabled - launching tracking service")
                    val serviceIntent = Intent(context, TrackingForegroundService::class.java)
                    context.startForegroundService(serviceIntent)
                } else {
                    Log.i("OTT/Boot", "Start on boot disabled - skipping")
                }
            } catch (e: Exception) {
                Log.e("OTT/Boot", "Error checking boot preference", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
