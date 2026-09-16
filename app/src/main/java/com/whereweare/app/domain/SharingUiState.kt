package com.whereweare.app.domain

/** Revision zero is the server's never-initialized state, including migration from older clients. */
fun shouldInitializeSharing(intent: Boolean?,revision: Long,remoteActive: Boolean)=
    !remoteActive && (intent==true || (intent==null && revision==0L))
enum class SharingUiState { OFF, STARTING, ON, STOPPING, REMOTE_ACTIVE, VERIFYING, SUSPENDED }
fun sharingUiState(active: Boolean,starting: Boolean,remote: Boolean,stopping: Boolean,initializing: Boolean=false,suspended: Boolean=false)=when {
    stopping -> SharingUiState.STOPPING
    initializing -> SharingUiState.VERIFYING
    suspended && (active || starting) -> SharingUiState.SUSPENDED
    starting -> SharingUiState.STARTING
    active -> SharingUiState.ON
    remote -> SharingUiState.REMOTE_ACTIVE
    else -> SharingUiState.OFF
}
