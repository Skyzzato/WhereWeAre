package com.whereweare.app.ui

import android.net.Uri
import com.whereweare.app.domain.normalizeInviteCode

/** Strictly typed invite links; never infer a group from a person code. */
fun parseTypedInvite(uri: Uri?): Pair<String,String>? {
    if(uri==null) return null
    val parts=uri.pathSegments
    val type: String=when {
        (uri.scheme=="whereweare" && uri.host in setOf("person","group")) -> uri.host!!
        (uri.scheme=="https" && uri.host=="whereweare.app" && parts.firstOrNull()=="join" && parts.getOrNull(1) in setOf("person","group")) -> parts[1]
        else -> return null
    }
    val raw=if(uri.scheme=="whereweare") parts.firstOrNull() else parts.getOrNull(2)
    val code=raw?.let(::normalizeInviteCode) ?: return null
    if(code.isBlank() || code.length !in 6..9 || !code.matches(Regex("[A-Z0-9]{3,4}-[A-Z0-9]{3,4}"))) return null
    return type to code
}
fun inviteLink(type: String,code: String): String = "whereweare://$type/${normalizeInviteCode(code)}"
