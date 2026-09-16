package com.whereweare.app.data

import com.whereweare.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AuthRepository @Inject constructor(private val client: SupabaseClient) {
    val accountDeleted=MutableStateFlow(false)
    val session = client.auth.sessionStatus
    val userId get() = client.auth.currentUserOrNull()?.id
    val email get() = client.auth.currentUserOrNull()?.email.orEmpty()
    val createdAt get() = client.auth.currentUserOrNull()?.createdAt?.toString()
    suspend fun awaitSession() {
        val restored=session.first { it !is SessionStatus.Initializing }
        if(restored !is SessionStatus.Authenticated || userId==null) throw SessionUnavailableException()
    }
    private val recovery=SessionRefreshGate()
    suspend fun recoverSession() {
        if(client.auth.currentSessionOrNull()==null) throw SessionUnavailableException()
        recovery.refresh({client.auth.currentSessionOrNull()?.accessToken}) {client.auth.refreshCurrentSession()}
    }
    fun checkConfiguration() { check(BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) { "configuration" } }
    suspend fun login(email: String, password: String) {
        accountDeleted.value=false
        checkConfiguration()
        client.auth.signInWith(Email) { this.email=email.trim(); this.password=password }
    }
    suspend fun register(name: String, email: String, password: String): Boolean {
        checkConfiguration()
        client.auth.signUpWith(Email) {
            this.email=email.trim(); this.password=password
            data=buildJsonObject { put("display_name",name.trim()) }
        }
        return client.auth.currentSessionOrNull() == null
    }
    suspend fun logout() = client.auth.signOut()
    suspend fun clearLocalSession() { client.auth.clearSession(); accountDeleted.value=true }
}
