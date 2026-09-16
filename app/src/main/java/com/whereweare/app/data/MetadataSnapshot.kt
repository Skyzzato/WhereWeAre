package com.whereweare.app.data

import com.whereweare.app.domain.Snapshot

/** Authenticated metadata is useful even when the separate location request fails. */
fun applyMetadata(last: Snapshot,m: MetadataDto): Snapshot = last.copy(
    profile=m.profile.domain(),names=m.names.associate {it.user_id to it.display_name},
    requests=m.requests.map {it.domain()},shares=m.shares.map {it.domain()},statuses=m.statuses.map {it.domain()},
    contacts=m.contacts.map {it.domain()}.associateBy {it.id},groups=m.groups.map {it.domain()},members=m.members.map {it.domain()},
    groupRequests=m.group_requests,meetings=m.meetings,savedPeople=m.saved_people.toSet(),
    sharedPrecisionAvailable=m.shared_precision,locationRequestsAvailable=m.location_requests_available,locationRequests=m.location_requests,
    eventsAvailable=m.events_available,events=m.events,temporaryGroupsAvailable=m.temporary_groups_available,
    placesAvailable=m.places_available,sosAvailable=m.sos_available,nearbySosAvailable=m.nearby_sos_available,loading=false
)
