package com.applock.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applock.R
import com.applock.applocker.service.AppDetectionService
import com.applock.applocker.service.ProtectionWatchdogService
import com.applock.authentication.ui.PinPad
import com.applock.core.Graph
import com.applock.core.security.LockoutState
import com.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FR-171: settings/app-list show what's protected — keep them out of
        // screenshots in release. Debug stays capturable for emulator E2E.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        if (Graph.credentialRepository.isPinSet()) {
            ProtectionWatchdogService.start(this)
        }

        setContent {
            AppLockTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppLockNav()
                }
            }
        }
    }
}

private enum class Screen { PIN_SETUP, SELF_GATE, APP_LIST, SETTINGS }

@Composable
private fun AppLockNav() {
    val pinSet = Graph.credentialRepository.isPinSet()
    var screen by rememberSaveable {
        mutableStateOf(if (pinSet) Screen.SELF_GATE else Screen.PIN_SETUP)
    }

    when (screen) {
        Screen.PIN_SETUP -> PinSetupScreen(onDone = { screen = Screen.APP_LIST })
        Screen.SELF_GATE -> SelfGateScreen(onUnlocked = { screen = Screen.APP_LIST })
        Screen.APP_LIST -> AppListScreen(onOpenSettings = { screen = Screen.SETTINGS })
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.APP_LIST })
    }
}

@Composable
private fun PinSetupScreen(onDone: () -> Unit) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    PinEntryScaffold(
        title = stringResource(
            if (firstPin == null) R.string.set_pin_title else R.string.confirm_pin_title
        ),
        subtitle = if (error) stringResource(R.string.pins_do_not_match) else null,
    ) { pin ->
        val entered = String(pin)
        if (firstPin == null) {
            firstPin = entered
            error = false
        } else if (firstPin == entered) {
            Graph.credentialRepository.setPin(entered.toCharArray())
            onDone()
        } else {
            firstPin = null
            error = true
        }
        true
    }
}

@Composable
private fun SelfGateScreen(onUnlocked: () -> Unit) {
    var error by remember { mutableStateOf(false) }

    // The app's own gate counts toward the same lockout as protected apps
    // (FR-174) — otherwise it would be a free brute-force surface.
    var lockoutRemainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            lockoutRemainingMs =
                (Graph.lockoutManager.currentState() as? LockoutState.LockedOut)
                    ?.remainingMs ?: 0L
            delay(250)
        }
    }

    val subtitle = if (lockoutRemainingMs > 0) {
        val totalSeconds = (lockoutRemainingMs + 999) / 1000
        stringResource(
            R.string.lockout_countdown,
            "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
        )
    } else if (error) {
        stringResource(R.string.pin_incorrect)
    } else {
        stringResource(R.string.enter_pin)
    }

    PinEntryScaffold(
        title = stringResource(R.string.app_name),
        subtitle = subtitle,
    ) { pin ->
        if (lockoutRemainingMs > 0 ||
            Graph.lockoutManager.currentState() is LockoutState.LockedOut
        ) {
            return@PinEntryScaffold true
        }
        if (Graph.credentialRepository.verifyPin(pin)) {
            Graph.lockoutManager.recordSuccess()
            onUnlocked()
            false
        } else {
            error = true
            Graph.lockoutManager.recordFailure()
            true
        }
    }
}

@Composable
private fun PinEntryScaffold(
    title: String,
    subtitle: String?,
    onPinComplete: (CharArray) -> Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        subtitle?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        PinPad(onPinComplete = onPinComplete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    onOpenSettings: () -> Unit,
    viewModel: AppListViewModel = viewModel(),
) {
    val apps by viewModel.apps.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Re-check accessibility permission every time we come back to the app.
    var serviceEnabled by remember { mutableStateOf(AppDetectionService.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = AppDetectionService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!serviceEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.accessibility_needed_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.accessibility_needed_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }
            }
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = app.isProtected,
                            onCheckedChange = { viewModel.setProtected(app.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}
