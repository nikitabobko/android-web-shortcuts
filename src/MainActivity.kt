package bobko.webshortcuts

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val SHORTCUT_ID_PREFIX = "bobko-web-shortcut-"

private data class WebShortcut(val id: String, val name: String, val url: String)

private data class EditorState(val id: String?, val name: String, val url: String)

class MainActivity : ComponentActivity() {
    private val refreshKey = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShortcutsScreen(refreshKey = refreshKey.intValue)
                }
            }
        }
    }

    // Pinning is confirmed by the user in a system dialog, so a freshly created shortcut only
    // shows up in pinnedShortcuts once we come back to the foreground. Reload the list then.
    override fun onResume() {
        super.onResume()
        refreshKey.intValue++
    }
}

@Composable private fun ShortcutsScreen(refreshKey: Int) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(emptyList<WebShortcut>()) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        shortcuts = loadShortcuts(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Web Shortcuts", style = MaterialTheme.typography.headlineSmall)

        Button(
            onClick = { editor = EditorState(id = null, name = "", url = "") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("New shortcut")
        }

        if (shortcuts.isEmpty()) {
            Text(
                "No shortcuts yet. Tap \"New shortcut\" to create one.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shortcuts, key = { it.id }) { shortcut ->
                    Card(
                        onClick = { editor = EditorState(shortcut.id, shortcut.name, shortcut.url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(shortcut.name, style = MaterialTheme.typography.titleMedium)
                            Text(shortcut.url, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    editor?.let { state ->
        ShortcutEditorDialog(
            state = state,
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead") // false positive
                editor = null
            },
            onSave = { name, url ->
                scope.launch {
                    if (upsertShortcut(context, state.id, name.trim(), url.trim())) {
                        @Suppress("AssignedValueIsNeverRead") // false positive
                        editor = null
                        shortcuts = loadShortcuts(context)
                    }
                }
            },
        )
    }
}

@Composable private fun ShortcutEditorDialog(
    state: EditorState,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit,
) {
    var name by remember(state) { mutableStateOf(state.name) }
    var url by remember(state) { mutableStateOf(state.url) }
    val creating = state.id == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New shortcut" else "Edit shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url) }) {
                Text(if (creating) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private suspend fun loadShortcuts(context: Context): List<WebShortcut> {
    val shortcutManager = context.getSystemService<ShortcutManager>() ?: return emptyList()
    return withContext(Dispatchers.IO) {
        // 'shortcutManager.pinnedShortcuts' may take several seconds to complete,
        // so it should only be called from a worker thread.
        shortcutManager.pinnedShortcuts
            .filter { it.id.startsWith(SHORTCUT_ID_PREFIX) }
            .map { info ->
                WebShortcut(
                    id = info.id,
                    name = info.shortLabel?.toString().orEmpty(),
                    // The publisher app keeps access to the intent, so we can recover the URL.
                    url = info.intent?.data?.toString().orEmpty(),
                )
            }
    }
}

private fun upsertShortcut(
    context: Context,
    id: String?,
    name: String,
    rawUrl: String,
): Boolean {
    if (name.isBlank() || rawUrl.isBlank()) {
        Toast.makeText(context, "Please fill in both fields", Toast.LENGTH_SHORT).show()
        return false
    }

    // Ensure the URL has a scheme so the browser can handle it.
    val url = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"

    val shortcutManager = context.getSystemService<ShortcutManager>()
    if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported) {
        Toast.makeText(context, "Your launcher does not support pinning shortcuts", Toast.LENGTH_LONG).show()
        return false
    }

    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val shortcut = ShortcutInfo.Builder(context, id ?: "$SHORTCUT_ID_PREFIX${UUID.randomUUID()}")
        .setShortLabel(name)
        .setLongLabel(name)
        .setIcon(android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()

    if (id == null) {
        shortcutManager.requestPinShortcut(shortcut, null)
    } else {
        val updated = shortcutManager.updateShortcuts(listOf(shortcut))
        if (!updated) {
            Toast.makeText(context, "Could not update the shortcut", Toast.LENGTH_LONG).show()
            return false
        }
    }
    return true
}
