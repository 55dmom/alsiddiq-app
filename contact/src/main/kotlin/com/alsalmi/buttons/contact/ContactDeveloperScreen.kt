package com.alsalmi.buttons.contact

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Contact info (same as «تواصل معنا» in تفاؤل). Nothing is stored; no accounts, no forms. */
internal object CtInfo {
    const val TITLE = "تواصل مع المطوّر"
    const val SUBTITLE = "تواصل معنا"
    const val BODY = "راسلنا على قناة تفاؤل في تيليجرام: اقتراح، سؤال، أو استفسار."
    const val BUTTON = "افتح قناة تفاؤل في تيليجرام"
    const val LINK = "t.me/tafaulapp"
    const val URL = "https://t.me/tafaulapp"
}

/** Material "content_copy" glyph (Apache-2.0), kept here so the module needs only icons-core. */
internal val CopyIcon: ImageVector by lazy {
    ImageVector.Builder("copy", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes("M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"),
        fill = SolidColor(Color.Black),
    ).build()
}

internal val Telegram = Color(0xFF29A1DE) // rgb(0.16, 0.63, 0.87)

internal data class CtColors(
    val bg: Color, val card: Color, val line: Color, val soft: Color,
    val text: Color, val sub: Color, val grad1: Color, val grad2: Color,
)

@Composable
internal fun ctColors(dark: Boolean = isSystemInDarkTheme()) = if (dark) CtColors(
    bg = Color(0xFF0B0E12), card = Color(0xFF161A20), line = Color(0xFF262C35), soft = Color(0xFF173244),
    text = Color(0xFFF1F4F8), sub = Color(0xFF9AA4B2), grad1 = Color(0xFF1F86BC), grad2 = Color(0xFF16628F),
) else CtColors(
    bg = Color(0xFFF3F5F7), card = Color(0xFFFFFFFF), line = Color(0xFFE4E8EE), soft = Color(0xFFE1F1FA),
    text = Color(0xFF111827), sub = Color(0xFF5B6472), grad1 = Color(0xFF2AA1DE), grad2 = Color(0xFF1C7FC0),
)

internal fun openTelegram(ctx: Context) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(CtInfo.URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
}

internal fun copyLink(ctx: Context) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(CtInfo.TITLE, CtInfo.URL))
}

internal fun shareLink(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, CtInfo.URL)
    runCatching { ctx.startActivity(Intent.createChooser(send, "مشاركة الرابط").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * «تواصل مع المطوّر» — the button's single entry screen.
 * @param onBack optional; when given, a back arrow shows in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDeveloperScreen(onBack: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    val c = ctColors(dark)
    val ctx = LocalContext.current
    var appeared by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    val heroScale by animateFloatAsState(if (appeared) 1f else 0.6f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "hs")
    val cardShift by animateDpAsState(if (appeared) 0.dp else 24.dp, spring(dampingRatio = 0.8f, stiffness = 300f), label = "cs")
    val fade by animateFloatAsState(if (appeared) 1f else 0f, tween(450), label = "fa")

    val scheme = if (dark) darkColorScheme(primary = Telegram, background = c.bg, surface = c.bg)
        else lightColorScheme(primary = Telegram, background = c.bg, surface = c.bg)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = c.bg,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(CtInfo.TITLE, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.text) },
                        navigationIcon = {
                            if (onBack != null) IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = c.text)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.bg),
                    )
                },
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(pad)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        // Hero
                        Box(
                            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                                .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Telegram, spotColor = Telegram)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Brush.linearGradient(listOf(c.grad1, c.grad2))),
                        ) {
                            Box(Modifier.size(200.dp).offset(x = (-60).dp, y = (-70).dp).background(Color.White.copy(alpha = 0.10f), CircleShape))
                            Box(Modifier.size(130.dp).align(Alignment.BottomStart).offset(x = (-30).dp, y = 40.dp).background(Color.White.copy(alpha = 0.07f), CircleShape))
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.scale(heroScale).alpha(fade).size(104.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(84.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Telegram,
                                                modifier = Modifier.size(38.dp).graphicsLayer { rotationZ = -25f })
                                        }
                                    }
                                }
                                Text(CtInfo.TITLE, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(CtInfo.SUBTITLE, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        // Card
                        Column(
                            Modifier.widthIn(max = 560.dp).fillMaxWidth().offset(y = cardShift).alpha(fade)
                                .shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                                .clip(RoundedCornerShape(22.dp)).background(c.card)
                                .border(0.6.dp, c.line, RoundedCornerShape(22.dp)).padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(CtInfo.BODY, color = c.sub, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
                            PressButton(onClick = { openTelegram(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), bg = Telegram, shape = RoundedCornerShape(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -25f })
                                    }
                                    Text(CtInfo.BUTTON, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(CtInfo.LINK, color = c.sub, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmallAction(
                                    icon = if (copied) Icons.Filled.Check else CopyIcon,
                                    title = if (copied) "انتسخ" else "نسخ الرابط", c = c, modifier = Modifier.weight(1f),
                                ) { copyLink(ctx); copied = true }
                                SmallAction(icon = Icons.Filled.Share, title = "مشاركة الرابط", c = c, modifier = Modifier.weight(1f)) { shareLink(ctx) }
                            }
                        }
                        Text("ما يُحفظ أي شي، وبدون حسابات أو نماذج.", color = c.sub, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    AnimatedVisibility(
                        visible = copied,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("تم نسخ الرابط", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PressButton(onClick: () -> Unit, modifier: Modifier, bg: Color, shape: RoundedCornerShape, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f), label = "pb")
    Surface(onClick = onClick, modifier = modifier.scale(s), shape = shape, color = bg, interactionSource = src) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, title: String, c: CtColors, modifier: Modifier, onClick: () -> Unit) {
    PressButton(onClick = onClick, modifier = modifier.heightIn(min = 44.dp), bg = c.soft, shape = RoundedCornerShape(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Telegram, modifier = Modifier.size(18.dp))
            Text(title, color = Telegram, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Ready-made Activity (declared in the module's manifest). */
class ContactDeveloperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ContactDeveloperScreen(onBack = { finish() }) }
    }
    companion object {
        @JvmStatic fun start(context: Context) {
            val i = Intent(context, ContactDeveloperActivity::class.java)
            if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}
