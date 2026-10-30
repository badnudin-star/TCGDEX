package com.pokemontcg.sammlung.data.local.converters

import androidx.room.TypeConverter
import com.pokemontcg.sammlung.data.local.user.CardCategory

class CardCategoryConverter {

    @TypeConverter
    fun fromCardCategory(category: CardCategory?): String {
        return category?.name ?: CardCategory.NONE.name
    }

    @TypeConverter
    fun toCardCategory(value: String?): CardCategory {
        return try {
            if (value.isNullOrBlank()) CardCategory.NONE
            else CardCategory.valueOf(value)
        } catch (_: Exception) {
            CardCategory.NONE
        }
    }
}
