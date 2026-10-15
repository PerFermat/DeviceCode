package de.perfermat.devicecode

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

private const val CHROME = "com.android.chrome"
private const val REMEMBERED_CODES = 10
private const val TAB_HEIGHT_FRACTION = 0.7

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    DeviceCodeApp()
                }
            }
        }
    }
}

@Composable
private fun DeviceCodeApp() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    // Falls die Berechtigung in den Systemeinstellungen erteilt wurde.
    LifecycleResumeEffect(Unit) {
        hasPermission = hasCameraPermission(context)
        onPauseOrDispose { }
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Hält den Zustand der Kameraseite (letzte Codes) fest, solange die Einstellungen offen sind.
    val stateHolder = rememberSaveableStateHolder()

    if (hasPermission) {
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else {
            stateHolder.SaveableStateProvider("scanner") {
                ScannerScreen(onOpenSettings = { showSettings = true })
            }
        }
    } else {
        PermissionScreen(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
    }
}

@Composable
private fun ScannerScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var lastCode by rememberSaveable { mutableStateOf<String?>(null) }
    // Die zuletzt geöffnete Adresse; null, solange der Nutzer noch keine ausgewählt hat.
    var lastUrl by rememberSaveable { mutableStateOf<String?>(null) }
    // Die Adressen aus den Einstellungen, zwischen denen der Nutzer gerade wählen soll.
    var choices by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Die zuletzt verarbeiteten Codes – der Fernseher zeigt einen Code oft noch an, wenn man zurückkommt.
    var recentCodes by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Ein Zähler statt eines Flags: Wird ein Tab durch einen neuen ersetzt, kommt das Ergebnis
    // des alten erst an, wenn der neue schon offen ist.
    var openTabs by rememberSaveable { mutableIntStateOf(0) }
    val tabLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { openTabs = (openTabs - 1).coerceAtLeast(0) }

    fun open(code: String, url: String) {
        lastUrl = url
        choices = emptyList()
        if (openTabs > 0) returnToCamera(context)
        copyAndOpen(context, code, url) { intent ->
            tabLauncher.launch(intent)
            openTabs++
        }
    }

    // Adresse aus dem Bild, sonst die einzige aus den Einstellungen, sonst nachfragen.
    fun openOrChoose(code: String, detectedUrl: String?) {
        val urls = AppSettings.urls(context)
        when {
            detectedUrl != null -> open(code, detectedUrl)
            urls.size == 1 -> open(code, urls.single())
            else -> {
                lastUrl = null
                choices = urls
                copyToClipboard(context, code)
                if (openTabs > 0) returnToCamera(context)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Die Kamera läuft auch hinter dem Halbfenster weiter und erkennt so schon den nächsten Code.
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onDetection = { (code, url) ->
                if (code !in recentCodes) {
                    recentCodes = (recentCodes + code).takeLast(REMEMBERED_CODES)
                    lastCode = code
                    vibrate(context)
                    openOrChoose(code, url)
                }
            },
        )

        if (openTabs > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { returnToCamera(context) }
            ) {
                Surface(
                    Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Hier tippen – zurück zur Kamera",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Einstellungen") }
        }

        Card(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val code = lastCode
                val url = lastUrl
                if (code == null) {
                    Text(
                        "Kamera auf den Code am Fernseher richten",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text("Zuletzt erkannt", style = MaterialTheme.typography.labelLarge)
                    Text(
                        code,
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (choices.isNotEmpty()) {
                        Text(
                            "Im Bild war keine Adresse zu lesen. Welche Seite soll geöffnet werden?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        choices.forEach { choice ->
                            OutlinedButton(onClick = { open(code, choice) }, Modifier.fillMaxWidth()) {
                                Text(displayUrl(choice))
                            }
                        }
                        TextButton(onClick = { choices = emptyList() }) { Text("Abbrechen") }
                    } else {
                        Text(
                            if (url != null) "Kopiert – geöffnet: ${displayUrl(url)}" else "In die Zwischenablage kopiert",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { if (url != null) open(code, url) else openOrChoose(code, null) }) {
                            Text("Erneut öffnen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onDetection: (Detection) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDetection by rememberUpdatedState(onDetection)

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Etwas mehr Auflösung als der Standard, damit auch kleiner Text auf dem Fernseher lesbar ist.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                CodeAnalyzer(recognizer) { currentOnDetection(it) },
            )
        }
    }

    // An den Lifecycle gebunden: Beim Wechsel in den Custom Tab stoppt die Kamera,
    // beim Zurückkommen läuft sie automatisch wieder.
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            recognizer.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
    )
}

private class CodeAnalyzer(
    private val recognizer: TextRecognizer,
    private val onDetection: (Detection) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = StableCodeDetector(requiredFrames = 3)

    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image ?: return image.close()
        recognizer.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { result ->
                detector.onFrame(DeviceCodeParser.find(result.text), DeviceCodeParser.findUrl(result.text))
                    ?.let(onDetection)
            }
            .addOnFailureListener { detector.onFrame(null) }
            // Erst danach kommt das nächste Frame – so läuft nie mehr als eine Erkennung gleichzeitig.
            .addOnCompleteListener { image.close() }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            "DeviceCode braucht die Kamera, um den Code auf dem Fernseher zu lesen. " +
                "Die Bilder werden nur auf dem Gerät ausgewertet.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Kamera erlauben") }
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) { Text("App-Einstellungen öffnen") }
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
}

private fun copyToClipboard(context: Context, code: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Gerätecode", code))
}

private fun copyAndOpen(context: Context, code: String, url: String, launch: (Intent) -> Unit) {
    copyToClipboard(context, code)

    // Als Halbfenster (Partial Custom Tab): Oben bleibt die Kamera sichtbar und nimmt Tipps an.
    // Browser ohne Unterstützung dafür zeigen die Seite einfach im Vollbild.
    val customTab = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setInitialActivityHeightPx(
            (context.resources.displayMetrics.heightPixels * TAB_HEIGHT_FRACTION).toInt(),
            CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE,
        )
        .setToolbarCornerRadiusDp(16)
        .setBackgroundInteractionEnabled(true)
        .build()
    // Chrome bevorzugen, damit die dort bestehende Google-Anmeldung greift; sonst Standardbrowser.
    CustomTabsClient.getPackageName(context, listOf(CHROME), true)
        ?.let { customTab.intent.setPackage(it) }
    customTab.intent.data = Uri.parse(withPrefilledCode(context, url, code))
    try {
        // Chrome zeigt das Halbfenster nur, wenn der Tab per startActivityForResult kommt.
        launch(customTab.intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Kein Browser gefunden", Toast.LENGTH_LONG).show()
    }
}

/** Hängt bei google.com/device den Code an, damit das Eingabefeld schon ausgefüllt ist. */
private fun withPrefilledCode(context: Context, url: String, code: String): String {
    val uri = Uri.parse(url)
    val isGoogleDevicePage = uri.host?.removePrefix("www.") == "google.com" && uri.path?.trimEnd('/') == "/device"
    return if (isGoogleDevicePage && uri.query == null && AppSettings.prefillCode(context)) {
        "$url?user_code=${Uri.encode(code)}"
    } else {
        url
    }
}

/** Schließt einen offenen Custom Tab: Er liegt im selben Task über MainActivity. */
private fun returnToCamera(context: Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
}
