package org.arend.injection

import com.intellij.openapi.util.TextRange
import org.arend.error.doc.*


class CollectingDocStringBuilder(private val builder: StringBuilder) : DocStringBuilder(builder) {
    val textRanges = ArrayList<List<TextRange>>()
    private var last: ArrayList<TextRange>? = null

    override fun visitReference(doc: ReferenceDoc, newLine: Boolean): Void? {
        val start = builder.length
        super.visitReference(doc, newLine)
        textRanges.add(listOf(TextRange(start, builder.length)))
        last = null
        return null
    }

    override fun visitTermLine(doc: TermLineDoc, newLine: Boolean): Void? {
        val start = builder.length
        super.visitTermLine(doc, newLine)
        textRanges.add(listOf(TextRange(start, builder.length)))
        last = null
        return null
    }

    override fun visitText(doc: TextDoc, newLine: Boolean): Void? {
        if (doc !is TermTextDoc) {
            return super.visitText(doc, newLine)
        }

        if (doc.isFirst || last == null) {
            last = ArrayList()
            textRanges.add(last!!)
        }

        val start = builder.length
        super.visitText(doc, newLine)
        last!!.add(TextRange(start, builder.length))

        return null
    }
}