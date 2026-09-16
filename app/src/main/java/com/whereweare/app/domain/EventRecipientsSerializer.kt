package com.whereweare.app.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

/** PostgreSQL jsonb_agg returns null when an event has no ordinary recipients. */
object EventRecipientsSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if(element==JsonNull) JsonArray(emptyList()) else element
}
