package com.customgeocache.app.ui.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Vrátí device azimuth (orientace na sever) ve stupních. Používá rotation vector
 * sensor (TYPE_ROTATION_VECTOR), který je dostupný na všech moderních Androidech
 * a kombinuje accelerometer + magnetometer + gyroscope.
 */
@Composable
fun rememberDeviceAzimuth(): State<Float> {
    val context = LocalContext.current
    val state = remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val r = FloatArray(9)
            private val orientation = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(r, event.values)
                SensorManager.getOrientation(r, orientation)
                val azimuthRad = orientation[0]
                state.floatValue = ((Math.toDegrees(azimuthRad.toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    return state
}
