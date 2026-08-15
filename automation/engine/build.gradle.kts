plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":automation:state"))
    implementation(project(":browser:session"))
    implementation(project(":browser:bridge"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}
