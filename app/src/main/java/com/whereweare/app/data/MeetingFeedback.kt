package com.whereweare.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class FlareEvent(val user: String,val id: String)

/** One shared local/realtime path. Persist the ID before sending the create RPC. */
@Singleton class MeetingFeedback @Inject constructor(private val preferences: PreferencesRepository) {
    private val pending=MutableStateFlow<List<FlareEvent>>(emptyList())
    val events=pending.asStateFlow()
    suspend fun created(user: String,meeting: String): Boolean {
        val event=FlareEvent(user,"$meeting:created")
        if(!preferences.markMeetingSeen(user,event.id)) return false
        pending.update {it+event}
        return true
    }
    fun finish(user: String,id: String) {pending.update {events -> events.filterNot {it.user==user && it.id==id}}}
    fun clear() {pending.value=emptyList()}
}
