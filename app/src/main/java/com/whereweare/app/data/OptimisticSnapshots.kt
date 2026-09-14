package com.whereweare.app.data

import com.whereweare.app.domain.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class OptimisticSnapshots {
    data class Change(val user: String,val apply: (Snapshot)->Snapshot,val confirmed: (Snapshot)->Boolean)
    private val pending=MutableStateFlow<Map<String,Change>>(emptyMap())
    val changes=pending.asStateFlow()
    fun begin(user: String,apply: (Snapshot)->Snapshot,confirmed: (Snapshot)->Boolean): String {
        val key=UUID.randomUUID().toString()
        pending.update {it+(key to Change(user,apply,confirmed))}
        return key
    }
    fun rollback(key: String) {pending.update {it-key}}
    fun render(base: Snapshot,edits: Map<String,Change> = pending.value): Snapshot {
        val relevant=edits.filterValues {it.user==base.profile?.id}
        val completed=relevant.filterValues {it.confirmed(base)}.keys
        // Retire only against the newly received base, never against our optimistic output.
        if(completed.isNotEmpty()) pending.update {it-completed}
        return relevant.filterKeys {it !in completed}.values.fold(base) {value,edit -> edit.apply(value)}
    }
}
