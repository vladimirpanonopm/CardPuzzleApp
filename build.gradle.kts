// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Файл сборки верхнего уровня, где можно добавить опции конфигурации,
// общие для всех подпроектов/модулей.

plugins {
    // Используем alias из файла libs.versions.toml для версионирования
    // 'apply false' означает, что плагин здесь только объявляется, но не применяется
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
}
