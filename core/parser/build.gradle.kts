plugins {
    id("org.jetbrains.kotlin.jvm")
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("fixtures/article-format/v1.0"))
    }
}

dependencies {
    implementation(project(":core:model"))
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
