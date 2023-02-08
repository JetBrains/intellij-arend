package org.arend.injection.actions

import org.arend.core.expr.Expression
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.*
import org.arend.extImpl.UncheckedExpressionImpl

fun Doc.withNormalizedTerms(cache: NormalizationCache, ppConfig: PrettyPrinterConfig): Doc {
    return this.accept(DocMapper(ppConfig), cache::getNormalizedExpression)
}

private class DocMapper(val config: PrettyPrinterConfig) : DocVisitor<(Expression) -> Expression, Doc> {
    override fun visitVList(doc: VListDoc, params: (Expression) -> Expression): Doc {
        return DocFactory.vList(doc.docs.map { it.accept(this, params) })
    }

    override fun visitHList(doc: HListDoc, params: (Expression) -> Expression): Doc {
        return DocFactory.hList(doc.docs.map { it.accept(this, params) as LineDoc })
    }

    override fun visitText(doc: TextDoc, params: (Expression) -> Expression): Doc {
        return doc
    }

    override fun visitHang(doc: HangDoc, params: (Expression) -> Expression): Doc {
        return DocFactory.hang(doc.top.accept(this, params), doc.bottom.accept(this, params))
    }

    override fun visitReference(doc: ReferenceDoc, params: (Expression) -> Expression): Doc {
        return doc
    }

    override fun visitCaching(doc: CachingDoc, params: (Expression) -> Expression): Doc {
        return doc
    }

    override fun visitTermLine(doc: TermLineDoc, params: (Expression) -> Expression): Doc {
        return DocFactory.termLine(params(doc.term as Expression), config)
    }

    override fun visitPattern(doc: PatternDoc, params: (Expression) -> Expression): Doc {
        return doc
    }

    override fun visitTerm(doc: TermDoc, params: (Expression) -> Expression): Doc {
        return DocFactory.termDoc(params(UncheckedExpressionImpl.extract(doc.term)), config)
    }
}