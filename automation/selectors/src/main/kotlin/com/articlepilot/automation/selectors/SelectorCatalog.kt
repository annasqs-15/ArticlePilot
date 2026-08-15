package com.articlepilot.automation.selectors

interface SelectorCatalog {
    val profileId: String
    val version: String
    fun selectorFor(target: SelectorTarget): SemanticSelectorDefinition?
}

enum class SelectorTarget {
    LOGIN_INDICATOR,
    EDITOR_ENTRY,
    TITLE_FIELD,
    EXCERPT_FIELD,
    COVER_UPLOAD,
    SECTION_HEADING_FIELD,
    SECTION_TEXT_FIELD,
    IMAGE_UPLOAD,
    IMAGE_CAPTION_FIELD,
    IMAGE_SOURCE_FIELD,
    SUBMIT_ACTION,
    SUCCESS_INDICATOR,
}

data class SemanticSelectorDefinition(
    val target: SelectorTarget,
    val role: String? = null,
    val accessibleName: String? = null,
    val testId: String? = null,
    val css: String? = null,
    val requiredEvidence: List<String> = emptyList(),
)
