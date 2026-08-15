package com.articlepilot.app.workspace.application

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ArticleWorkspaceViewModelFactory(
    private val dependencies: ArticleWorkspaceDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ArticleWorkspaceViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return ArticleWorkspaceViewModel(
            parser = dependencies.parser,
            validator = dependencies.validator,
            mediaPipelineFactory = dependencies.mediaPipelineFactory,
            validationPolicy = dependencies.validationPolicy,
        ) as T
    }
}
