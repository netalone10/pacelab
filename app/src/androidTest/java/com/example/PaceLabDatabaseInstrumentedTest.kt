package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.PaceLabDatabase
import com.example.data.local.UserSettingsEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaceLabDatabaseInstrumentedTest {
    @Test
    fun userSettings_roundTripWithoutChangingStoredZones() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, PaceLabDatabase::class.java).build()
        try {
            val settings = UserSettingsEntity(maxHr = 195, z1Max = 116, z2Max = 136)
            database.dao().saveUserSettings(settings)
            assertEquals(settings, database.dao().getUserSettings())
        } finally {
            database.close()
        }
    }
}
