package org.arend.documentation

import org.arend.ArendTestBase
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.ext.PsiReferable
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup

class ArendDocumentationCommentTest : ArendTestBase() {

    private fun getArendComment(@Language("Arend") code: String): ArendDocComment? {
        val psiFile = InlineFile(code).psiFile
        val comments = psiFile.children.filterIsInstance<ArendDocComment>()
        return comments.getOrNull(0)
    }

    private fun getHtml(@Language("Arend") code: String, withLatexCode: Boolean): String {
        val comment = getArendComment(code)
        assertNotNull(comment)

        val ref = comment!!.owner as? PsiReferable?
        val doc = ref?.documentation
        assertNotNull(ref)
        assertNotNull(doc)

        val hasLatexCode = hasLatexCode(doc!!)
        assertEquals(withLatexCode, hasLatexCode)

        val docCommentInfo = ArendDocCommentInfo(hasLatexCode = hasLatexCode, wasPrevRow = false, suggestedFont = ArendDocumentationProvider.DEFAULT_FONT)
        val stringBuilder = StringBuilder()
        stringBuilder.generateDocComments(ref, doc, true, docCommentInfo)

        return stringBuilder.toString()
    }

    fun testSimpleComment() {
        val code = "{- |\n" +
                " - a" +
                " -}" +
                "\\data Empty"

        val html = getHtml(code, false)
        val document = Jsoup.parse(html)

        val rows = document.getElementsByClass("row")
        assertEquals(1, rows.size)

        assertEquals("a", rows[0].text())
        assertEquals(0, rows[0].select("img").size)
    }

    fun testCommentWithLatex() {
        val code = "{- |\n" +
                " -   1. a \$\\sqrt{9}\$\n" +
                " -   2. b\n" +
                " -              * bb\n" +
                " -}" +
                "\\data Empty"

        val html = getHtml(code, true)
        val document = Jsoup.parse(html)

        val rows = document.getElementsByClass("row")
        assertEquals(4, rows.size)
        assertTrue(document.getElementsByTag("ol").text().contains("1. a 2. b • bb"))
        assertEquals(2, rows.flatMap { it.select("img") }.size)
    }
}
