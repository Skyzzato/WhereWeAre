package com.whereweare.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whereweare.app.data.AvatarRepository
import com.whereweare.app.domain.*
import com.whereweare.app.ui.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Local rendering smoke checks; not a substitute for two-device interaction tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V043RenderingTest {
    @Test fun persistentSosOutcomeRendersInBothLanguagesThemesAndFontSizes() {
        for(language in listOf("it","en")) for(dark in listOf(false,true)) for(scale in listOf(1f,1.6f)) for(outcome in listOf(SosSendState.UNKNOWN,SosSendState.FAILED)) {
            val config=RuntimeEnvironment.getApplication().resources.configuration
            config.fontScale=scale
            @Suppress("DEPRECATION")
            RuntimeEnvironment.getApplication().resources.updateConfiguration(config,null)
            Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
                val activity=controller.get()
                Strings.configure(localizedActivityContext(activity,language))
                activity.setContent {
                    MaterialTheme(colorScheme=appColors(if(dark) "dark" else "default")) {
                        Surface(Modifier.fillMaxSize()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            SosOutcomeCard(outcome,null,{}, {})
                            Text(Strings.text(R.string.nearby_permanent))
                            GroupIdentity("🌲","Luogo personalizzato")

                        }}
                    }
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                val view=activity.window.decorView
                view.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY))
                view.layout(0,0,360,800)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                val bitmap=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val output=File("build/reports/v043-ui/$language-${if(dark) "dark" else "light"}-$scale-$outcome.png")
                output.parentFile!!.mkdirs();output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                org.junit.Assert.assertTrue("Rendered content must not be a blank image",(0 until 800 step 5).flatMap {y -> (0 until 360 step 5).map {x -> bitmap.getPixel(x,y)}}.distinct().size>10)
                bitmap.recycle()
            }
        }
    }
}
