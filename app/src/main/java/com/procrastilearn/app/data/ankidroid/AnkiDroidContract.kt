package com.procrastilearn.app.data.ankidroid

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

object AnkiDroidContract {
    const val PACKAGE_NAME = "com.ichi2.anki"
    const val READ_WRITE_PERMISSION = "$PACKAGE_NAME.permission.READ_WRITE_DATABASE"
    private const val AUTHORITY = "$PACKAGE_NAME.flashcards"
    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    val CARDS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "cards")
    val SCHEDULE_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
    val DECKS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")

    fun cardUri(cardId: Long): Uri = Uri.withAppendedPath(CARDS_URI, cardId.toString())

    object Card {
        const val ID = "_id"
        const val NOTE_ID = "note_id"
        const val ORD = "ord"
        const val DECK_ID = "deck_id"
        const val TYPE = "type"
        const val QUESTION_SIMPLE = "question_simple"
        const val ANSWER_PURE = "answer_pure"
        const val TYPE_NEW = 0
    }

    object Schedule {
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val EASE = "answer_ease"
        const val TIME_TAKEN = "time_taken"
    }

    object Deck {
        const val ID = "deck_id"
        const val NAME = "deck_name"
    }

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE_NAME, 0) }.isSuccess

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
}
