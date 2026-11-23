package dev.spikeai.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.spikeai.photos.ui.LabScreen
import dev.spikeai.photos.ui.LabelsScreen
import dev.spikeai.photos.ui.LoadingScreen
import dev.spikeai.photos.ui.PermissionScreen
import dev.spikeai.photos.ui.SearchScreen
import dev.spikeai.photos.ui.SetupScreen

class MainActivity : ComponentActivity() {
    private val state by lazy { AppState(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Root(state)
            }
        }
    }
}

private val photoPermissions =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
private fun Root(state: AppState) {
    val context = LocalContext.current
    fun hasPhotos() = photoPermissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(hasPhotos()) }
    var modelsReady by remember { mutableStateOf(state.models.missing().isEmpty()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasPhotos() }

    when {
        !modelsReady -> SetupScreen(state.models) { modelsReady = true }
        !granted -> PermissionScreen { launcher.launch(photoPermissions) }
        else -> {
            LaunchedEffect(Unit) { state.load() }
            val ready = state.ready
            if (ready == null) LoadingScreen(state.loadError) else {
                LaunchedEffect(ready) { ready.indexer.run() }
                Tabs(state, ready)
            }
        }
    }
}

@Composable
private fun Tabs(state: AppState, ready: AppState.Ready) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("Search" to "⌕", "Labels" to "◎", "Lab" to "⚗")
    Scaffold(bottomBar = {
        NavigationBar {
            names.forEachIndexed { i, (label, glyph) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(glyph, style = MaterialTheme.typography.titleLarge) }, label = { Text(label) })
            }
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> SearchScreen(state, ready)
                1 -> LabelsScreen(state, ready)
                else -> LabScreen(state, ready)
            }
        }
    }
}
