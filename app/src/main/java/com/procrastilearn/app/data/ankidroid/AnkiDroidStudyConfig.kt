package com.procrastilearn.app.data.ankidroid

object AnkiDroidStudyConfig {
    val excludedDecks = listOf("Musica que me gusta")
    val excludedNoteTypes = listOf("Image Occlusion Enhanced", "Oclusión de imagen")
    const val NEW_CANDIDATES_TO_RENDER = 15
    const val REVIEW_CANDIDATES_TO_RENDER = 15

    fun searchFilter(): String =
        buildList {
            excludedDecks.forEach { add("-\"deck:$it\"") }
            excludedNoteTypes.forEach { add("-\"note:$it\"") }
            add("-is:suspended")
            add("-is:buried")
        }.joinToString(" ")
}
