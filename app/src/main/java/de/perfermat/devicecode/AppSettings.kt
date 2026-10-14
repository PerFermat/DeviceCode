package de.perfermat.devicecode

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Die Einstellungen der App, gespeichert in den SharedPreferences. */
object AppSettings {
    private const val FILE = "settings"
    private const val PREFILL_CODE = "prefill_code"

    /** Code als `?user_code=` an die Google-Adresse hängen, damit das Feld schon ausgefüllt ist. */
    fun prefillCode(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(PREFILL_CODE, true)

    fun setPrefillCode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(PREFILL_CODE, enabled).apply()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prefillCode by remember { mutableStateOf(AppSettings.prefillCode(context)) }
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Code automatisch eintragen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gibt den Code in der Adresse an Google weiter, sodass das Eingabefeld schon " +
                        "ausgefüllt ist. Ausgeschaltet muss er aus der Zwischenablage eingefügt werden.",
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
