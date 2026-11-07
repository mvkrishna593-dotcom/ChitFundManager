package com.chitfund.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitfund.app.Biometrics
import com.chitfund.app.SecurityViewModel
import com.chitfund.app.findFragmentActivity

/** Shown every time the app is opened while the PIN lock is on. */
@Composable
fun LockScreen(vm: SecurityViewModel, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val biometricOn by vm.biometricOn.collectAsState()
    val question by vm.question.collectAsState()
    var mode by remember { mutableStateOf("pin") }
    var error by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }
    var firstPin by remember { mutableStateOf("") }

    val canFinger = biometricOn && Biometrics.available(ctx)
    val activity = ctx.findFragmentActivity()
    val askFinger: () -> Unit = {
        if (activity != null) Biometrics.prompt(activity, "Unlock Chit Fund Manager", onUnlocked)
    }

    LaunchedEffect(Unit) {
        if (canFinger) askFinger()
    }

    when (mode) {
        "pin" -> PinPad(
            title = "Enter your PIN",
            subtitle = "Chit Fund Manager",
            error = error,
            resetKey = resetKey,
            onComplete = { pin ->
                val r = vm.checkPin(pin)
                if (r.ok) {
                    onUnlocked()
                } else {
                    error = failText(r, "PIN")
                    resetKey++
                }
            },
            onFingerprint = if (canFinger) askFinger else null,
            footer = {
                if (question != null) {
                    TextButton(
                        onClick = { error = null; mode = "answer" },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) { Text("Forgot PIN?") }
                }
            }
        )

        "answer" -> AnswerStep(
            question = question ?: "",
            onBack = { mode = "pin" },
            onCheck = { vm.checkAnswer(it) },
            onCorrect = { error = null; resetKey++; mode = "newpin" }
        )

        "newpin" -> PinPad(
            title = "Create a new PIN",
            subtitle = "Choose 4 digits",
            error = error,
            resetKey = resetKey,
            onComplete = { pin ->
                firstPin = pin
                error = null
                resetKey++
                mode = "confirmpin"
            }
        )

        else -> PinPad(
            title = "Confirm new PIN",
            subtitle = "Enter the same 4 digits again",
            error = error,
            resetKey = resetKey,
            onBack = { error = null; resetKey++; mode = "newpin" },
            onComplete = { pin ->
                if (pin == firstPin) {
                    vm.finishReset(pin)
                    onUnlocked()
                } else {
                    error = "The PINs do not match. Try again."
                    resetKey++
                }
            }
        )
    }
}

@Composable
private fun AnswerStep(
    question: String,
    onBack: () -> Unit,
    onCheck: (String) -> com.chitfund.app.CheckResult,
    onCorrect: () -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F5132))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Reset your PIN", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Answer your security question", color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(question, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it; error = null },
                    label = { Text("Your answer") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Capital or small letters do not matter") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val r = onCheck(answer)
                        if (r.ok) onCorrect() else error = failText(r, "answer")
                    },
                    enabled = answer.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue") }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) { Text("Back to PIN") }
    }
}
