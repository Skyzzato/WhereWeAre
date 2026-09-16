package com.whereweare.app.data

import com.whereweare.app.domain.Snapshot
import kotlinx.serialization.json.*

/** Clear only affected grants; live status updates keep map continuity. */
fun invalidateRealtime(snapshot: Snapshot, table: String, record: JsonObject, user: String): Snapshot {
    fun text(key: String)=record[key]?.jsonPrimitive?.contentOrNull
    return when(table) {
        "sharing_status" -> if(record["is_sharing"]?.jsonPrimitive?.booleanOrNull==true) snapshot else
            snapshot.copy(locations=snapshot.locations.filter {it.userId==user || (text("user_id")!=null && it.userId!=text("user_id"))})
        "location_shares" -> snapshot.copy(locations=snapshot.locations.filter {it.userId==user || (text("owner_id")!=null && it.userId!=text("owner_id"))},
            events=snapshot.events.filter {it.sender_id==user})
        // Payload-free account revisions can revoke group membership or precision. Fail closed.
        "account_events" -> snapshot.copy(locations=snapshot.locations.filter {it.userId==user},events=snapshot.events.filter {it.sender_id==user})
        else -> snapshot
    }
}
