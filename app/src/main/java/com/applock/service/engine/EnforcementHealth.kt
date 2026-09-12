package com.applock.service.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The owner of the enforcement health (M7 WP2 change D). This is the `enforcement.health` oracle dimension
 * (Phase 3). One serialized owner folds three INDEPENDENT facts into one derived [State] flow. Each fact has its
 * own producer on its own schedule. Thus the owner stores the facts separately, and one fact never overwrites
 * another:
 *
 *  - [Detector]: whether the foreground detector is enabled. WP3 changes the source here from a11y to Usage
 *    Access. WP4 does the full watchdog rewrite. Neither changes this aggregation.
 *  - [OverlayGrant]: whether the system grants `SYSTEM_ALERT_WINDOW`. The change-E capability check submits it.
 *  - [Presentation]: the last apply outcome of a surface, from [LockPresenter.present] or [LockPresenter.dismiss].
 *    The interpreter submits it after each apply, so a failed hide is as visible as a failed show.
 *
 * Only this owner mutates the derived flow. Each producer submits a fact, and the owner recomputes. The submit
 * methods are `@Synchronized`. Thus the read-modify-write of the three facts and the single publish are atomic
 * under concurrent producers, and only the owner mutates the flow. Phase-2 health is the conjunction
 * `detector == Enabled && overlayGrant == Granted && presentation != Failed` ([State.healthy]).
 *
 * The initial facts are fail-secure. Until a producer says otherwise, the detector is disabled and the overlay
 * grant is missing, so health starts false. The presentation fact is asymmetric: it holds health false only when
 * it is [Presentation.Failed]. Thus before any present ([Presentation.Unknown]), the mechanism can be healthy
 * once the detector is enabled and the overlay is granted. The interpreter attempts a present only when there is
 * something to lock. So health becomes true from a positive detector fact, a positive overlay fact, and no
 * present failure. It does not need a positive present.
 */
class EnforcementHealth(
    detector: Detector = Detector.Disabled,
    overlayGrant: OverlayGrant = OverlayGrant.Missing,
    presentation: Presentation = Presentation.Unknown,
) {

    enum class Detector { Enabled, Disabled }

    enum class OverlayGrant { Granted, Missing }

    /** The last apply outcome of a surface (present or dismiss). [Failed] carries a short [reason] that is never
     *  shown to the user. */
    sealed interface Presentation {
        data object Unknown : Presentation
        data object Succeeded : Presentation
        data class Failed(val reason: String) : Presentation
    }

    /** The three facts and their Phase-2 aggregation. This is a read-model; [EnforcementHealth] owns the live one. */
    data class State(
        val detector: Detector,
        val overlayGrant: OverlayGrant,
        val presentation: Presentation,
    ) {
        val healthy: Boolean
            get() = detector == Detector.Enabled &&
                overlayGrant == OverlayGrant.Granted &&
                presentation !is Presentation.Failed
    }

    private val _state = MutableStateFlow(State(detector, overlayGrant, presentation))

    /** The derived health. The owner recomputes it on each fact submission. The watchdog, the banner, and the
     *  oracle read it. */
    val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    fun submitDetector(value: Detector) {
        _state.value = _state.value.copy(detector = value)
    }

    @Synchronized
    fun submitOverlayGrant(value: OverlayGrant) {
        _state.value = _state.value.copy(overlayGrant = value)
    }

    @Synchronized
    fun submitPresentation(value: Presentation) {
        _state.value = _state.value.copy(presentation = value)
    }
}
