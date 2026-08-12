package com.applock.presentation.authentication

import androidx.lifecycle.ViewModel
import com.applock.security.CredentialRepository
import com.applock.security.LockoutManager
import com.applock.security.LockoutState
import com.applock.service.ApplicationLockEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * WP5 (M1): the auth-gate composables (PIN setup, self-gate) were the last direct `Graph`
 * consumers among the top-level composables. This thin `@HiltViewModel` gives them their
 * dependencies via `hiltViewModel()`. The methods are **synchronous, same-thread passthroughs**
 * — the exact accessor swap of the former `Graph.x.y()` calls, so the gate's control flow
 * (FR-174 lockout counting, engine-routed audit logging, current threading incl. Argon2 on the
 * caller thread) is unchanged. The proper MVVM state modelling lands in M3.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val lockoutManager: LockoutManager,
    private val lockEngine: ApplicationLockEngine,
) : ViewModel() {

    fun isPinSet(): Boolean = credentialRepository.isPinSet()

    fun setPin(pin: CharArray) = credentialRepository.setPin(pin)

    fun verifyPin(pin: CharArray): Boolean = credentialRepository.verifyPin(pin)

    fun lockoutState(): LockoutState = lockoutManager.currentState()

    fun onUnlockSuccess(packageName: String) = lockEngine.onUnlockSuccess(packageName)

    fun onUnlockFailure(packageName: String) = lockEngine.onUnlockFailure(packageName)
}
