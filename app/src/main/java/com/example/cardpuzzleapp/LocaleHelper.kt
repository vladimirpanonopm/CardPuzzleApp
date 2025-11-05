package com.example.cardpuzzleapp

import android.content.Context
import android.content.ContextWrapper
import android.os.LocaleList
import java.util.Locale

class LocaleHelper(base: Context) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context, language: String): ContextWrapper {
            val config = context.resources.configuration
            val locale = Locale(language)
            Locale.setDefault(locale)

            val localeList = LocaleList(locale)
            config.setLocales(localeList)

            val updatedContext = context.createConfigurationContext(config)
            return ContextWrapper(updatedContext)
        }
    }
}