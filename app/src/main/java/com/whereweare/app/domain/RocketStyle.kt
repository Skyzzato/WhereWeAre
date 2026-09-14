package com.whereweare.app.domain

import kotlin.math.*
import kotlin.random.Random

data class RocketStyle(val id: Int,val vx: Float,val up: Float,val gravity: Float,val descent: Float,
    val duration: Int,val count: Int,val spread: Float,val drag: Float,val red: Float,val head: Float,val trail: Int,val particleSize: Float) {
    val apex get()=up/gravity
    val burstTime get()=apex+descent
    fun x(seconds: Float)=.06f+vx*seconds
    fun y(seconds: Float)=.94f-up*seconds+gravity*seconds*seconds/2
}
data class RocketParticle(val vx: Float,val vy: Float,val life: Float,val red: Float,val radius: Float)
object RocketStyles {
    val all=listOf(
        RocketStyle(31,.45f,.92f,.75f,.18f,3000,100,.38f,.7f,.55f,8f,30,2.5f),
        RocketStyle(32,.58f,1.02f,1.05f,.12f,2500,80,.28f,.4f,.2f,10f,24,3.2f),
        RocketStyle(33,.39f,.85f,.6f,.24f,3900,160,.44f,.9f,.75f,7f,42,2f),
        RocketStyle(34,.5f,1.12f,1f,.2f,3200,120,.5f,1.1f,.45f,11f,38,3f),
        RocketStyle(35,.35f,.98f,.7f,.3f,4100,180,.36f,.5f,.85f,9f,46,2.4f),
        RocketStyle(36,.61f,.86f,1.1f,.15f,2400,72,.48f,.35f,.3f,8f,20,4f),
        RocketStyle(37,.42f,1.06f,.82f,.17f,3600,140,.3f,1.3f,.65f,12f,48,3.5f),
        RocketStyle(38,.48f,.8f,.68f,.28f,3400,96,.55f,.85f,.15f,7f,28,2.8f),
        RocketStyle(39,.37f,1f,.68f,.22f,4300,192,.42f,.6f,.5f,10f,44,2.1f),
        RocketStyle(40,.65f,.95f,1.25f,.14f,2600,110,.4f,1.2f,.8f,13f,26,3.3f),
        RocketStyle(41,.43f,.9f,.72f,.25f,3500,88,.6f,.45f,.4f,9f,36,4.2f),
        RocketStyle(42,.52f,1.08f,.95f,.15f,2900,150,.34f,1.5f,.1f,8f,32,2.2f),
        RocketStyle(43,.34f,.95f,.65f,.3f,4400,170,.52f,.7f,.9f,11f,48,2.7f),
        RocketStyle(44,.56f,.88f,.98f,.2f,3100,130,.46f,.3f,.6f,10f,34,3.6f),
        RocketStyle(45,.41f,1.1f,.85f,.19f,3800,100,.65f,.95f,.35f,12f,40,3.8f),
        RocketStyle(46,.6f,1.04f,1.2f,.16f,2700,190,.3f,1.1f,.7f,8f,22,1.9f),
        RocketStyle(47,.46f,.83f,.7f,.32f,4000,145,.48f,.55f,.25f,9f,45,3.1f),
        RocketStyle(48,.38f,1.02f,.72f,.26f,4200,176,.58f,1.3f,.6f,13f,42,2.6f),
        RocketStyle(49,.54f,.97f,1.04f,.21f,3300,164,.4f,.4f,.8f,11f,30,3.4f),
        RocketStyle(50,.44f,1.12f,.87f,.27f,4600,196,.62f,.65f,.45f,14f,48,3f)
    )
    fun get(id: Int)=all[id-31]
    fun flight(id: Int,seed: Int): RocketStyle {
        val random=Random(seed)
        val style=get(id)
        return style.copy(vx=style.vx*(.97f+random.nextFloat()*.06f),up=style.up*(.98f+random.nextFloat()*.04f))
    }
    fun particles(style: RocketStyle,seed: Int): List<RocketParticle> {
        val random=Random(seed)
        return List(style.count) {
            val angle=random.nextFloat()*2f*PI.toFloat()
            val speed=style.spread*(.35f+random.nextFloat()*.8f)
            RocketParticle(cos(angle)*speed,sin(angle)*speed,.65f+random.nextFloat()*.35f,
                random.nextFloat(),.7f+random.nextFloat()*.6f)
        }
    }
    fun displacement(velocity: Float,drag: Float,time: Float)=velocity*(1-exp(-drag*time))/drag
}
