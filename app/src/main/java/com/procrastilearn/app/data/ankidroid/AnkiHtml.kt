package com.procrastilearn.app.data.ankidroid

private val styleOrScript = Regex("(?is)<style.*?</style>|<script.*?</script>")
private val soundTag = Regex("\\[sound:[^\\]]*]")
private val lineBreakTags = Regex("(?i)<br\\s*/?>|</div>|</p>|</li>|</tr>|<hr[^>]*>")
private val listItem = Regex("(?i)<li[^>]*>")
private val anyTag = Regex("<[^>]+>")
private val manyBlankLines = Regex("\\n{3,}")
private val htmlEntities =
    mapOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'")

fun ankiHtmlToText(html: String?): String {
    var text = (html ?: "").replace(styleOrScript, "").replace(soundTag, "")
    text = text.replace(lineBreakTags, "\n").replace(listItem, "• ").replace(anyTag, "")
    htmlEntities.forEach { (entity, value) -> text = text.replace(entity, value) }
    text = text.replace("&amp;", "&")
    return text.lines().joinToString("\n") { it.trim() }.replace(manyBlankLines, "\n\n").trim()
}
