package dev.spikeai.spike.appfunctions

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TAG = "SpikeB"

class MainActivity : ComponentActivity() {

    private var report by mutableStateOf<AccessReport?>(null)
    private var lastRoleRequest by mutableStateOf("not tried")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val roleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                lastRoleRequest = if (result.resultCode == RESULT_OK) "granted" else "refused/cancelled (code ${result.resultCode})"
                refresh()
            }
            Screen(
                report = report,
                lastRoleRequest = lastRoleRequest,
                onRefresh = ::refresh,
                onRequestRole = {
                    val rm = getSystemService(RoleManager::class.java)
                    try {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                    } catch (e: Exception) {
                        lastRoleRequest = "request intent failed: ${e.javaClass.simpleName}"
                    }
                },
                onOpenDefaultApps = {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh() // picks up changes after returning from Settings
    }

    private fun refresh() {
        val r = buildAccessReport(this)
        report = r
        Log.i(TAG, r.toJson() + "\nrole_request=$lastRoleRequest")
    }
}

@Composable
private fun Screen(
    report: AccessReport?,
    lastRoleRequest: String,
    onRefresh: () -> Unit,
    onRequestRole: () -> Unit,
    onOpenDefaultApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Spike B — AppFunctions caller access", style = TextStyle(fontSize = 20.sp))
        BasicText(
            "1) Tap 'Request Assistant role'. 2) If refused, open Default apps → Digital assistant app → pick this app. " +
                "3) Come back and check permission_granted.",
            style = TextStyle(fontSize = 14.sp, color = Color.DarkGray),
        )
        ActionButton("Request Assistant role", onRequestRole)
        ActionButton("Open Default apps settings", onOpenDefaultApps)
        ActionButton("Refresh", onRefresh)
        BasicText("Last role request: $lastRoleRequest", style = TextStyle(fontSize = 14.sp))
        SelectionContainer {
            BasicText(
                report?.toJson() ?: "…",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
        }
        BasicText(
            "Also logged: adb logcat -s SpikeB",
            style = TextStyle(fontSize = 12.sp, color = Color.Gray),
        )
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = TextStyle(fontSize = 16.sp, color = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1F5FBF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    )
}
