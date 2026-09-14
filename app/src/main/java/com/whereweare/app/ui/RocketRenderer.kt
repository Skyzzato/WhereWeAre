package com.whereweare.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.whereweare.app.domain.*

/** Ballistic trajectory and damped particle velocities, evaluated at actual elapsed time. */
fun DrawScope.drawRocket(style: RocketStyle,progress: Float,particles: List<RocketParticle>) {
    val seconds=progress*style.duration/1000f
    fun position(t: Float)=Offset(style.x(t)*size.width,style.y(t)*size.height)
    val scale=size.minDimension/400f
    if(seconds<style.burstTime) {
        repeat(style.trail) {i ->
            val age=i.toFloat()/style.trail
            val point=position((seconds-age*style.trail/100f).coerceAtLeast(0f))
            drawCircle(Color(0xFFFFAE24).copy(alpha=(1-age)*.7f),(.5f+style.head*(1-age)*.3f)*scale,point)
        }
        val point=position(seconds)
        drawCircle(Color(0xFFFF9E23).copy(alpha=.2f),style.head*2.8f*scale,point)
        drawCircle(Color(0xFFFFE09A),style.head*scale,point)
    } else {
        val elapsed=seconds-style.burstTime
        val remaining=style.duration/1000f-style.burstTime
        val origin=position(style.burstTime)
        particles.forEach {spark ->
            val life=remaining*spark.life
            if(elapsed<life) {
                fun point(t: Float)=origin+Offset(
                    RocketStyles.displacement(spark.vx,style.drag,t)*size.minDimension,
                    (RocketStyles.displacement(spark.vy,style.drag,t)+style.gravity*.15f*t*t)*size.minDimension)
                val color=when {spark.red<style.red -> Color(0xFFEF3A20);spark.red<style.red+(1-style.red)*.3f -> Color(0xFFFF8F20);else -> Color(0xFFFFD447)}
                val alpha=(1-elapsed/life).coerceIn(0f,1f)
                val current=point(elapsed)
                drawLine(color.copy(alpha=alpha*.65f),point((elapsed-style.trail/300f).coerceAtLeast(0f)),current,style.particleSize*scale)
                drawCircle(color.copy(alpha=alpha),style.particleSize*spark.radius*scale,current)
            }
        }
    }
}
