package com.studysync

/**
 * In-app languages. Tags match Android resource qualifiers (`values-af`, `values-nso`, …).
 */
enum class AppLanguage(
    val tag: String,
    val nativeName: String
) {
    ENGLISH("en", "English"),
    AFRIKAANS("af", "Afrikaans"),
    SEPEDI("nso", "Sepedi"),
    ISIZULU("zu", "isiZulu"),
    TSONGA("ts", "Xitsonga");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag?.substringBefore('-'), ignoreCase = true) }
                ?: ENGLISH
    }
}
