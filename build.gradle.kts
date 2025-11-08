// build.gradle.kts (Project Level)

plugins {
    // Android
    id("com.android.application") version "8.4.1" apply false

    // Kotlin
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false

    // Hilt
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}