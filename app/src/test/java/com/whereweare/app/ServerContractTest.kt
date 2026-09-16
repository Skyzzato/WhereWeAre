package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.Snapshot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlin.reflect.typeOf
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ServerContractTest {
    private fun raw()=javaClass.getResource("/contract/metadata-null-recipients.json")!!.readText()
    private fun decode(raw: String): MetadataDto = runBlocking {
        val client=io.github.jan.supabase.createSupabaseClient("https://example.supabase.co","test") {}
        try {client.defaultSerializer.decode<MetadataDto>(typeOf<MetadataDto>(),raw)} finally {client.close()}
    }
    @Test fun nearbyOnlySosDoesNotBreakTheEntireMetadataResponse() {
        val metadata=decode(raw())
        val snapshot=applyMetadata(Snapshot(),metadata)
        assertTrue(snapshot.sosAvailable)
        assertEquals(2,snapshot.events.size)
        assertTrue(snapshot.events.single {it.id.startsWith("f045")}.recipients.isEmpty())
    }
    @Test fun postgresMetadataDecodesAndConvertsToSnapshot() {
        val raw=javaClass.getResource("/contract/metadata.json")!!.readText()
        val metadata=decode(raw)
        val snapshot=applyMetadata(Snapshot(),metadata)
        assertTrue(snapshot.sosAvailable)
        assertEquals(2,snapshot.contacts.size)
        assertNotNull(Instant.parse(metadata.server_time))
        assertEquals(1,snapshot.events.size)
        assertEquals(listOf("f0440000-0000-0000-0000-000000000002"),snapshot.events.single().recipients)
    }
    @Test fun missingRecipientsStillDefaultsToEmpty() {
        val data=Json.parseToJsonElement(raw()).jsonObject
        val changed=JsonObject(data+("events" to JsonArray(data.getValue("events").jsonArray.map {JsonObject(it.jsonObject-"recipients")})))
        assertTrue(decode(changed.toString()).events.all {it.recipients.isEmpty()})
    }
    @Test fun malformedListsAndOtherRequiredFieldsStillFail() {
        for(replacement in listOf("{}","\"invalid\"","[null]")) {
            assertThrows(SerializationException::class.java) {decode(raw().replace("\"recipients\": null","\"recipients\": $replacement"))}
        }
        assertThrows(SerializationException::class.java) {decode(raw().replace("\"sos_available\": true","\"sos_available\": null"))}
    }
    @Test fun successfulMetadataReadClearsTheDiagnosticError() = runBlocking {
        val diagnostics=ConnectionDiagnostics({0},{Instant.EPOCH})
        try {diagnostics.measure(false,"metadata_decode") {throw SerializationException()}} catch(_: SerializationException) {}
        assertEquals("response",diagnostics.state.value.failure)
        diagnostics.measure(false,"metadata_decode") {decode(raw())}
        assertNull(diagnostics.state.value.failure)
        assertTrue(diagnostics.state.value.failures.isEmpty())
        assertEquals(true,diagnostics.state.value.serverReachable)
    }
}
