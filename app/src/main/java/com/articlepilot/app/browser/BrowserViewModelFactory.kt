package com.articlepilot.app.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.articlepilot.browser.session.BrowserSession

class BrowserViewModelFactory(
    private val session: BrowserSession,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BrowserViewModel::class.java))
        return BrowserViewModel(session) as T
    }
}
