package com.example.roomdisplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Replace with your deployed Worker URL.
private const val STATUS_URL = "https://room-display-worker.YOUR-SUBDOMAIN.workers.dev/api/status"
private const val POLL_INTERVAL_MS = 30_000L

data class RoomStatus(
    val isOccupied: Boolean,
    val currentTitle: String? = null,
    val currentEndTime: String? = null,
    val nextTitle: String? = null,
    val nextStartTime: String? = null,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()

        setContent {
            var status by remember { mutableStateOf<RoomStatus?>(null) }

            LaunchedEffect(Unit) {
                while (true) {
                    status = fetchStatus()
                    delay(POLL_INTERVAL_MS)
                }
            }

            RoomScreen(status)
        }
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private suspend fun fetchStatus(): RoomStatus? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url(STATUS_URL).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val occupied = json.getBoolean("isOccupied")

            val current = json.optJSONObject("currentMeeting")
            val next = json.optJSONObject("nextMeeting")

            RoomStatus(
                isOccupied = occupied,
                currentTitle = current?.optString("title"),
                currentEndTime = current?.optString("endTime"),
                nextTitle = next?.optString("title"),
                nextStartTime = next?.optString("startTime"),
            )
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun RoomScreen(status: RoomStatus?) {
    val backgroundColor = when {
        status == null -> Color.DarkGray
        status.isOccupied -> Color(0xFFD32F2F) // red
        else -> Color(0xFF388E3C) // green
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                status == null -> Text("...", color = Color.White, fontSize = 48.sp)
                status.isOccupied -> {
                    Text("BUSY", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Text(status.currentTitle ?: "", color = Color.White, fontSize = 32.sp)
                    Text("until ${status.currentEndTime}", color = Color.White, fontSize = 24.sp)
                }
                else -> {
                    Text("FREE", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    if (status.nextTitle != null) {
                        Text("Next: ${status.nextTitle}", color = Color.White, fontSize = 28.sp)
                        Text("at ${status.nextStartTime}", color = Color.White, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
