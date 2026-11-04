package com.studysync

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
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

/** Walks ContextWrappers to find the host [ComponentActivity], if any. */
fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Makes [androidx.compose.ui.res.stringResource] follow the in-app language immediately.
 * Re-provides [LocalActivityResultRegistryOwner] so Google Sign-In / pickers keep working
 * after LocalContext is swapped to a configuration context.
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val base = LocalContext.current
    val resultOwner = LocalActivityResultRegistryOwner.current
    val localized = remember(language, base) { base.withAppLanguage(language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration
    ) {
        if (resultOwner != null) {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides resultOwner,
                content = content
            )
        } else {
            content()
        }
    }
}
