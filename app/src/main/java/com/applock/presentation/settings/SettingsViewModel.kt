package com.applock.presentation.settings

import androidx.lifecycle.ViewModel
import com.applock.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * WP5 (M1): `SettingsScreen` read/wrote `Graph.settings.*` directly. This thin `@HiltViewModel`
 * supplies the same [SettingsRepository] via `hiltViewModel()` — a mechanical accessor swap
 * (`Graph.settings` → `vm.settings`), no behavior change. Proper settings state modelling is M3.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: SettingsRepository,
) : ViewModel()
