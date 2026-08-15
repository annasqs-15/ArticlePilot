plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":browser:session"))
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}
