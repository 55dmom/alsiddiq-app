package com.alsiddiq.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.alsiddiq.app.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalContainer = compositionLocalOf<AppContainer> { error("no container") }
val LocalSnackbar = compositionLocalOf { SnackbarHostState() }
/** Long-lived scope so a message survives the screen that triggered it closing. */
val LocalAppScope = compositionLocalOf<CoroutineScope?> { null }

/** Runs a repository call, shows its success or error message, then continues. */
class Actions(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    fun <T> run(okMessage: String?, block: suspend () -> Result<T>, onError: () -> Unit = {}, then: (T) -> Unit = {}) {
        scope.launch {
            val r = block()
            r.onSuccess {
                if (okMessage != null) launch { snackbar.showSnackbar(okMessage) }
                then(it)
            }.onFailure {
                onError()
                launch { snackbar.showSnackbar(it.message ?: "حدث خطأ، حاول مرة أخرى") }
            }
        }
    }

    fun say(message: String) { scope.launch { snackbar.showSnackbar(message) } }
}

@Composable
fun rememberActions(): Actions {
    val local = rememberCoroutineScope()
    val scope = LocalAppScope.current ?: local
    val sb = LocalSnackbar.current
    return remember(scope, sb) { Actions(scope, sb) }
}
