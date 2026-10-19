package com.example.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

sealed class HealthConnectStatus {
    object Available : HealthConnectStatus()
    object UpdateRequired : HealthConnectStatus()
    object NotInstalled : HealthConnectStatus()
}

/**
 * Single source of truth for the Health Connect permissions the app asks for.
 * [core] is the minimum needed to list runs; [all] adds heart rate, energy, elevation and history.
 */
object HealthPermissions {
    const val HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    val exercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val distance = HealthPermission.getReadPermission(DistanceRecord::class)
    val heartRate = HealthPermission.getReadPermission(HeartRateRecord::class)
    val calories = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    val elevation = HealthPermission.getReadPermission(ElevationGainedRecord::class)
    val speed = HealthPermission.getReadPermission(SpeedRecord::class)

    val core: Set<String> = setOf(exercise, distance)

    val all: Set<String> = core + setOf(heartRate, speed, calories, elevation, HISTORY)
}
