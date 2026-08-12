package com.applock.presentation.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Numeric PIN pad. [onPinComplete] fires when [pinLength] digits are entered;
 * return true to clear the input (wrong PIN), false to leave it.
 *
 * Phase 2 hook: randomized key layout goes here (shuffle `digits`).
 */
@Composable
fun PinPad(
    onPinComplete: (CharArray) -> Boolean,
    pinLength: Int = 4,
) {
    var input by remember { mutableStateOf("") }

    fun press(digit: Char) {
        if (input.length >= pinLength) return
        input += digit
        if (input.length == pinLength) {
            val pin = input.toCharArray()
            val clear = onPinComplete(pin)
            if (clear) input = ""
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Dots indicator
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(pinLength) { i ->
                Surface(
                    shape = CircleShape,
                    color = if (i < input.length) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(16.dp),
                ) {}
            }
        }
        Spacer(Modifier.height(32.dp))

        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )
        rows.forEach { row ->
            Row {
                row.forEach { digit -> PinKey(digit.toString()) { press(digit) } }
            }
        }
        Row {
            Box(Modifier.size(88.dp)) // spacer where a key would be
            PinKey("0") { press('0') }
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = { input = input.dropLast(1) },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
