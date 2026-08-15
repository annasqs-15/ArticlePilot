package com.articlepilot.browser.bridge

import com.articlepilot.browser.session.BrowserInspection
import com.articlepilot.browser.session.BrowserSession

/**
 * The current bridge permits inspection only. Mutation and selector operations remain paused
 * until current authenticated editor evidence and selector contracts are reviewed.
 */
class SafeInspectionBrowserBridge(
    private val session: BrowserSession,
) : BrowserBridge {
    override suspend fun send(command: BrowserCommand): BridgeResult = when (command) {
        BrowserCommand.InspectPage -> {
            val inspection: BrowserInspection = session.inspect()
            BridgeResult.PageInspected(inspection.toBrowserPage(), inspection.evidence)
        }
        is BrowserCommand.FindSemanticElement -> BridgeResult.Paused(
            "Semantic element lookup is not enabled before authenticated editor evidence is reviewed",
        )
        is BrowserCommand.ExecuteVerifiedAction -> BridgeResult.Paused(
            "Browser mutation actions are disabled; manual takeover is required",
        )
    }
}

private fun BrowserInspection.toBrowserPage(): com.articlepilot.browser.session.BrowserPage = page
