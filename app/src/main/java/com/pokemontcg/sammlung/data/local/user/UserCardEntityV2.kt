package com.pokemontcg.sammlung.data.local.user

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardCondition {
    MINT,
    NEAR_MINT,
    EXCELLENT,
    GOOD,
    LIGHT_PLAYED,
    PLAYED,
    POOR
}

enum class CardCategory {
    NONE,
    COMMON,
    FULLART
}

/**
 * Grading information for a professionally graded copy (PSA/CGC/BGS slab).
 * All fields are null for raw, ungraded copies - the default case.
 */
data class CardGrading(
    val gradingCompany: String? = null,
    val grade: String? = null,
    val certNumber: String? = null
)

/** Grading companies with dedicated UI labels. */
enum class GradingCompany {
    PSA,
    CGC,
    BGS
}

/** Maximum accepted length of a grading certificate number. */
const val MAX_GRADE_LENGTH = 6
const val MAX_CERT_NUMBER_LENGTH = 24

private val validGradingCompanyNames = GradingCompany.entries.map { it.name }.toSet()

/**
 * Grades as printed on the slab label: whole numbers 1-10, PSA only whole grades,
 * CGC/BGS additionally half grades in .5 steps (9.5, 8.5, ...).
 */
private val GRADE_PATTERN = Regex("^((10)|([1-9](\\.[05])?))$")

/**
 * Single source of truth for grading normalization, shared by the edit-dialog sanitizer
 * and the JSON/CSV import: the company must be a known grader (otherwise the whole
 * grading is dropped - a grade without a grader is meaningless), the grade must match
 * the slab notation (German comma is normalized to a dot, anything else becomes null),
 * the certificate number is trimmed and length-capped.
 */
fun normalizeGrading(grading: CardGrading): CardGrading {
    val company = grading.gradingCompany
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it in validGradingCompanyNames }
    if (company == null) return CardGrading()
    val grade = grading.grade
        ?.trim()
        ?.replace(',', '.')
        ?.takeIf { it.length <= MAX_GRADE_LENGTH && it.matches(GRADE_PATTERN) }
    val cert = grading.certNumber
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_CERT_NUMBER_LENGTH)
    return CardGrading(gradingCompany = company, grade = grade, certNumber = cert)
}

@Entity(
    tableName = "user_cards",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["pokemonDexId"])
    ]
)
data class UserCardEntityV2(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val pokemonId: String,
    val pokemonDexId: Int,
    val language: String = "de",
    val quantity: Int = 1,
    val condition: String? = null,
    val notes: String? = null,
    val category: String = CardCategory.NONE.name,
    val isPrimary: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val gradingCompany: String? = null,
    val grade: String? = null,
    val certNumber: String? = null
)
