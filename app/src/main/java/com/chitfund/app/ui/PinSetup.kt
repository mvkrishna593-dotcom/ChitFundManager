package com.chitfund.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.chitfund.app.SecurityViewModel

private val PRESETS = listOf(
    "What is your mother's maiden name?",
    "What was the name of your first school?",
    "What is the name of your favourite teacher?",
    "In which town were you born?",
    "What was the name of your first pet?",
    "What is your favourite food?"
)
private const val OWN = "Write my own question"

/** Create a PIN (and a security question the first time), or change the PIN. */
@Composable
fun PinSetupScreen(vm: SecurityViewModel, changing: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val question by vm.question.collectAsState()
    var step by remember { mutableStateOf(if (changing) "old" else "new") }
    var error by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }
    var firstPin by remember { mutableStateOf("") }

    when (step) {
        "old" -> PinPad(
            title = "Enter your current PIN",
            subtitle = "To change your PIN",
            error = error,
            resetKey = resetKey,
            onBack = onBack,
            onComplete = { pin ->
                val r = vm.checkPin(pin)
                if (r.ok) { error = null; resetKey++; step = "new" }
                else { error = failText(r, "PIN"); resetKey++ }
            }
        )

        "new" -> PinPad(
            title = "Create a 4-digit PIN",
            subtitle = "Asked every time you open the app",
            error = error,
            resetKey = resetKey,
            onBack = onBack,
            onComplete = { pin ->
                firstPin = pin; error = null; resetKey++; step = "confirm"
            }
        )

        "confirm" -> PinPad(
            title = "Confirm your PIN",
            subtitle = "Enter the same 4 digits again",
            error = error,
            resetKey = resetKey,
            onBack = { error = null; resetKey++; step = "new" },
            onComplete = { pin ->
                if (pin != firstPin) {
                    error = "The PINs do not match. Try again."; resetKey++
                } else if (question != null) {
                    vm.setPin(pin); onDone()
                } else {
                    step = "question"
                }
            }
        )

        else -> QuestionPage(
            title = "Security question",
            buttonText = "Save and turn on lock",
            initialQuestion = null,
            onBack = { step = "confirm"; resetKey++ },
            onSave = { q, a ->
                vm.setPin(firstPin)
                vm.saveQuestion(q, a)
                onDone()
            }
        )
    }
}

/** Change the security question and answer. */
@Composable
fun QuestionScreen(vm: SecurityViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val question by vm.question.collectAsState()
    QuestionPage(
        title = "Security question",
        buttonText = "Save",
        initialQuestion = question,
        onBack = onBack,
        onSave = { q, a -> vm.saveQuestion(q, a); onDone() }
    )
}

@Composable
private fun QuestionPage(
    title: String,
    buttonText: String,
    initialQuestion: String?,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    SimplePage(title, onBack) {
        Text(
            "If you forget your PIN, you can set a new one by answering this question.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        val startsCustom = initialQuestion != null && initialQuestion !in PRESETS
        var choice by remember { mutableStateOf(if (startsCustom) OWN else initialQuestion ?: PRESETS[0]) }
        var custom by remember { mutableStateOf(if (startsCustom) initialQuestion ?: "" else "") }
        var answer by remember { mutableStateOf("") }
        var open by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(choice, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (PRESETS + OWN).forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = { choice = it; open = false })
                }
            }
        }
        if (choice == OWN) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("Your question") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Your answer") },
            singleLine = true,
            supportingText = { Text("Capital or small letters do not matter") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val finalQuestion = if (choice == OWN) custom.trim() else choice
        Button(
            onClick = { onSave(finalQuestion, answer) },
            enabled = finalQuestion.isNotEmpty() && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(buttonText) }
    }
}

/** Asks for the current PIN before a sensitive change. */
@Composable
fun VerifyPinDialog(vm: SecurityViewModel, onDismiss: () -> Unit, onVerified: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) { pin = v; error = null } },
                label = { Text("4-digit PIN") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4,
                onClick = {
                    val r = vm.checkPin(pin)
                    if (r.ok) onVerified() else { error = failText(r, "PIN"); pin = "" }
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
