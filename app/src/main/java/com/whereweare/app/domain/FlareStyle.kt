package com.whereweare.app.domain

/** Normalized coordinates, seconds and bounded particles; one renderer for all presets. */
data class FlareStyle(val id: Int,val duration: Int=1800,val ascent: Float=.55f,val height: Float=.65f,
    val head: Float=5f,val trail: Int=18,val trailLength: Float=.17f,val sway: Float=0f,
    val speedCurve: Float=1f,val radius: Float=.23f,val particles: Int=36,val particleSize: Float=2f,
    val gravity: Float=.08f,val irregularity: Float=.12f,val pulse: Float=0f,
    val burstDelay: Float=0f,val secondary: Float=0f,val progressive: Float=0f,val brightness: Float=.85f,
    val name: String="") { fun nameOrFallback()=name.ifBlank {"Flare $id"} }

object FlareStyles {
    fun normalize(id: Int?)=id?.takeIf {it in 1..50} ?: 1
    val all=listOf(
        FlareStyle(1,1300,ascent=.65f,radius=.16f,particles=10),
        FlareStyle(2,1000,ascent=.42f,radius=.14f,particles=22,speedCurve=.65f),
        FlareStyle(3,2900,ascent=.62f,height=.72f,radius=.36f,particles=48,speedCurve=1.5f),
        FlareStyle(4,1900,sway=.035f,irregularity=.38f,trailLength=.22f),
        FlareStyle(5,1700,head=2.5f,particles=80,particleSize=1.2f,radius=.26f),
        FlareStyle(6,1250,ascent=.7f,head=8f,particles=20,particleSize=3.5f,radius=.2f),
        FlareStyle(7,2100,trail=36,trailLength=.38f,head=3f,particles=28,particleSize=1.5f),
        FlareStyle(8,1350,trail=8,trailLength=.06f,head=6f,brightness=1f,pulse=.3f),
        FlareStyle(9,1900,radius=.4f,particles=12,particleSize=4.5f,gravity=.03f),
        FlareStyle(10,1600,radius=.13f,particles=96,particleSize=1f,irregularity=.2f),
        FlareStyle(11,2300,sway=.075f,trail=28,height=.6f,pulse=.2f),
        FlareStyle(12,1450,ascent=.6f,height=.78f,irregularity=0f,speedCurve=.8f,particles=24),
        FlareStyle(13,2000,irregularity=.8f,radius=.33f,particles=55,gravity=.16f),
        FlareStyle(14,3200,ascent=.32f,radius=.24f,particles=42,particleSize=2.3f,gravity=.04f),
        FlareStyle(15,950,ascent=.55f,radius=.32f,particles=32,particleSize=1.5f,speedCurve=.65f),
        FlareStyle(16,2600,progressive=.45f,radius=.3f,particles=60,ascent=.42f),
        FlareStyle(17,2300,secondary=.38f,burstDelay=.06f,particles=42,radius=.28f),
        FlareStyle(18,2800,ascent=.7f,head=9f,trail=26,trailLength=.25f,speedCurve=1.6f),
        FlareStyle(19,750,ascent=.35f,head=2f,trail=9,radius=.12f,particles=16),
        FlareStyle(20,2000,trail=64,trailLength=.3f,head=4f,particles=44,pulse=.15f),
        FlareStyle(21,2200,trail=6,trailLength=.28f,head=3f,particles=18,brightness=.6f),
        FlareStyle(22,2700,ascent=.4f,gravity=.42f,radius=.27f,particles=52,trail=24),
        FlareStyle(23,2100,irregularity=0f,gravity=0f,particles=48,radius=.3f,particleSize=1.7f),
        FlareStyle(24,2300,irregularity=.95f,sway=.028f,particles=37,radius=.36f,gravity=.22f),
        FlareStyle(25,2450,speedCurve=1.35f,pulse=.45f,sway=.012f,trail=25,irregularity=.3f),
        FlareStyle(26,2600,ascent=.82f,height=.8f,radius=.23f,particles=34,trailLength=.3f),
        FlareStyle(27,3000,ascent=.22f,height=.48f,particles=50,radius=.3f,gravity=.14f),
        FlareStyle(28,1450,height=.4f,head=2f,trail=7,particles=14,radius=.1f,brightness=.5f),
        FlareStyle(29,2400,height=.76f,head=7f,trail=45,particles=80,radius=.4f,brightness=1f),
        FlareStyle(30,3100,height=.78f,trail=56,trailLength=.32f,particles=96,radius=.4f,secondary=.48f,progressive=.18f,gravity=.2f,pulse=.25f)
    )+RocketStyles.all.map {FlareStyle(it.id,it.duration,ascent=it.burstTime/(it.duration/1000f),particles=it.count)}
    fun get(id: Int?)=all[normalize(id)-1]
}
