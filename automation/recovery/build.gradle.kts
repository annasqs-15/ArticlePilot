plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":automation:state"))
    testImplementation(project(":core:model"))
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}
