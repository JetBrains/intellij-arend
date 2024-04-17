package org.arend.injection.actions

import org.arend.core.expr.Expression
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.*
import org.arend.extImpl.UncheckedExpressionImpl
import org.arend.term.prettyprint.TermWithSubtermDoc
import org.arend.typechecking.error.local.TypeMismatchWithSubexprError

fun Doc.withNormalizedTerms(cache: NormalizationCache, ppConfig: PrettyPrinterConfig, error: GeneralError): Doc {
    return if (error is TypeMismatchWithSubexprError) this else this.accept(DocMapper(ppConfig), cache::getNormalizedExpression)
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
        return DocFactory.termLine(params(doc.term as Expression), doc.prettifier, config)
    }

    override fun visitPattern(doc: PatternDoc, params: (Expression) -> Expression): Doc {
        return doc
    }

    override fun visitTerm(doc: TermDoc, params: (Expression) -> Expression): Doc {
        return if (doc is TermWithSubtermDoc) TermWithSubtermDoc(params(UncheckedExpressionImpl.extract(doc.term)), doc.subterm, doc.levels, doc.prettifier, config) else DocFactory.termDoc(params(UncheckedExpressionImpl.extract(doc.term)), doc.prettifier, config)
    }
}