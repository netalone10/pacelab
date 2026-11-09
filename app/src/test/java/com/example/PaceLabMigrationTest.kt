package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.PaceLabDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaceLabMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PaceLabDatabase::class.java
    )

    @Test
    fun migration2To3_preservesUserDataAndLeavesHistogramUnprocessed() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            insertUserSettings(this)
            execSQL(
                "INSERT INTO target_race_events VALUES " +
                    "(1, 'Jakarta HM', 20000, 21097.5, 7200, 341, 'Jakarta')"
            )
            execSQL(
                "INSERT INTO plan_states VALUES " +
                    "('2026-11-09_TUESDAY', 1, 5000.0, 360, 20000)"
            )
            execSQL(
                "INSERT INTO cached_sessions VALUES " +
                    "('run-1', 1000, 3601000, 10000.0, 3600, 360, 150, 175, " +
                    "500.0, 25.0, 0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            PaceLabDatabase.MIGRATION_2_3
        ).apply {
            query("SELECT raceName, targetDurationSeconds FROM target_race_events WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Jakarta HM", it.getString(0))
                assertEquals(7200L, it.getLong(1))
            }
            query("SELECT maxHr, z2Max FROM user_settings WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals(195, it.getInt(0))
                assertEquals(136, it.getInt(1))
            }
            query("SELECT isDone, matchedPaceSec FROM plan_states").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
                assertEquals(360, it.getInt(1))
            }
            query("SELECT distanceMeters, hrHistogram FROM cached_sessions WHERE id = 'run-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(10000.0, it.getDouble(0), 0.0)
                assertNull(it.getString(1))
                assertTrue(it.isNull(1))
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'ignored_sessions'").use {
                assertTrue(it.moveToFirst())
            }
            close()
        }
    }

    private fun insertUserSettings(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO user_settings VALUES " +
                "(1, 5000.0, 1800, 145, 50.0, 21097.5, 4, 116, 117, 136, 137, 155, 156, 175, 195)"
        )
    }

    private companion object {
        const val TEST_DATABASE = "pacelab-migration-test"
    }
}
