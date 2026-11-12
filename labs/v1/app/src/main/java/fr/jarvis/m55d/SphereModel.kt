package fr.jarvis.m55d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure 3D sphere geometry (no Android): Fibonacci points, yaw/pitch rotation, perspective projection. */
object SphereModel {
    data class P3(val x: Float, val y: Float, val z: Float)
    data class P2(val x: Float, val y: Float, val depth: Float, val scale: Float)

    fun fibonacci(n: Int): List<P3> {
        require(n in 1..5000)
        val golden = PI * (3 - sqrt(5.0))
        return List(n) { i ->
            val y = 1 - (i + 0.5) * 2.0 / n
            val r = sqrt(1 - y * y)
            val t = golden * i
            P3((cos(t) * r).toFloat(), y.toFloat(), (sin(t) * r).toFloat())
        }
    }

    fun rotate(p: P3, yaw: Float, pitch: Float): P3 {
        val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        val x1 = p.x * cy + p.z * sy; val z1 = -p.x * sy + p.z * cy
        val y2 = p.y * cp - z1 * sp; val z2 = p.y * sp + z1 * cp
        return P3(x1, y2, z2)
    }

    /** Perspective projection; depth in [-1, 1] (1 = facing the viewer) for painter ordering and alpha. */
    fun project(p: P3, radiusPx: Float, cx: Float, cy: Float, camera: Float = 3.2f): P2 {
        val s = camera / (camera - p.z)
        return P2(cx + p.x * radiusPx * s, cy - p.y * radiusPx * s, p.z, s)
    }

    /** Inertia: angular velocity decays exponentially; returns the new velocity after dtSeconds. */
    fun decay(velocity: Float, dtSeconds: Float, halfLifeSeconds: Float = 0.45f): Float {
        val v = velocity * Math.pow(0.5, (dtSeconds / halfLifeSeconds).toDouble()).toFloat()
        return if (kotlin.math.abs(v) < 0.002f) 0f else v
    }
}
