package com.whereweare.app.data

import android.content.Context
import android.net.Uri
import com.whereweare.app.ui.parseTypedInvite
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PendingInvite(val id: String,val type: String,val code: String)
@Singleton class InviteStore @Inject constructor(@ApplicationContext context: Context) {
    private val storage=context.getSharedPreferences("pending_invite",0)
    private fun restore(): PendingInvite? {
        val uri=(storage.all["uri"] as? String)?.let(Uri::parse) ?: return null
        val parsed=parseTypedInvite(uri) ?: return null
        return PendingInvite(storage.all["id"] as? String ?: UUID.randomUUID().toString(),parsed.first,parsed.second)
    }
    private val value=MutableStateFlow(restore())
    val pending=value.asStateFlow()
    @Synchronized fun accept(uri: Uri): Boolean {
        val parsed=parseTypedInvite(uri) ?: return false
        val invite=PendingInvite(UUID.randomUUID().toString(),parsed.first,parsed.second)
        if(!storage.edit().putString("id",invite.id).putString("uri","whereweare://${invite.type}/${invite.code}").commit()) return false
        value.value=invite;return true
    }
    @Synchronized fun consume(id: String) {
        if(value.value?.id!=id) return
        storage.edit().clear().commit();value.value=null
    }
}
