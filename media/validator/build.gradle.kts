plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.articlepilot.media"

base {
    archivesName.set("media-validator")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":media:storage"))
    implementation(project(":media:inspection"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
