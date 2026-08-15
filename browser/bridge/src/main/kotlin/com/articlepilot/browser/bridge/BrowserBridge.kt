package com.articlepilot.browser.bridge

import com.articlepilot.browser.session.BrowserPage

interface BrowserBridge {
    suspend fun send(command: BrowserCommand): BridgeResult
}

sealed interface BrowserCommand {
    data object InspectPage : BrowserCommand
    data class FindSemanticElement(val selector: SemanticSelector) : BrowserCommand
    data class ExecuteVerifiedAction(val action: VerifiedAction) : BrowserCommand
}

sealed interface SemanticSelector {
    data class ByRole(val role: String, val name: String? = null) : SemanticSelector
    data class ByLabel(val label: String) : SemanticSelector
    data class ByTestId(val value: String) : SemanticSelector
    data class ByCss(val value: String) : SemanticSelector
}

sealed interface VerifiedAction {
    data class FillText(val selector: SemanticSelector, val value: String) : VerifiedAction
    data class Click(val selector: SemanticSelector) : VerifiedAction
    data class UploadFile(val selector: SemanticSelector, val localPath: String) : VerifiedAction
}

sealed interface BridgeResult {
    data class PageInspected(val page: BrowserPage, val evidence: List<String>) : BridgeResult
    data class ElementLocated(val selector: SemanticSelector, val evidence: String) : BridgeResult
    data class ActionVerified(val action: VerifiedAction, val evidence: String) : BridgeResult
    data class Paused(val reason: String) : BridgeResult
    data class Failed(val reason: String) : BridgeResult
}
