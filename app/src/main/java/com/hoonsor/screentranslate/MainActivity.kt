package com.hoonsor.screentranslate

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// HSL 調校過的暗色色票
private val Bg = Color(0xFF101318)
private val Surface = Color(0xFF181D25)
private val SurfaceHi = Color(0xFF212835)
private val Teal = Color(0xFF4FC3B4)
private val Indigo = Color(0xFF3A6EC4)
private val Amber = Color(0xFFF2B24C)
private val Rose = Color(0xFFE07A7A)
private val TextHi = Color(0xFFE8EDF5)
private val TextLo = Color(0xFF94A0B4)

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Teal,
                    onPrimary = Color(0xFF06201D),
                    secondary = Indigo,
                    background = Bg,
                    surface = Surface,
                    surfaceVariant = SurfaceHi,
                    onSurface = TextHi,
                    onSurfaceVariant = TextLo,
                    error = Rose,
                ),
            ) {
                SettingsScreen(prefs, resumeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    // ── 系統設定捷徑 ──────────────────────────────────

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestAddTile(onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult("請下拉快速設定面板 → 編輯（鉛筆圖示）→ 把「螢幕翻譯」拖進面板")
            return
        }
        val sbm = getSystemService(StatusBarManager::class.java)
        sbm.requestAddTileService(
            ComponentName(this, TranslateTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { code ->
            onResult(
                when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已加入快速設定面板"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "快速設定面板裡已經有了"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "已取消加入"
                    else -> "無法自動加入（代碼 $code），請手動編輯快速設定面板"
                }
            )
        }
    }
}

@Composable
private fun SettingsScreen(prefs: Prefs, tick: Int) {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val serviceOn = remember(tick) { TranslatorAccessibilityService.instance != null }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14202A), Bg, Bg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header()
            SetupCard(activity, serviceOn)
            BehaviorCard(prefs, serviceOn)
            EngineCard(prefs)
            ColorOsCard(activity)
            Text(
                "v${BuildConfig.VERSION_NAME}・API key 只存在本機 App 私有儲存區，不會備份或上傳",
                color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text("螢幕翻譯", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("點一下浮動小圓點，把畫面上的外文翻成中文", color = TextLo, fontSize = 14.sp)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.92f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (ok) Teal else Amber,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextHi, fontSize = 15.sp)
    }
}

// ── 1. 啟用步驟 ─────────────────────────────────────

@Composable
private fun SetupCard(activity: MainActivity, serviceOn: Boolean) {
    var tileMsg by remember { mutableStateOf<String?>(null) }
    SectionCard("啟用") {
        StatusRow(serviceOn, if (serviceOn) "無障礙服務已啟用" else "無障礙服務尚未啟用")
        if (!serviceOn) {
            Text(
                "第一次安裝時，Android 會把自行安裝的 App 的無障礙權限標成「受限設定」。請依序：\n" +
                    "① 點「應用程式資訊」→ 右上角 ⋮ →「允許受限設定」（若沒有這個選項可略過）\n" +
                    "② 點「開啟無障礙設定」→ 已下載的應用程式 →「螢幕翻譯」→ 開啟",
                color = TextLo, fontSize = 13.sp, lineHeight = 19.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { activity.openAppDetails() }) { Text("應用程式資訊") }
                Button(onClick = { activity.openAccessibilitySettings() }) { Text("開啟無障礙設定") }
            }
        }
        HorizontalDivider(color = SurfaceHi)
        Text("快速設定面板開關", color = TextHi, fontSize = 15.sp)
        Text("加入後，下拉通知欄就能一鍵顯示／隱藏浮動小圓點。", color = TextLo, fontSize = 13.sp)
        Button(onClick = { activity.requestAddTile { tileMsg = it } }) { Text("加入快速設定面板") }
        tileMsg?.let { Text(it, color = Teal, fontSize = 13.sp) }
    }
}

// ── 2. 浮動按鈕與翻譯行為 ───────────────────────────────

@Composable
private fun BehaviorCard(prefs: Prefs, serviceOn: Boolean) {
    var bubbleOn by remember { mutableStateOf(prefs.bubbleEnabled) }
    var mode by remember { mutableStateOf(prefs.mode) }
    var script by remember { mutableStateOf(prefs.ocrScript) }
    var size by remember { mutableStateOf(prefs.bubbleSizeDp.toFloat()) }
    var alpha by remember { mutableStateOf(prefs.bubbleAlpha) }
    var maxSp by remember { mutableStateOf(prefs.maxTextSp.toFloat()) }
    var autoSwipe by remember { mutableStateOf(prefs.autoAfterSwipe) }

    SectionCard("浮動按鈕與翻譯") {
        LabeledSwitch("顯示浮動小圓點", bubbleOn, enabled = serviceOn) {
            bubbleOn = it
            val svc = TranslatorAccessibilityService.instance
            if (svc != null) svc.setBubbleEnabled(it) else prefs.bubbleEnabled = it
        }

        Text("翻譯模式（也可長按小圓點切換）", color = TextLo, fontSize = 13.sp)
        ChipRow(TranslateMode.entries, mode, { it.label }) {
            mode = it
            prefs.mode = it
            TranslatorAccessibilityService.instance?.refreshBubbleStyle()
        }
        Text(
            if (mode == TranslateMode.FAST) "快速模式：本機 OCR 辨識文字 → 只把文字送去翻譯。最快、最省額度。"
            else "漫畫模式：截圖和 OCR 結果一起送給視覺模型，修正手寫字體並補上漏掉的對話框。較準、多 1～3 秒。小圓點右上角會有琥珀色點。",
            color = TextLo, fontSize = 13.sp, lineHeight = 19.sp,
        )

        Text("原文語言（OCR 辨識用）", color = TextLo, fontSize = 13.sp)
        ChipRow(OcrScript.entries, script, { it.label }) {
            script = it
            prefs.ocrScript = it
        }

        LabeledSwitch("在翻譯畫面上滑動翻頁後，自動翻譯下一頁", autoSwipe) {
            autoSwipe = it
            prefs.autoAfterSwipe = it
        }

        LabeledSlider("小圓點大小", "${size.roundToInt()} dp", size, 34f..64f) {
            size = it
            prefs.bubbleSizeDp = it.roundToInt()
            TranslatorAccessibilityService.instance?.refreshBubbleStyle()
        }
        LabeledSlider("小圓點不透明度", "${(alpha * 100).roundToInt()}%", alpha, 0.3f..1f) {
            alpha = it
            prefs.bubbleAlpha = it
            TranslatorAccessibilityService.instance?.refreshBubbleStyle()
        }
        LabeledSlider("譯文字級上限", "${maxSp.roundToInt()} sp", maxSp, 12f..26f) {
            maxSp = it
            prefs.maxTextSp = it.roundToInt()
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = if (enabled) TextHi else TextLo, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = TextHi, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = TextLo, fontSize = 14.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
            )
        }
    }
}

// ── 3. 翻譯引擎 ─────────────────────────────────────

@Composable
private fun EngineCard(prefs: Prefs) {
    var primary by remember { mutableStateOf(prefs.primary) }
    var fallback by remember { mutableStateOf(prefs.fallback) }
    var vision by remember { mutableStateOf(prefs.vision) }

    SectionCard("翻譯引擎") {
        Text("快速模式主要引擎", color = TextLo, fontSize = 13.sp)
        ChipRow(ProviderId.entries, primary, { it.label }) {
            primary = it
            prefs.primary = it
        }
        Text("備援引擎（主要引擎失敗、超時或額度用完時自動改用）", color = TextLo, fontSize = 13.sp)
        ChipRow(listOf<ProviderId?>(null) + ProviderId.entries, fallback, { it?.label ?: "不使用" }) {
            fallback = it
            prefs.fallback = it
        }
        Text("漫畫模式引擎（需支援圖片輸入）", color = TextLo, fontSize = 13.sp)
        ChipRow(ProviderId.entries, vision, { it.label }) {
            vision = it
            prefs.vision = it
        }
        HorizontalDivider(color = SurfaceHi)
        Text("各引擎設定", color = TextHi, fontSize = 15.sp)
        ProviderId.entries.forEach { ProviderEditor(prefs, it) }
    }
}

@Composable
private fun ProviderEditor(prefs: Prefs, id: ProviderId) {
    var cfg by remember { mutableStateOf(prefs.provider(id)) }
    var expanded by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(true) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun update(newCfg: ProviderConfig) {
        cfg = newCfg
        prefs.saveProvider(newCfg)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceHi),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(id.label, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (cfg.isUsable) "已設定・${cfg.model}" else "尚未設定",
                        color = if (cfg.isUsable) Teal else TextLo, fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = "展開")
                }
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(id.hint, color = TextLo, fontSize = 12.sp, lineHeight = 17.sp)
                    OutlinedTextField(
                        value = cfg.apiKey,
                        onValueChange = { update(cfg.copy(apiKey = it)) },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "顯示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cfg.baseUrl,
                        onValueChange = { update(cfg.copy(baseUrl = it)) },
                        label = { Text("Base URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box {
                        OutlinedTextField(
                            value = cfg.model,
                            onValueChange = { update(cfg.copy(model = it)) },
                            label = { Text("模型") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            models.forEach { m ->
                                DropdownMenuItem(text = { Text(m, fontSize = 14.sp) }, onClick = {
                                    update(cfg.copy(model = m))
                                    modelMenu = false
                                })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cfg.temperature,
                            onValueChange = { update(cfg.copy(temperature = it)) },
                            label = { Text("temperature") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.4f),
                        )
                        OutlinedTextField(
                            value = cfg.extraJson,
                            onValueChange = { update(cfg.copy(extraJson = it)) },
                            label = { Text("額外參數 JSON") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                testOutput = null
                                try {
                                    models = LlmClient.listModels(cfg)
                                    if (models.isEmpty()) {
                                        testOk = false
                                        testOutput = "沒有取得任何模型"
                                    } else {
                                        modelMenu = true
                                    }
                                } catch (e: Exception) {
                                    testOk = false
                                    testOutput = "取得模型失敗：${e.message}"
                                }
                            }
                        }) { Text("模型列表") }
                        Button(
                            enabled = !testing,
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
                            onClick = {
                                scope.launch {
                                    testing = true
                                    testOutput = null
                                    val t0 = System.currentTimeMillis()
                                    try {
                                        val out = Translator(prefs).testProvider(cfg)
                                        testOk = true
                                        testOutput = "✓ ${System.currentTimeMillis() - t0} ms\n$out"
                                    } catch (e: Exception) {
                                        testOk = false
                                        testOutput = "✗ ${e.message}"
                                    } finally {
                                        testing = false
                                    }
                                }
                            },
                        ) {
                            if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("測試翻譯")
                        }
                        TextButton(onClick = {
                            update(
                                cfg.copy(
                                    baseUrl = id.defaultBaseUrl,
                                    model = id.defaultModel,
                                    extraJson = id.defaultExtraJson,
                                    temperature = id.defaultTemperature,
                                )
                            )
                        }) { Text("還原預設") }
                    }
                    testOutput?.let {
                        Text(it, color = if (testOk) Teal else Rose, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

// ── 4. ColorOS 穩定性設定 ──────────────────────────────

@Composable
private fun ColorOsCard(activity: MainActivity) {
    SectionCard("OPPO / ColorOS 穩定性設定") {
        Text(
            "ColorOS 會主動關閉背景 App，可能導致無障礙服務被停用、小圓點消失。建議在「應用程式資訊」中：\n" +
                "• 電池 → 選「不限制」（或關閉「最佳化電池使用」）\n" +
                "• 開啟「允許自動啟動」與「允許背景活動」\n" +
                "• 在多工畫面中把「螢幕翻譯」往下拉鎖定\n" +
                "若小圓點突然消失，回到這裡看「無障礙服務」狀態，重新開啟即可。",
            color = TextLo, fontSize = 13.sp, lineHeight = 19.sp,
        )
        OutlinedButton(onClick = { activity.openAppDetails() }) { Text("開啟應用程式資訊") }
    }
}
