package com.example.cardpuzzleapp

/**
 * Regex для поиска всех символов Unicode,
 * которые являются огласовками (никуд).
 */
private val NIKUD_REGEX = Regex("[\u0590-\u05C7]")

/**
 * Regex для определения, содержит ли строка ХОТЯ БЫ ОДНУ букву иврита.
 */
private val HEBREW_CHAR_REGEX = Regex(".*[\u0590-\u05FF].*")

/**
 * Расширение для String, которое удаляет все огласовки (никуд).
 * "מָה?" станет "מה?"
 */
fun String.stripNikud(): String {
    return NIKUD_REGEX.replace(this, "")
}

/**
 * Проверяет, является ли строка (запрос) ивритской.
 */
fun String.isHebrew(): Boolean {
    return this.matches(HEBREW_CHAR_REGEX)
}