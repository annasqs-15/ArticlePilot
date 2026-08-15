package com.articlepilot.automation.profiles

import com.articlepilot.automation.selectors.SelectorCatalog
import com.articlepilot.core.validator.PlatformValidationPolicy

interface PublishingProfile {
    val id: String
    val version: String
    val siteBaseUrl: String
    val selectors: SelectorCatalog
    val validationPolicy: PlatformValidationPolicy
    val capabilities: PublishingCapabilities
}

data class PublishingCapabilities(
    val supportsCoverUpload: Boolean,
    val supportsSectionImages: Boolean,
    val supportsManualTakeover: Boolean,
)

/** Placeholder until the IDN Times editor DOM and publishing requirements are verified. */
object UnconfiguredIdnTimesProfile {
    const val PROFILE_ID = "idn-times-unconfigured"
}
