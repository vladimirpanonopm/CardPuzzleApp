// Файл сборки на уровне модуля 'app'

plugins {
    // Применяем плагины, объявленные на уровне проекта
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.cardpuzzleapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cardpuzzleapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ИЗМЕНЕНИЕ: Этот блок теперь находится в правильном месте - ПОСЛЕ defaultConfig
    flavorDimensions += "version"
    productFlavors {
        create("prod") {
            dimension = "version"
            applicationIdSuffix = null
            resValue("string", "app_name_variant", "Card Puzzle")
        }
        create("sandbox") {
            dimension = "version"
            applicationIdSuffix = ".sandbox"
            resValue("string", "app_name_variant", "Puzzle Sandbox")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Основные зависимости AndroidX и Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Зависимости Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Иконки Material Design
    implementation("androidx.compose.material:material-icons-extended-android:1.6.7")

    // Сериализация KotlinX для работы с JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Зависимость appcompat, может быть полезна для совместимости
    implementation(libs.androidx.appcompat)

    // Зависимости для тестирования (стандартные)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Зависимости для отладки Compose
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}