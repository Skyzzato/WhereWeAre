import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
val config = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(key: String, fallback: String = "") = (config.getProperty(key) ?: fallback)
    .replace("\\", "\\\\").replace("\"", "\\\"")
android {
    namespace = "com.whereweare.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.whereweare.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 17
        versionName = "0.48"
        buildConfigField("String", "INVITE_BASE_URL", "\"${setting("INVITE_BASE_URL")}\"")
        buildConfigField("String", "ROUTING_PROVIDER", "\"${setting("ROUTING_PROVIDER")}\"")
        buildConfigField("String", "ROUTING_ENDPOINT", "\"${setting("ROUTING_ENDPOINT")}\"")
        val inviteOrigin=config.getProperty("INVITE_BASE_URL", "").takeIf {it.startsWith("https://")}?.let {URI(it).host}
        manifestPlaceholders["inviteHost"] = inviteOrigin ?: "invites"
        manifestPlaceholders["inviteScheme"] = if(inviteOrigin!=null) "https" else "whereweare-inactive"
        manifestPlaceholders["inviteAutoVerify"] = if(inviteOrigin!=null) "true" else "false"
        buildConfigField("String", "SUPABASE_URL", "\"${setting("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${setting("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"${setting("MAP_STYLE_URL", "https://tiles.openfreemap.org/styles/liberty")}\"")
        listOf("FIREBASE_APP_ID","FIREBASE_API_KEY","FIREBASE_PROJECT_ID","FIREBASE_SENDER_ID").forEach { key -> buildConfigField("String",key,"\"${setting(key)}\"") }
    }
    bundle { language { enableSplit=false } }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.material.icons)
    implementation(libs.exif)
    implementation(libs.zxing)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.datastore)
    implementation(libs.work)
    implementation(libs.location)
    implementation(libs.coroutines)
    implementation(libs.coroutines.tasks)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.okhttp)
    implementation(libs.maplibre)
    runtimeOnly(libs.maplibre.runtime)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito)
    testImplementation(libs.coroutines.test)
}
