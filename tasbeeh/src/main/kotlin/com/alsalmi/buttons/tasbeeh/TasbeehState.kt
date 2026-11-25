package com.alsalmi.buttons.tasbeeh

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class TsbPhrase(val id: Int, val text: String, val ref: String)

/** Pure counter progression helper (unit-testable without Android Context). */
object TasbeehLogic {
    data class StepResult(val nextIndex: Int, val nextCount: Int, val reached: Boolean)

    /**
     * Advances a single dhikr count by 1.
     * When reaching [target] (default 33), resets count to 0 and automatically advances to the next dhikr.
     * When finishing the last dhikr, wraps back to index 0 at count 0.
     */
    fun step(currentIndex: Int, currentCount: Int, target: Int, phraseCount: Int): StepResult {
        val safeTotal = phraseCount.coerceAtLeast(1)
        val safeIdx = currentIndex.coerceIn(0, safeTotal - 1)
        val safeTarget = target.coerceAtLeast(1)
        val c = currentCount + 1
        return if (c >= safeTarget) {
            StepResult(nextIndex = (safeIdx + 1) % safeTotal, nextCount = 0, reached = true)
        } else {
            StepResult(nextIndex = safeIdx, nextCount = c, reached = false)
        }
    }
}

/** Counter state, saved in SharedPreferences "alsalmi_tasbeeh". */
internal class TasbeehState(context: Context) {
    companion object {
        val phrases = listOf(
        TsbPhrase(0, "سُبْحَانَ اللَّهِ", "مسلم 597"),
        TsbPhrase(1, "الْحَمْدُ لِلَّهِ", "مسلم 597"),
        TsbPhrase(2, "اللَّهُ أَكْبَرُ", "مسلم 597"),
        TsbPhrase(3, "لاَ إِلَهَ إِلاَّ اللَّهُ", "مسلم 2137"),
        TsbPhrase(4, "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "مسلم 2692"),
        TsbPhrase(5, "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحانَ اللَّهِ الْعَظِيمِ", "البخاري 6682، ومسلم 2694"),
        TsbPhrase(6, "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", "صحيح الجامع 944"),
        TsbPhrase(7, "لاَ حَوْلَ وَلاَ قُوَّةَ إِلاَّ بِاللَّهِ", "البخاري 6384، ومسلم 2704"),
        TsbPhrase(8, "لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", "البخاري 6403، ومسلم 2691")
        )
        val presetTargets = listOf(33, 99, 100)
    }

    private val p = context.applicationContext.getSharedPreferences("alsalmi_tasbeeh", Context.MODE_PRIVATE)

    var selected by mutableIntStateOf(p.getInt("sel", 0).coerceIn(0, phrases.size - 1))
        private set
    val counts = mutableStateListOf<Int>().apply {
        val saved = p.getString("counts", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        addAll(if (saved != null && saved.size == phrases.size) saved else List(phrases.size) { 0 })
    }
    // build 17: each dhikr counts to 33 then auto-advances to the next dhikr (wrapping back to first at 0).
    var target by mutableIntStateOf(if (p.getBoolean("t33_v17", false)) p.getInt("target", 33).let { if (it > 0) it else 33 } else 33)
        private set
    var total by mutableIntStateOf(p.getInt("total", 0))
        private set
    var haptics by mutableStateOf(p.getBoolean("haptics", true))
        private set

    val phrase get() = phrases[selected]
    val count get() = counts[selected]
    val progress get() = if (target > 0) (count.toFloat() / target).coerceAtMost(1f) else 0f
    val isCustomTarget get() = target !in presetTargets

    private fun save() {
        p.edit()
            .putInt("sel", selected)
            .putString("counts", counts.joinToString(","))
            .putInt("target", target)
            .putBoolean("t33_v17", true)
            .putInt("total", total)
            .putBoolean("haptics", haptics)
            .apply()
    }

    /** One tap; true when target (33) was reached and dhikr automatically advanced to the next one. */
    fun tap(): Boolean {
        total += 1
        val step = TasbeehLogic.step(selected, counts[selected], target, phrases.size)
        if (step.reached) {
            counts[selected] = 0
            selected = step.nextIndex
            counts[selected] = 0
        } else {
            counts[selected] = step.nextCount
        }
        save()
        return step.reached
    }

    fun select(i: Int) { selected = i.coerceIn(0, phrases.size - 1); save() }
    fun chooseTarget(t: Int) { if (t in 1..100000) { target = t; save() } }
    fun toggleHaptics() { haptics = !haptics; save() }
    fun resetCurrent() { counts[selected] = 0; save() }
    fun resetAll() {
        for (i in counts.indices) counts[i] = 0
        total = 0; save()
    }
}
