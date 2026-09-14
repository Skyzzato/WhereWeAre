package com.whereweare.app.ui

import android.net.Uri
import com.whereweare.app.BuildConfig
import com.whereweare.app.domain.normalizeInviteCode
import com.whereweare.app.domain.validInviteCode

private fun validOrigin(base: Uri)=base.scheme=="https" && !base.host.isNullOrBlank() && base.userInfo==null && base.port==-1 && base.query==null && base.fragment==null

/** Configured HTTPS origin only. Custom scheme exists for local tests. */
fun parseTypedInvite(uri: Uri?,baseUrl: String=BuildConfig.INVITE_BASE_URL): Pair<String,String>? {
    if(uri==null || uri.query!=null || uri.fragment!=null || uri.userInfo!=null || uri.port!=-1) return null
    val parts=uri.pathSegments
    val type: String
    val raw: String
    if(uri.scheme=="whereweare" && uri.host in setOf("person","group") && parts.size==1) {
        type=uri.host ?: return null;raw=parts[0]
    } else {
        val base=Uri.parse(baseUrl)
        if(baseUrl.isBlank() || !validOrigin(base) || uri.scheme!="https" || uri.host!=base.host) return null
        val prefix=base.pathSegments
        if(parts.size!=prefix.size+2 || parts.take(prefix.size)!=prefix) return null
        type=parts[prefix.size];raw=parts.last()
        if(type !in setOf("person","group")) return null
    }
    if(!raw.matches(Regex("[A-Za-z0-9]{3,4}-?[A-Za-z0-9]{3,4}")) || !validInviteCode(raw)) return null
    return type to normalizeInviteCode(raw)
}
/** No hosted origin: share the usable code, never a misleading custom-scheme URL. */
fun inviteLink(type: String,code: String,baseUrl: String=BuildConfig.INVITE_BASE_URL): String {
    val normalized=normalizeInviteCode(code)
    val base=Uri.parse(baseUrl)
    if(baseUrl.isBlank() || !validOrigin(base)) return normalized
    require(type in setOf("person","group") && validInviteCode(normalized))
    return base.buildUpon().appendPath(type).appendPath(normalized).build().toString()
}

fun copyInviteCode(context: android.content.Context,code: String): Boolean=runCatching {
    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("WhereWeAre",code))
}.isSuccess

fun shareInviteText(context: android.content.Context,text: String): Boolean=runCatching {
    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,text),null))
}.isSuccess
