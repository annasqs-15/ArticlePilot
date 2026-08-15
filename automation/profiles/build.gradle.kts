plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":automation:selectors"))
    implementation(project(":core:validator"))
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}
