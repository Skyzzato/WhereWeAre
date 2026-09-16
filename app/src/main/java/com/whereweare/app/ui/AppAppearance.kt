package com.whereweare.app.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.app.Activity
import android.view.ContextThemeWrapper
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import java.util.Locale

fun languageTag(preference: String,system: String)=when(preference) { "it" -> "it"; "en" -> "en"; else -> if(system=="it") "it" else "en" }
fun localizedContext(context: Context,preference: String): Context {
    val locale=Locale.forLanguageTag(languageTag(preference,Resources.getSystem().configuration.locales[0].language))
    return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
}
/** Preserve the Activity in the base-context chain used by Hilt and activity launchers. */
fun localizedActivityContext(activity: Activity,preference: String): Context {
    val locale=Locale.forLanguageTag(languageTag(preference,Resources.getSystem().configuration.locales[0].language))
    return ContextThemeWrapper(activity,activity.theme).apply {
        applyOverrideConfiguration(Configuration(activity.resources.configuration).apply {setLocale(locale)})
    }
}
fun appColors(theme: String): ColorScheme=when(theme) {
    "ocean" -> lightColorScheme(primary=Color(0xFF1467A0),secondary=Color(0xFF526474),tertiary=Color(0xFF406678),primaryContainer=Color(0xFFD0E7FF),onPrimaryContainer=Color(0xFF001D34),secondaryContainer=Color(0xFFD5E5F5),tertiaryContainer=Color(0xFFC4E8FC),surface=Color(0xFFF7FAFF),background=Color(0xFFF7FAFF))
    "sunset" -> lightColorScheme(primary=Color(0xFF944B00),secondary=Color(0xFF765942),background=Color(0xFFFFF8F0),surface=Color(0xFFFFF8F0),primaryContainer=Color(0xFFFFDDB8),onPrimaryContainer=Color(0xFF301400),secondaryContainer=Color(0xFFFFDCC4),tertiary=Color(0xFF775A00),tertiaryContainer=Color(0xFFFFE090))
    "lavender" -> lightColorScheme(primary=Color(0xFF745399),secondary=Color(0xFF655A70),primaryContainer=Color(0xFFEEDBFF),background=Color(0xFFFFFBFF),surface=Color(0xFFFFFBFF))
    "graphite" -> lightColorScheme(primary=Color(0xFF3E5368),secondary=Color(0xFF58616B),background=Color(0xFFF6F7F8),surface=Color(0xFFF6F7F8),primaryContainer=Color(0xFFD5E4F5),onPrimaryContainer=Color(0xFF102030),secondaryContainer=Color(0xFFDCE3EA),tertiary=Color(0xFF52636B),tertiaryContainer=Color(0xFFD5E5ED))
    "dark" -> darkColorScheme(primary=Color(0xFF75DAC8),onPrimary=Color(0xFF00382F),primaryContainer=Color(0xFF005047),onPrimaryContainer=Color(0xFF94F7E3),background=Color(0xFF141A19),surface=Color(0xFF141A19),secondary=Color(0xFFB0CCC5))
    else -> lightColorScheme(primary=Color(0xFF147D73),secondary=Color(0xFF4F635F),tertiary=Color(0xFF52618C),primaryContainer=Color(0xFFA3F2E6),onPrimaryContainer=Color(0xFF00201B),secondaryContainer=Color(0xFFD2E8E2))
}

/** Shared semantic token for starting sharing and map actions, independent of accent theme. */
fun sharingActionColor(dark: Boolean)=appColors(if(dark) "dark" else "default").primary
@androidx.compose.runtime.Composable fun sharingActionColor()=sharingActionColor(MaterialTheme.colorScheme.background == appColors("dark").background)
