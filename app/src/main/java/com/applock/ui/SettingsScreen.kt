package com.applock.ui

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.applock.R
import com.applock.applocker.admin.UninstallProtectionReceiver
import com.applock.applocker.session.RelockPolicy
import com.applock.core.Graph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var selected by remember { mutableStateOf(Graph.settings.relockPolicy) }

    fun select(policy: RelockPolicy) {
        selected = policy
        Graph.settings.relockPolicy = policy
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.relock_policy_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(Modifier.selectableGroup()) {
                PolicyOption(
                    title = stringResource(R.string.relock_immediate_title),
                    description = stringResource(R.string.relock_immediate_desc),
                    selected = selected == RelockPolicy.IMMEDIATE,
                    onClick = { select(RelockPolicy.IMMEDIATE) },
                )
                PolicyOption(
                    title = stringResource(R.string.relock_grace_title),
                    description = stringResource(R.string.relock_grace_desc),
                    selected = selected == RelockPolicy.GRACE_10S,
                    onClick = { select(RelockPolicy.GRACE_10S) },
                )
                PolicyOption(
                    title = stringResource(R.string.relock_screen_off_title),
                    description = stringResource(R.string.relock_screen_off_desc),
                    selected = selected == RelockPolicy.SCREEN_OFF,
                    onClick = { select(RelockPolicy.SCREEN_OFF) },
                )
            }

            val context = LocalContext.current
            // FR-002: hide the biometric option entirely on unsupported devices.
            val biometricsSupported = remember {
                BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) !=
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            }
            if (biometricsSupported) {
                var biometricsEnabled by remember {
                    mutableStateOf(Graph.settings.biometricUnlockEnabled)
                }
                ToggleOption(
                    title = stringResource(R.string.settings_biometric_title),
                    description = stringResource(R.string.settings_biometric_desc),
                    checked = biometricsEnabled,
                    onCheckedChange = {
                        biometricsEnabled = it
                        Graph.settings.biometricUnlockEnabled = it
                    },
                )
            }

            // Activation happens in a system dialog, so re-read the real state
            // whenever we come back to the foreground.
            var uninstallProtection by remember {
                mutableStateOf(UninstallProtectionReceiver.isActive(context))
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        uninstallProtection = UninstallProtectionReceiver.isActive(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            ToggleOption(
                title = stringResource(R.string.settings_uninstall_protection_title),
                description = stringResource(R.string.settings_uninstall_protection_desc),
                checked = uninstallProtection,
                onCheckedChange = { wanted ->
                    if (wanted) {
                        context.startActivity(UninstallProtectionReceiver.activationIntent(context))
                    } else {
                        UninstallProtectionReceiver.deactivate(context)
                        uninstallProtection = false
                    }
                },
            )
        }
    }
}

@Composable
private fun ToggleOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PolicyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
