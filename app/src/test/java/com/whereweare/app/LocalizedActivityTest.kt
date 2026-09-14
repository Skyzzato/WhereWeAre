package com.whereweare.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.whereweare.app.ui.localizedActivityContext
import com.whereweare.app.ui.localizedContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LocalizedActivityTest {
    @Test fun originalApplicationContextReproducesHiltStartupCrash() {
        val broken=localizedContext(RuntimeEnvironment.getApplication(),"it")
        val error=assertThrows(IllegalStateException::class.java) {
            HiltViewModelFactory(broken,ViewModelProvider.NewInstanceFactory())
        }
        assertTrue(error.message.orEmpty().contains("activity",ignoreCase=true))
    }

    @Test fun bothLanguagesPreserveActivityAndLocalizedResources() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            val activity=controller.get()
            for(language in listOf("it","en")) {
                val context=localizedActivityContext(activity,language)
                var base: Context=context
                while(base is ContextWrapper && base !is ComponentActivity) base=base.baseContext
                assertSame(activity,base)
                assertEquals(language,context.resources.configuration.locales[0].language)
                assertEquals(if(language=="it") "Gruppo non trovato" else "Group not found",context.getString(R.string.group_not_found))
                assertSame(activity.applicationContext,context.applicationContext)
            }
        }
    }
}
