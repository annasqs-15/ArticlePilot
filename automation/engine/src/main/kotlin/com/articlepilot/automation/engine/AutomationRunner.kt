package com.articlepilot.automation.engine

import com.articlepilot.automation.state.AutomationCheckpoint
import com.articlepilot.automation.state.AutomationEvent
import com.articlepilot.automation.state.AutomationStateMachine
import com.articlepilot.automation.state.StateTransition
import com.articlepilot.browser.bridge.BrowserBridge

interface AutomationRunner {
    suspend fun run(
        checkpoint: AutomationCheckpoint,
        stateMachine: AutomationStateMachine,
        bridge: BrowserBridge,
    ): AutomationRunResult
}

sealed interface AutomationRunResult {
    data class Advanced(val transition: StateTransition) : AutomationRunResult
    data class Paused(val transition: StateTransition.Paused) : AutomationRunResult
    data class Failed(val transition: StateTransition.Failed) : AutomationRunResult
    data class Completed(val transition: StateTransition.Completed) : AutomationRunResult
}

/** Production orchestration is intentionally not implemented until platform selectors are verified. */
class UnimplementedAutomationRunner : AutomationRunner {
    override suspend fun run(
        checkpoint: AutomationCheckpoint,
        stateMachine: AutomationStateMachine,
        bridge: BrowserBridge,
    ): AutomationRunResult =
        AutomationRunResult.Paused(
            StateTransition.Paused(
                checkpoint = checkpoint,
                reason = "Automation runner is not implemented; manual takeover required.",
            ),
        )
}
