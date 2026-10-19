package com.example.data.sync

import android.content.Context

/** Small per-device switches for automatic sync. Backup is disabled, so nothing leaves the device. */
object SyncPrefs {
    private const val FILE = "pacelab_sync"
    private const val KEY_AUTO = "auto_sync_enabled"
    private const val KEY_LAST = "last_sync_epoch_ms"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isAutoSyncEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /** Epoch millis of the last successful sync, or 0 if none yet. */
    fun lastSyncEpochMs(context: Context): Long = prefs(context).getLong(KEY_LAST, 0L)

    fun markSynced(context: Context, epochMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST, epochMs).apply()
    }
}
