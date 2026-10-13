package com.whereweare.app.domain

/** Monotonic deadline; fix updates do not change it. One movement per uninterrupted empty view. */
class EmptyMapTimer {
    private var since: Long?=null
    private var fired=false
    fun reset() {since=null;fired=false}
    fun tick(now: Long,enabled: Boolean,visible: Boolean,hasTarget: Boolean): Boolean {
        if(!enabled || visible || !hasTarget) {reset();return false}
        if(fired) return false
        if(since==null) since=now
        if(now-requireNotNull(since)<20_000) return false
        fired=true;return true
    }
}
