package de.perfermat.devicecode

import android.Manifest
import androidx.camera.core.ZoomState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.Observer
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
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
            detectedUrl != null -> open(code, DeviceCodeParser.snapToKnown(detectedUrl, urls + GOOGLE_DEVICE_URL))
            urls.size == 1 -> open(code, urls.single())
            else -> {
                lastUrl = null
                choices = urls
                copyToClipboard(context, code)
                if (openTabs > 0) returnToCamera(context)
            }
        }
    }

    fun accept(code: String, url: String?) {
        recentCodes = (recentCodes - code + code).takeLast(REMEMBERED_CODES)
        lastCode = code
        vibrate(context)
        openOrChoose(code, url)
    }

    val scanControl = remember { ScanControl() }
    // Das angetippte, eingefrorene Bild, in dem der Nutzer den Code selbst markiert.
    var frozen by remember { mutableStateOf<FrozenFrame?>(null) }

    fun unfreeze() {
        frozen = null
        scanControl.paused = false
    }

    Box(Modifier.fillMaxSize()) {
        // Die Kamera läuft auch hinter dem Halbfenster weiter und erkennt so schon den nächsten Code.
        CameraPreview(
            control = scanControl,
            modifier = Modifier.fillMaxSize(),
            onDetection = { (code, url) -> if (code !in recentCodes) accept(code, url) },
            onCapture = { frame ->
                scanControl.paused = true
                frozen = frame
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
            // Antippen friert das Bild ein, damit der Nutzer einen Code in anderem Format markieren kann;
            // zwei Finger zoomen. Die Vorschau selbst bekommt keine Berührungen mehr ab.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { scanControl.captureNext.set(true) } }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ -> scanControl.zoomTo(scanControl.zoomRatio * zoom) }
                    }
            )
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Einstellungen") }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (openTabs == 0 && scanControl.maxZoom > 1f) ZoomBar(scanControl)
            Card(Modifier.fillMaxWidth()) {
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
                        Text(
                            "Wird er nicht erkannt: aufs Bild tippen und den Code selbst markieren.",
                            style = MaterialTheme.typography.bodyMedium,
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

        frozen?.let { frame ->
            FrozenFrameScreen(
                frame = frame,
                onUse = { code ->
                    unfreeze()
                    accept(code, frame.url)
                },
                onCancel = ::unfreeze,
            )
        }
    }
}

/** Steuert Kamera und Analyse von außen: Bild festhalten, Erkennung anhalten, zoomen. */
private class ScanControl {
    val captureNext = AtomicBoolean(false)

    @Volatile
    var paused = false

    var camera: CameraController? = null
    var zoomRatio by mutableFloatStateOf(1f)
    var maxZoom by mutableFloatStateOf(1f)

    // Der gewünschte Zoom überdauert das Schließen der Kamera, wenn der Custom Tab geöffnet wird.
    var wantedZoom = 1f
        private set

    fun zoomTo(ratio: Float) {
        wantedZoom = ratio.coerceIn(1f, maxZoom.coerceAtLeast(1f))
        camera?.setZoomRatio(wantedZoom)
    }

    fun onZoomState(state: ZoomState) {
        maxZoom = state.maxZoomRatio
        zoomRatio = state.zoomRatio
        // Nach dem Zurückkommen startet die Kamera wieder ohne Zoom – den alten wiederherstellen.
        if (state.zoomRatio == 1f && wantedZoom > 1f) camera?.setZoomRatio(wantedZoom)
    }
}

@Composable
private fun ZoomBar(control: ScanControl) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Zoom", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = control.zoomRatio.coerceIn(1f, control.maxZoom),
                onValueChange = control::zoomTo,
                valueRange = 1f..control.maxZoom,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%.1f×".format(control.zoomRatio),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** Ein erkanntes Wort mit seiner Position im (aufrecht gedrehten) Bild. */
private class Word(val text: String, val box: Rect)

private class FrozenFrame(val bitmap: Bitmap, val words: List<Word>, val url: String?)

@Composable
private fun FrozenFrameScreen(frame: FrozenFrame, onUse: (String) -> Unit, onCancel: () -> Unit) {
    val image = remember(frame) { frame.bitmap.asImageBitmap() }
    // Vorschlag: das längste Wort, das wie ein Code aussieht.
    val suggestion = remember(frame) {
        frame.words.indices.filter { DeviceCodeParser.isCodeLike(frame.words[it].text) }
            .maxByOrNull { frame.words[it].text.length }
    }
    var selected by remember(frame) { mutableStateOf(setOfNotNull(suggestion)) }
    var text by remember(frame) { mutableStateOf(suggestion?.let { frame.words[it].text }.orEmpty()) }
    BackHandler(onBack = onCancel)

    val selectedColor = MaterialTheme.colorScheme.primary
    val suggestedColor = MaterialTheme.colorScheme.tertiary
    val otherColor = Color.White.copy(alpha = 0.5f)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        Text(
            "Tippe auf den Code – mehrere Teile nacheinander antippen.",
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(frame) {
                    detectTapGestures { tap ->
                        val fit = ImageFit(size.width.toFloat(), size.height.toFloat(), image)
                        val x = (tap.x - fit.dx) / fit.scale
                        val y = (tap.y - fit.dy) / fit.scale
                        // Etwas Spielraum, die Rahmen sind auf dem Handy klein.
                        val slop = 16 / fit.scale
                        val hit = frame.words.indices.firstOrNull { i ->
                            val box = frame.words[i].box
                            x >= box.left - slop && x <= box.right + slop && y >= box.top - slop && y <= box.bottom + slop
                        } ?: return@detectTapGestures
                        selected = if (hit in selected) selected - hit else selected + hit
                        // Wörter in Lesereihenfolge zusammensetzen, wie ML Kit sie geliefert hat.
                        text = selected.sorted().joinToString(" ") { frame.words[it].text }
                    }
                }
        ) {
            val fit = ImageFit(size.width, size.height, image)
            drawImage(
                image,
                dstOffset = IntOffset(fit.dx.toInt(), fit.dy.toInt()),
                dstSize = IntSize((image.width * fit.scale).toInt(), (image.height * fit.scale).toInt()),
            )
            frame.words.forEachIndexed { i, word ->
                val color = when {
                    i in selected -> selectedColor
                    DeviceCodeParser.isCodeLike(word.text) -> suggestedColor
                    else -> otherColor
                }
                drawRect(
                    color,
                    topLeft = Offset(fit.dx + word.box.left * fit.scale, fit.dy + word.box.top * fit.scale),
                    size = Size(word.box.width() * fit.scale, word.box.height() * fit.scale),
                    style = Stroke(width = if (i in selected) 6f else 3f),
                )
            }
        }
        Card(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Abbrechen") }
                    val code = text.filterNot { it.isWhitespace() }
                    Button(onClick = { onUse(code) }, enabled = code.isNotEmpty()) { Text("Übernehmen") }
                }
            }
        }
    }
}

/** Skalierung und Versatz, mit denen ein Bild eingepasst (ContentScale.Fit) und zentriert wird. */
private class ImageFit(width: Float, height: Float, image: ImageBitmap) {
    val scale = min(width / image.width, height / image.height)
    val dx = (width - image.width * scale) / 2
    val dy = (height - image.height * scale) / 2
}

@Composable
private fun CameraPreview(
    control: ScanControl,
    onDetection: (Detection) -> Unit,
    onCapture: (FrozenFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDetection by rememberUpdatedState(onDetection)
    val currentOnCapture by rememberUpdatedState(onCapture)

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
                        android.util.Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                CodeAnalyzer(
                    recognizer,
                    control,
                    onDetection = { currentOnDetection(it) },
                    onCapture = { currentOnCapture(it) },
                ),
            )
        }
    }

    // An den Lifecycle gebunden: Beim Wechsel in den Custom Tab stoppt die Kamera,
    // beim Zurückkommen läuft sie automatisch wieder.
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        control.camera = controller
        val zoomObserver = Observer<ZoomState>(control::onZoomState)
        controller.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            controller.zoomState.removeObserver(zoomObserver)
            control.camera = null
            controller.unbind()
            recognizer.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // Zoomen übernimmt die Compose-Ebene darüber, die auch das Antippen auswertet.
                controller.isPinchToZoomEnabled = false
                this.controller = controller
            }
        },
    )
}

private class CodeAnalyzer(
    private val recognizer: TextRecognizer,
    private val control: ScanControl,
    private val onDetection: (Detection) -> Unit,
    private val onCapture: (FrozenFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = StableCodeDetector(requiredFrames = 3)

    override fun analyze(image: ImageProxy) {
        if (control.paused) return image.close()
        val mediaImage = image.image ?: return image.close()
        val rotation = image.imageInfo.rotationDegrees
        // Das Bild muss vor close() kopiert werden; ML Kit liefert die Rahmen fürs aufrechte Bild.
        val snapshot = if (control.captureNext.getAndSet(false)) image.toBitmap().rotated(rotation) else null
        recognizer.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { result ->
                if (snapshot != null) {
                    val words = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }
                        .mapNotNull { element -> element.boundingBox?.let { Word(element.text, it) } }
                    onCapture(FrozenFrame(snapshot, words, DeviceCodeParser.findUrl(result.text)))
                } else {
                    detector.onFrame(DeviceCodeParser.find(result.text), DeviceCodeParser.findUrl(result.text))
                        ?.let(onDetection)
                }
            }
            .addOnFailureListener {
                if (snapshot != null) control.captureNext.set(true) // Mit dem nächsten Bild noch einmal.
                detector.onFrame(null)
            }
            // Erst danach kommt das nächste Frame – so läuft nie mehr als eine Erkennung gleichzeitig.
            .addOnCompleteListener { image.close() }
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap =
    if (degrees == 0) this
    else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

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
