package com.whereweare.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w420dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V047RenderingTest {
    @Test fun mapAvatarExtremesAndSemanticActionsRenderInLightAndDark() {
        for(theme in listOf("default","dark")) {
            Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
                val activity=controller.get()
                Strings.configure(localizedActivityContext(activity,"it"))
                val repository=mock(AvatarRepository::class.java)
                activity.setContent {MaterialTheme(colorScheme=appColors(theme)) {Surface {
                    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(Strings.text(R.string.app_name),style=MaterialTheme.typography.headlineMedium)
                        Text(Strings.text(R.string.checkin_inbox))
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            listOf(Icons.Default.Flag,Icons.Default.Check,Icons.Default.MoreVert).forEach {
                                FloatingActionButton(onClick={}) {Icon(it,null,tint=sharingActionColor())}
                            }
                        }
                        Button(onClick={},colors=ButtonDefaults.buttonColors(containerColor=sharingActionColor())) {Text(Strings.text(R.string.start))}
                        SyncFailureNotice(Snapshot(syncInProgress=true)) {}
                        for(choice in listOf(.75f,1f,1.25f,1.5f)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).padding(5.dp),contentAlignment=Alignment.Center) {
                                    Avatar("person","Anna",null,true,repository,mapAvatarDp(choice).dp)
                                }
                                Avatar("person","Anna",null,true,repository,mapAvatarDp(choice,true).dp)
                                Text("${mapAvatarDp(choice)} dp")
                            }
                        }
                    }
                }}}
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                val view=activity.window.decorView
                view.measure(View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY))
                view.layout(0,0,420,900)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                val bitmap=Bitmap.createBitmap(420,900,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file=File("build/reports/v047-ui/$theme.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                org.junit.Assert.assertTrue((0 until 900 step 5).flatMap {y -> (0 until 420 step 5).map {x -> bitmap.getPixel(x,y)}}.distinct().size>10)
                bitmap.recycle()
            }
        }
    }
}
