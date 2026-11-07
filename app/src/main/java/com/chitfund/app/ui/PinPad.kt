package com.chitfund.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitfund.app.CheckResult
import kotlinx.coroutines.delay

/** Message for a wrong PIN or answer. `what` is "PIN" or "answer". */
fun failText(r: CheckResult, what: String): String =
    if (r.lockedSeconds > 0) {
        val s = r.lockedSeconds
        val wait = if (s >= 90) "${(s + 59) / 60} minutes" else "$s seconds"
        "Too many wrong tries. Please wait $wait."
    } else {
        "Wrong $what. ${r.triesLeft} tries left."
    }

/**
 * Green screen with 4 dots and a number pad. Calls [onComplete] with the 4 digits.
 * Change [resetKey] to clear the dots (for example after a wrong PIN).
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    resetKey: Any,
    onComplete: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onFingerprint: (() -> Unit)? = null,
    footer: @Composable () -> Unit = {}
) {
    var digits by remember(resetKey) { mutableStateOf("") }
    val finish by rememberUpdatedState(onComplete)

    LaunchedEffect(digits) {
        if (digits.length == 4) {
            delay(120)
            finish(digits)
            digits = ""
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F5132), Color(0xFF146C43))))
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 64.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < digits.length) Color.White else Color.Transparent)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                error ?: " ",
                color = Color(0xFFFFCDD2),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(14.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "<")
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        row.forEach { k ->
                            PadKey(k) {
                                if (k == "<") {
                                    if (digits.isNotEmpty()) digits = digits.dropLast(1)
                                } else if (digits.length < 4) {
                                    digits += k
                                }
                            }
                        }
                    }
                }
            }

            if (onFingerprint != null) {
                Spacer(Modifier.height(18.dp))
                TextButton(
                    onClick = onFingerprint,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use fingerprint")
                }
            }
            footer()
        }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit) {
    if (label.isEmpty()) {
        Spacer(Modifier.size(68.dp))
        return
    }
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label == "<") {
            Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = Color.White)
        } else {
            Text(label, color = Color.White, fontSize = 24.sp)
        }
    }
}
