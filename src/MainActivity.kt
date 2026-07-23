package bobko.webshortcuts

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CreateShortcutScreen()
                }
            }
        }
    }
}

@Composable fun CreateShortcutScreen() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Shortcut name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Shortcut URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { createShortcut(context, name.trim(), url.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create")
        }
    }
}

private fun createShortcut(
    context: android.content.Context,
    name: String,
    rawUrl: String,
) {
    if (name.isBlank() || rawUrl.isBlank()) {
        Toast.makeText(context, "Please fill in both fields", Toast.LENGTH_SHORT).show()
        return
    }

    // Ensure the URL has a scheme so the browser can handle it.
    val url = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"

    val shortcutManager = context.getSystemService<ShortcutManager>()
    if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported) {
        Toast.makeText(context, "Your launcher does not support pinning shortcuts", Toast.LENGTH_LONG).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val shortcut = ShortcutInfo.Builder(context, "web-shortcut-${url.hashCode()}")
        .setShortLabel(name)
        .setLongLabel(name)
        .setIcon(android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()

    shortcutManager.requestPinShortcut(shortcut, null)
}
