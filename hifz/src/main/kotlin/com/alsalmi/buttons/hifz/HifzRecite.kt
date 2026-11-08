package com.alsalmi.buttons.hifz

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// ===== «تسميع» (his request, Nov 8): recite from memory, each word appears when it is heard right.
// The shown Qur'an text is never changed; the words are only simplified here, for comparing.

internal object HfRecite {
    /** Simplified form for comparing only: no tashkeel or Qur'anic marks, one alef, ه for ة, ي for ى, no tatweel or hamza. */
    fun key(w: String): String {
        val b = StringBuilder()
        for (c in HfText.norm(w)) {
            val v = c.code
            if (v == 0x0621 || v == 0x0640 || v == 0x200C || v == 0x200D) continue
            if (v in 0x0600..0x06FF && !(v in 0x0620..0x064A) && !(v in 0x0671..0x06D3)) continue
            if (c.isWhitespace() || c == '۞' || c == '۩') continue
            b.append(when (v) { 0x0671 -> 'ا'; 0x06CC -> 'ي'; 0x06A9 -> 'ك'; 0x0649 -> 'ي'; 0x0629 -> 'ه'; else -> c })
        }
        return b.toString()
    }

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    /** True when the heard word is the expected one (Levenshtein ≤ 1 for words of 4+ letters; the Uthmani spelling's missing alefs are forgiven). */
    fun same(heard: String, expected: String): Boolean {
        val h = key(heard); val e = key(expected)
        if (h.isEmpty() || e.isEmpty()) return false
        if (h == e) return true
        if (h.replace("ا", "") == e.replace("ا", "")) return true
        return maxOf(h.length, e.length) >= 4 && lev(h, e) <= 1
    }
}

/** What the reciting card shows and does. Words of the whole range, in order. */
internal class HfReciteState(val words: List<Pair<Int, String>>) {
    var done by mutableIntStateOf(0)
    var missed by mutableIntStateOf(-1)
    var listening by mutableStateOf(false)
    var message by mutableStateOf("")
    private var usedInUtterance = 0

    fun reset() { done = 0; missed = -1; usedInUtterance = 0; message = "" }
    fun newUtterance() { usedInUtterance = 0 }
    val finished get() = done >= words.size

    /** Feeds the recogniser's text; [final] = the utterance ended (the last word is sure). */
    fun hear(text: String, final: Boolean) {
        val heard = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val upto = if (final) heard.size else heard.size - 1
        while (usedInUtterance < upto && !finished) {
            val h = heard[usedInUtterance]
            if (HfRecite.same(h, words[done].second)) { done++; missed = -1 }
            // A word may be heard as two (or two as one): try joining with the next one.
            else if (usedInUtterance + 1 < upto && HfRecite.same(h + heard[usedInUtterance + 1], words[done].second)) { done++; missed = -1; usedInUtterance++ }
            else if (done + 1 < words.size && HfRecite.same(h, words[done].second + words[done + 1].second)) { done += 2; missed = -1 }
            else missed = done
            usedInUtterance++
        }
        if (final) usedInUtterance = 0
    }

    /** How many words of ayah [k] are revealed, and which of its words is marked missed (-1 = none). */
    fun shownIn(k: Int): Int { var n = 0; for (i in 0 until minOf(done, words.size)) if (words[i].first == k) n++; return n }
    fun missedIn(k: Int): Int {
        if (missed < 0 || missed >= words.size || words[missed].first != k) return -1
        var n = 0; for (i in 0 until missed) if (words[i].first == k) n++; return n
    }
}

/** The mic panel above the verses in «تسميع». */
@Composable
internal fun HfRecitePanel(r: HfReciteState) {
    val ctx = LocalContext.current
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    DisposableEffect(recognizer) { onDispose { runCatching { recognizer?.destroy() } } }
    var wantListen by remember { mutableStateOf(false) }

    fun startOnce() {
        val rz = recognizer ?: return
        r.newUtterance()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        runCatching { rz.startListening(i) }.onFailure { r.message = "ما قدرنا نشغّل المايك. جرّب مرة ثانية."; wantListen = false; r.listening = false }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { r.listening = true; r.message = "" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { r.hear(it, final = false) }
            }
            override fun onResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { r.hear(it, final = true) }
                r.listening = false
                if (r.finished) { wantListen = false; r.message = "ما شاء الله! سمّعت المقطع كامل." } else if (wantListen) startOnce()
            }
            override fun onError(error: Int) {
                r.listening = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (wantListen && !r.finished) startOnce()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { wantListen = false; r.message = "ما نقدر نسمعك بدون إذن المايك. فعّله من إعدادات الجوال ← التطبيقات ← الأذونات." }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        { wantListen = false; r.message = "التعرّف على الصوت يحتاج إنترنت في جوالك (أو نزّل العربية للاستخدام بدون نت من إعدادات الكتابة بالصوت)." }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { if (wantListen) startOnce() }
                    else -> { wantListen = false; r.message = "صار خطأ في المايك. اضغط عليه وجرّب مرة ثانية." }
                }
            }
        })
        onDispose { }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { wantListen = true; startOnce() }
        else r.message = "ما نقدر نسمعك بدون إذن المايك. فعّله من إعدادات الجوال ← التطبيقات ← الأذونات."
    }

    fun tapMic() {
        if (recognizer == null) { r.message = "التعرّف على الصوت مو متوفر في جوالك. ثبّت تطبيق Google (أو فعّل الكتابة بالصوت) وجرّب مرة ثانية."; return }
        if (wantListen) { wantListen = false; r.listening = false; runCatching { recognizer.stopListening() }; return }
        if (r.finished) r.reset()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { wantListen = true; startOnce() }
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val pulse = rememberInfiniteTransition(label = "mic")
    val sc by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "micS")
    HfCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (wantListen) "أسمعك… اقرأ من حفظك" else "اضغط المايك واقرأ من حفظك، وتطلع كل كلمة تقولها صح",
                color = HfC.text2, fontSize = 13.sp, textAlign = TextAlign.Center)
            Box(
                Modifier.scale(if (wantListen) sc else 1f).size(72.dp).clip(CircleShape)
                    .background(if (wantListen) HfC.warn else HfC.accent).hfPress(onClick = { tapMic() })
                    .semantics { contentDescription = if (wantListen) "إيقاف المايك" else "المايك" },
                contentAlignment = Alignment.Center,
            ) { HfMicGlyph(Color.White) }
            Text("${minOf(r.done, r.words.size)} / ${r.words.size} كلمة", color = HfC.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (r.missed >= 0 && !r.finished) Text("الكلمة الملوّنة ما انسمعت صح، أعدها", color = HfC.warn, fontSize = 12.sp, textAlign = TextAlign.Center)
            if (r.message.isNotEmpty()) Text(r.message, color = if (r.finished) HfC.good else HfC.warn, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (r.done > 0) Text("من جديد", color = HfC.accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.soft).hfPress(onClick = { r.reset() }).padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

/** A plain microphone, drawn here so the module needs no icon pack. */
@Composable
private fun HfMicGlyph(c: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(c, topLeft = androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.06f), size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f))
        drawArc(c, 0f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.28f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f))
        drawLine(c, androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.78f), androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.92f), strokeWidth = w * 0.07f)
        drawLine(c, androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.93f), androidx.compose.ui.geometry.Offset(w * 0.66f, h * 0.93f), strokeWidth = w * 0.07f)
    }
}
