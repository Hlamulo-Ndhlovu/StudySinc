package com.studysync

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Builds a [Context] whose string resources resolve for [language]. */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.tag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}

/**
 * Makes [stringResource] and other Compose resource readers follow the in-app language
 * immediately, even before the activity is recreated by AppCompat.
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val base = LocalContext.current
    val localized = remember(language, base) { base.withAppLanguage(language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content
    )
}
