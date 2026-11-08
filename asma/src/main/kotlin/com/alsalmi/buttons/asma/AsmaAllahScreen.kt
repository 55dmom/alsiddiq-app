package com.alsalmi.buttons.asma

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «أسماء الله الحسنى» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmaAllahScreen(onBack: (() -> Unit)? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val keys = remember { ASMA_NAMES.associate { it.n to arKey(it.plain + " " + it.meaning) } }
    val results by remember {
        derivedStateOf {
            val q = arKey(query.trim())
            when {
                q.isEmpty() -> ASMA_NAMES
                q.all { it.isDigit() } -> q.map { Character.getNumericValue(it) }.joinToString("").toIntOrNull()
                    ?.let { n -> ASMA_NAMES.filter { it.n == n } } ?: emptyList()
                else -> ASMA_NAMES.filter { keys[it.n]?.contains(q) == true }
            }
        }
    }

    AsmTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("أسماء الله الحسنى", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AsmCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("قال النبي ﷺ: «$ASMA_HADITH»", fontSize = 17.sp, lineHeight = 28.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.size(6.dp))
                            Text(ASMA_HADITH_REF, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "مسح") }
                        },
                        placeholder = { Text("ابحث بالاسم أو المعنى أو الرقم") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
                if (results.isEmpty()) item {
                    Text("ما فيه نتائج", Modifier.fillMaxWidth().padding(top = 30.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results, key = { it.n }) { x ->
                    AsmCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("${x.n}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(14.dp))
                            SelectionContainer {
                                Column {
                                    Text(x.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                                    Text(x.meaning, fontSize = 16.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ready-made full-screen Activity: `AsmaAllahActivity.start(context)`. */
class AsmaAllahActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AsmaAllahScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AsmaAllahActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
