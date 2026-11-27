package com.procrastilearn.app.data.ankidroid

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.SystemClock
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.data.repository.todayStamp
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.UndoResult
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class AnkiDroidUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private data class AnkiCard(
    val id: Long,
    val noteId: Long,
    val ord: Int,
    val isNew: Boolean,
    val question: String,
    val answer: String,
)

private data class ShownCard(
    val card: AnkiCard,
    val shownAtElapsedMs: Long,
)

private val FULL_COLUMNS =
    arrayOf(
        AnkiDroidContract.Card.ID,
        AnkiDroidContract.Card.NOTE_ID,
        AnkiDroidContract.Card.ORD,
        AnkiDroidContract.Card.TYPE,
        AnkiDroidContract.Card.QUESTION_SIMPLE,
        AnkiDroidContract.Card.ANSWER_PURE,
    )

@Singleton
class AnkiDroidStudyRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val dayCounters: DayCountersStore,
    ) : VocabularyStudyRepository {
        private val shownCards = ConcurrentHashMap<Long, ShownCard>()

        override suspend fun getNextVocabularyItem(): VocabularyItem =
            withContext(Dispatchers.IO) {
                ensureAccess()
                ensureDay()
                val card = nextDueCard() ?: nextNewCardIfAllowed() ?: throw NoAvailableItemsException()
                shownCards[card.id] = ShownCard(card, SystemClock.elapsedRealtime())
                VocabularyItem(
                    id = card.id,
                    word = card.question,
                    translation = card.answer,
                    isNew = card.isNew,
                )
            }

        override suspend fun hasAvailableItems(): Boolean =
            withContext(Dispatchers.IO) {
                if (!AnkiDroidContract.isInstalled(context) || !AnkiDroidContract.hasPermission(context)) {
                    return@withContext false
                }
                ensureDay()
                runCatching {
                    queryIds("is:due").isNotEmpty() || (newRemaining() > 0 && queryIds("is:new").isNotEmpty())
                }.getOrDefault(false)
            }

        override suspend fun reviewVocabularyItem(
            id: Long,
            rating: Rating,
            direction: StudyDirection,
        ) = withContext(Dispatchers.IO) {
            ensureAccess()
            val shown = shownCards.remove(id)
            val card =
                shown?.card ?: queryCard(id) ?: throw IllegalArgumentException("Card $id not found in AnkiDroid")
            val timeTakenMs = shown?.let { SystemClock.elapsedRealtime() - it.shownAtElapsedMs } ?: 0L
            val values =
                ContentValues().apply {
                    put(AnkiDroidContract.Schedule.NOTE_ID, card.noteId)
                    put(AnkiDroidContract.Schedule.CARD_ORD, card.ord)
                    put(AnkiDroidContract.Schedule.EASE, rating.toAnkiEase())
                    put(AnkiDroidContract.Schedule.TIME_TAKEN, timeTakenMs)
                }
            val updated = context.contentResolver.update(AnkiDroidContract.SCHEDULE_URI, values, null, null)
            check(updated > 0) { "AnkiDroid did not record the answer for card $id" }
            if (card.isNew) dayCounters.markNewShown() else dayCounters.markReviewShown()
        }

        override suspend fun undoLastRating(): UndoResult? = null

        override fun observeUndoCount(): Flow<Int> = flowOf(0)

        override fun observeBackwardOnlySkippedCount(): Flow<Int> = flowOf(0)

        private fun nextDueCard(): AnkiCard? =
            firstReadable(queryIds("is:due is:learn").sorted())
                ?: firstReadable(
                    queryIds("is:due -is:learn").sorted().take(AnkiDroidStudyConfig.REVIEW_CANDIDATES_TO_RENDER),
                )

        private suspend fun nextNewCardIfAllowed(): AnkiCard? {
            if (newRemaining() <= 0) return null
            return firstReadable(queryIds("is:new").sorted().take(AnkiDroidStudyConfig.NEW_CANDIDATES_TO_RENDER))
        }

        private fun firstReadable(ids: List<Long>): AnkiCard? =
            ids.asSequence().mapNotNull(::queryCard).firstOrNull { it.question.isNotBlank() }

        private suspend fun newRemaining(): Int {
            val policy = dayCounters.readPolicy().first()
            val counters = dayCounters.read().first()
            return (policy.newPerDay + counters.extraNewToday - counters.newShown).coerceAtLeast(0)
        }

        private suspend fun ensureDay() {
            val today = todayStamp()
            if (dayCounters.read().first().yyyymmdd != today) dayCounters.resetFor(today)
        }

        private fun ensureAccess() {
            if (!AnkiDroidContract.isInstalled(context)) {
                throw AnkiDroidUnavailableException("AnkiDroid is not installed")
            }
            if (!AnkiDroidContract.hasPermission(context)) {
                throw AnkiDroidUnavailableException("Permission to access AnkiDroid was not granted")
            }
        }

        private fun queryIds(search: String): List<Long> =
            query(
                uri = AnkiDroidContract.CARDS_URI,
                columns = arrayOf(AnkiDroidContract.Card.ID),
                selection = "$search ${AnkiDroidStudyConfig.searchFilter()}",
            ) {
                buildList {
                    val idIndex = it.getColumnIndexOrThrow(AnkiDroidContract.Card.ID)
                    while (it.moveToNext()) add(it.getLong(idIndex))
                }
            }

        private fun queryCard(cardId: Long): AnkiCard? =
            runCatching {
                query(AnkiDroidContract.cardUri(cardId), FULL_COLUMNS, null) { cursor ->
                    if (cursor.moveToFirst()) cursor.toAnkiCard() else null
                }
            }.getOrNull()

        private fun <T> query(
            uri: android.net.Uri,
            columns: Array<String>,
            selection: String?,
            read: (Cursor) -> T,
        ): T {
            val cursor =
                try {
                    context.contentResolver.query(uri, columns, selection, null, null)
                } catch (exception: SecurityException) {
                    throw AnkiDroidUnavailableException("AnkiDroid refused access", exception)
                } catch (exception: IllegalStateException) {
                    throw AnkiDroidUnavailableException("AnkiDroid collection is not available", exception)
                } ?: throw AnkiDroidUnavailableException("AnkiDroid returned no data for $uri")
            return cursor.use(read)
        }

        private fun Cursor.toAnkiCard(): AnkiCard =
            AnkiCard(
                id = getLong(getColumnIndexOrThrow(AnkiDroidContract.Card.ID)),
                noteId = getLong(getColumnIndexOrThrow(AnkiDroidContract.Card.NOTE_ID)),
                ord = getInt(getColumnIndexOrThrow(AnkiDroidContract.Card.ORD)),
                isNew = getInt(getColumnIndexOrThrow(AnkiDroidContract.Card.TYPE)) == AnkiDroidContract.Card.TYPE_NEW,
                question = ankiHtmlToText(getString(getColumnIndexOrThrow(AnkiDroidContract.Card.QUESTION_SIMPLE))),
                answer = ankiHtmlToText(getString(getColumnIndexOrThrow(AnkiDroidContract.Card.ANSWER_PURE))),
            )
    }

private fun Rating.toAnkiEase(): Int =
    when (this) {
        Rating.AGAIN -> 1
        Rating.HARD -> 2
        Rating.GOOD -> 3
        Rating.EASY -> 4
    }
