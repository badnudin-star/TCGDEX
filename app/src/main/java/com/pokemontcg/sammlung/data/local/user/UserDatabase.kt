package com.pokemontcg.sammlung.data.local.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserCardEntityV2::class,
        PriceHistoryEntity::class,
        WishlistEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userCardDao(): UserCardDaoV2
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun wishlistDao(): WishlistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS price_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "cardId TEXT NOT NULL, " +
                        "marketPrice REAL NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_cardId ON price_history(cardId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_timestamp ON price_history(timestamp)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wishlist (" +
                        "cardId TEXT NOT NULL PRIMARY KEY, " +
                        "targetPrice REAL, " +
                        "notes TEXT, " +
                        "addedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wishlist_cardId ON wishlist(cardId)")
            }
        }

        /** The production migration chain, shared with instrumentation tests. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "user_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
