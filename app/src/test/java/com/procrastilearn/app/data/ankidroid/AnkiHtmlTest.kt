package com.procrastilearn.app.data.ankidroid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnkiHtmlTest {
    @Test
    fun `strips styles, tags and sound references`() {
        val html = "<style>.card{color:red}</style><div>IECA&nbsp;<b>inhiben</b> la ECA</div>[sound:a.mp3]"

        assertThat(ankiHtmlToText(html)).isEqualTo("IECA inhiben la ECA")
    }

    @Test
    fun `keeps line breaks and list items readable`() {
        val html = "Efectos:<br><ul><li>Tos seca</li><li>Angioedema</li></ul>"

        assertThat(ankiHtmlToText(html)).isEqualTo("Efectos:\n• Tos seca\n• Angioedema")
    }

    @Test
    fun `image-only content becomes blank`() {
        assertThat(ankiHtmlToText("<img src=\"oclusion.png\">")).isEmpty()
    }

    @Test
    fun `decodes ampersand last so entities are not double decoded`() {
        assertThat(ankiHtmlToText("A &amp;lt; B")).isEqualTo("A &lt; B")
    }
}
