package com.whereweare.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.whereweare.app.R

@Composable fun OnboardingScreen(operation: OperationState,finish: ()->Unit) {
    var page by rememberSaveable {mutableIntStateOf(0)}
    BackHandler(page>0) {page--}
    val titles=listOf(R.string.onboarding_title_1,R.string.onboarding_title_2,R.string.onboarding_title_3,R.string.onboarding_title_4)
    val bodies=listOf(R.string.onboarding_body_1,R.string.onboarding_body_2,R.string.onboarding_body_3,R.string.onboarding_body_4)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("WhereWeAre - Troviamoci",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            TextButton(enabled=!operation.busy,onClick=finish) {Text(Strings.text(R.string.onboarding_skip))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            OnboardingIllustration(page)
            Spacer(Modifier.height(24.dp))
            Text(Strings.text(titles[page]),style=MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(Strings.text(bodies[page]),style=MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(20.dp)) {
            repeat(4) {index -> Text(if(page==index) "●" else "○",color=MaterialTheme.colorScheme.primary)}
        }
        Busy(operation,inline=true)
        Button(enabled=!operation.busy,onClick={if(page==3) finish() else page++},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Text(Strings.text(if(page==3) R.string.onboarding_start else R.string.onboarding_next))
        }
    }
}

@Composable private fun OnboardingIllustration(page: Int) {
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.tertiary
    Card(Modifier.fillMaxWidth().height(240.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(4) {i ->
                    drawLine(primary.copy(alpha=.09f),Offset(0f,size.height*i/4),Offset(size.width,size.height*(i+1)/4),12.dp.toPx())
                    drawLine(primary.copy(alpha=.09f),Offset(size.width*i/4,0f),Offset(size.width*(i+1)/4,size.height),8.dp.toPx())
                }
                if(page==2) listOf(.2f,.8f).forEach {x -> drawLine(secondary,Offset(size.width*x,size.height*.75f),Offset(size.width*.5f,size.height*.3f),3.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),6.dp.toPx())))}
            }
            Icon(when(page) {1 -> Icons.Default.Groups;2 -> Icons.Default.Flag;3 -> Icons.Default.VerifiedUser;else -> Icons.Default.LocationOn},null,
                Modifier.align(Alignment.Center).size(84.dp),tint=primary)
            Surface(Modifier.align(Alignment.BottomStart).padding(20.dp),shape=MaterialTheme.shapes.extraLarge,color=secondary.copy(alpha=.18f)) {
                Icon(Icons.Default.Person,null,Modifier.padding(10.dp).size(34.dp),tint=secondary)
            }
            Surface(Modifier.align(Alignment.TopEnd).padding(20.dp),shape=MaterialTheme.shapes.extraLarge,color=primary.copy(alpha=.18f)) {
                Icon(if(page==3) Icons.Default.Lock else Icons.Default.Person,null,Modifier.padding(10.dp).size(34.dp),tint=primary)
            }
        }
    }
}
