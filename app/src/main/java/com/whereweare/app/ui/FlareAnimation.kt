package com.whereweare.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.whereweare.app.R
import com.whereweare.app.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

private data class Spark(val angle: Float,val speed: Float,val size: Float,val delay: Float)

@Composable fun FlareAnimation(id: String?,styleId: Int=1,sound: Boolean=true,modifier: Modifier=Modifier.fillMaxSize(),finished: ()->Unit) {
    if(id==null) return
    val style=remember(id) {FlareStyles.get(styleId)}
    val progress=remember(id) {Animatable(0f)}
    val color=MaterialTheme.colorScheme.primary
    val context=LocalContext.current.applicationContext
    val audio=remember(id) {FlareAudio(context)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val done by rememberUpdatedState(finished)
    val enabled by rememberUpdatedState(sound)
    val sparks=remember(id) {val random=Random(id.hashCode());List(style.particles) {i ->
        Spark((2*PI*i/style.particles).toFloat()+(random.nextFloat()-.5f)*style.irregularity,
            1f+(random.nextFloat()-.5f)*style.irregularity, .65f+random.nextFloat()*.7f,random.nextFloat()*style.progressive)
    }}
    DisposableEffect(id,lifecycle) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {audio.close();done()}}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);audio.close()}
    }
    LaunchedEffect(id) {
        fun audible()=enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if(audible()) audio.play(R.raw.flare_launch)
        launch {delay(((style.ascent+style.burstDelay)*style.duration).toLong());if(audible()) audio.play(R.raw.flare_burst)}
        progress.animateTo(1f,tween(style.duration,easing=LinearEasing));done()
    }
    Canvas(modifier) {
        val p=progress.value
        // The v0.31 reference is deliberately unchanged, including its original pixel sizes.
        if(style.id==1) {classicFlare(p,color);return@Canvas}
        fun position(t: Float): Offset {
            val phase=t.coerceIn(0f,1f)
            val rise=phase.pow(style.speedCurve)+(sin(phase*PI*6).toFloat()*style.pulse*.018f)
            return Offset(size.width*(.7f+sin(phase*PI*4).toFloat()*style.sway*phase),size.height*(.95f-style.height*rise))
        }
        val rise=(p/style.ascent).coerceIn(0f,1f)
        val center=position(rise)
        if(p<style.ascent) {
            repeat(style.trail) {i ->
                val tail=i.toFloat()/style.trail
                val point=position((rise-tail*style.trailLength).coerceAtLeast(0f))
                drawCircle(color.copy(alpha=(1-tail)*.55f*style.brightness),style.head*(1-tail)*.45f+ .5f,point)
            }
            val pulse=1+sin(p*PI*20).toFloat()*style.pulse
            drawCircle(color.copy(alpha=.15f*style.brightness),style.head*2.7f*pulse,center)
            drawCircle(color.copy(alpha=style.brightness),style.head*pulse,center)
        } else {
            val start=style.ascent+style.burstDelay
            val burst=((p-start)/(1-start)).coerceIn(0f,1f)
            fun explosion(time: Float,scale: Float) {
                if(time<=0) return
                sparks.forEach {spark ->
                    val t=((time-spark.delay)/(1-spark.delay)).coerceIn(0f,1f)
                    if(t>0) {
                        val radius=size.minDimension*style.radius*scale*spark.speed*(1-(1-t).pow(1.7f))
                        val direction=Offset(cos(spark.angle),sin(spark.angle))
                        val offset=direction*radius+Offset(0f,size.minDimension*style.gravity*t*t)
                        val alpha=((1-t).pow(.8f)*style.brightness).coerceIn(0f,1f)
                        drawLine(color.copy(alpha=alpha*.55f),center+offset-direction*(8f*(1-t)),center+offset,style.particleSize)
                        drawCircle(color.copy(alpha=alpha),style.particleSize*spark.size,center+offset)
                    }
                }
            }
            explosion(burst,1f)
            if(style.secondary>0 && burst>style.secondary) explosion((burst-style.secondary)/(1-style.secondary),.62f)
        }
    }
}

private fun DrawScope.classicFlare(p: Float,color: Color) {
    val center=Offset(size.width*.7f,size.height*(.95f-.65f*(p/.65f).coerceAtMost(1f)))
    if(p<.65f) {drawLine(color.copy(alpha=.5f),center+Offset(-8f,70f),center,5f);drawCircle(color,6f,center)}
    else {val burst=(p-.65f)/.35f;repeat(10) {i -> val angle=i*PI/5;val radius=burst*size.minDimension*.16f
        val ray=Offset(cos(angle).toFloat(),sin(angle).toFloat());drawLine(color.copy(alpha=1f-burst),center+ray*radius*.6f,center+ray*radius,3f)
    };drawCircle(color.copy(alpha=(1f-burst)*.35f),burst*60f,center,style=Stroke(3f))}
}
