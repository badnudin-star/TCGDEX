package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.CardCondition
import com.pokemontcg.sammlung.data.local.user.CardGrading
import com.pokemontcg.sammlung.data.local.user.GradingCompany
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

internal const val MIN_CARD_QUANTITY = 1
internal const val MAX_CARD_QUANTITY = 999
internal const val MAX_CARD_NOTES_LENGTH = 500
internal const val MAX_GRADE_LENGTH = 6
internal const val MAX_CERT_NUMBER_LENGTH = 24

internal val SUPPORTED_CARD_LANGUAGES = setOf("de", "en", "ja", "fr", "es", "it", "ko")

private val validConditions = CardCondition.entries.map { it.name }.toSet()
private val validCategories = CardCategory.entries.map { it.name }.toSet()
private val validGradingCompanies = GradingCompany.entries.map { it.name }.toSet()

/**
 * Returns a normalized copy of [userCard] without touching persistence.
 * Invalid optional values fall back to null or to the value already stored on [userCard].
 */
internal fun sanitizeUserCardDetails(
    userCard: UserCardEntityV2,
    quantity: Int,
    condition: String?,
    language: String,
    notes: String?,
    category: String,
    grading: CardGrading = CardGrading()
): UserCardEntityV2 {
    val sanitizedCondition = condition?.takeIf { it in validConditions }
    val sanitizedLanguage = language.takeIf { it in SUPPORTED_CARD_LANGUAGES } ?: userCard.language
    val sanitizedCategory = category.takeIf { it in validCategories } ?: userCard.category
    val sanitizedNotes = notes
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_CARD_NOTES_LENGTH)
    val sanitizedGrading = sanitizeGrading(grading)

    return userCard.copy(
        quantity = quantity.coerceIn(MIN_CARD_QUANTITY, MAX_CARD_QUANTITY),
        condition = sanitizedCondition,
        language = sanitizedLanguage,
        notes = sanitizedNotes,
        category = sanitizedCategory,
        gradingCompany = sanitizedGrading.gradingCompany,
        grade = sanitizedGrading.grade,
        certNumber = sanitizedGrading.certNumber
    )
}

/**
 * Normalizes grading input: the company must be a known grader, grade and certificate
 * number are trimmed and length-capped. A grading without company is dropped entirely -
 * a grade without a grader is meaningless.
 */
internal fun sanitizeGrading(grading: CardGrading): CardGrading {
    val company = grading.gradingCompany
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it in validGradingCompanies }
    if (company == null) return CardGrading()
    val grade = grading.grade
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_GRADE_LENGTH)
    val cert = grading.certNumber
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_CERT_NUMBER_LENGTH)
    return CardGrading(gradingCompany = company, grade = grade, certNumber = cert)
}
