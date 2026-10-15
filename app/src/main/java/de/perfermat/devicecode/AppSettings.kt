package de.perfermat.devicecode

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

const val GOOGLE_DEVICE_URL = "https://www.google.com/device"

/** Die Einstellungen der App, gespeichert in den SharedPreferences. */
object AppSettings {
    private const val FILE = "settings"
    private const val PREFILL_CODE = "prefill_code"
    private const val URLS = "urls"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Code als `?user_code=` an google.com/device hängen, damit das Feld schon ausgefüllt ist. */
    fun prefillCode(context: Context): Boolean = prefs(context).getBoolean(PREFILL_CODE, true)

    fun setPrefillCode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREFILL_CODE, enabled).apply()
    }

    /** Adressen, die geöffnet werden, wenn im Bild keine zu lesen ist. Nie leer. */
    fun urls(context: Context): List<String> =
        prefs(context).getString(URLS, null)?.lines()?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOf(GOOGLE_DEVICE_URL)

    fun setUrls(context: Context, urls: List<String>) {
        prefs(context).edit().putString(URLS, urls.joinToString("\n")).apply()
    }

    /** Macht aus einer Eingabe wie "netflix.com/tv8" eine vollständige Adresse, oder null wenn ungültig. */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val url = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = url.substringAfter("://").substringBefore('/')
        return url.takeIf { host.contains('.') && !host.endsWith('.') }
    }
}

/** Zeigt eine Adresse ohne "https://" an. */
fun displayUrl(url: String) = url.substringAfter("://")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prefillCode by remember { mutableStateOf(AppSettings.prefillCode(context)) }
    var urls by remember { mutableStateOf(AppSettings.urls(context)) }
    var newUrl by remember { mutableStateOf("") }
    val normalizedNewUrl = AppSettings.normalizeUrl(newUrl)
    BackHandler(onBack = onBack)

    fun updateUrls(changed: List<String>) {
        urls = changed
        AppSettings.setUrls(context, changed)
    }

    fun addUrl() {
        val url = normalizedNewUrl ?: return
        if (url !in urls) updateUrls(urls + url)
        newUrl = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adressen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Steht im Bild eine Adresse, wird sie geöffnet. Sonst öffnet die App die Adresse von " +
                    "hier – bei mehreren fragt sie nach.",
                style = MaterialTheme.typography.bodyMedium,
            )
            urls.forEach { url ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(displayUrl(url), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    // Die letzte Adresse bleibt, sonst wüsste die App nicht, wohin.
                    TextButton(onClick = { updateUrls(urls - url) }, enabled = urls.size > 1) {
                        Text("Entfernen")
                    }
                }
                HorizontalDivider()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    label = { Text("Neue Adresse") },
                    placeholder = { Text("z. B. netflix.com/tv8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addUrl() }),
                )
                Button(onClick = ::addUrl, enabled = normalizedNewUrl != null) { Text("Hinzufügen") }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Code automatisch eintragen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gibt bei google.com/device den Code in der Adresse mit, sodass das Eingabefeld " +
                        "schon ausgefüllt ist. Sonst muss er aus der Zwischenablage eingefügt werden.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = prefillCode,
                onCheckedChange = {
                    prefillCode = it
                    AppSettings.setPrefillCode(context, it)
                },
            )
        }

        OutlinedButton(onClick = onBack) { Text("Zurück zur Kamera") }
    }
}
