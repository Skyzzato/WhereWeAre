package com.whereweare.app.domain

enum class SharingUiState { OFF, STARTING, ON, STOPPING, REMOTE_ACTIVE }
fun sharingUiState(active: Boolean,starting: Boolean,remote: Boolean,stopping: Boolean)=when {
    stopping -> SharingUiState.STOPPING
    starting -> SharingUiState.STARTING
    active -> SharingUiState.ON
    remote -> SharingUiState.REMOTE_ACTIVE
    else -> SharingUiState.OFF
}
