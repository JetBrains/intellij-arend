package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.resolving.ArendResolveCache
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.TCReferableWrapper
import org.arend.resolving.WrapperReferableConverter
import org.arend.term.concrete.Concrete

class ArendHighlightingPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend resolver annotator", textRange, highlightInfoProcessor) {

    override fun collectInfo(progress: ProgressIndicator) {
        val concreteProvider = PsiConcreteProvider(myProject, WrapperReferableConverter, this, null, false)
        file.concreteProvider = concreteProvider
        val resolverCache = ServiceManager.getService(myProject, ArendResolveCache::class.java)
        DefinitionResolveNameVisitor(concreteProvider, this, object : ResolverListener {
            private fun resolveReference(data: Any?, referent: Referable, originalRef: Referable?) {
                val reference = (data as? ArendLongName)?.refIdentifierList?.lastOrNull() ?: data as? ArendReferenceElement ?: return
                if (reference is ArendRefIdentifier && referent is GlobalReferable && referent.precedence.isInfix) {
                    holder.createInfoAnnotation(reference, null).textAttributes = ArendHighlightingColors.OPERATORS.textAttributesKey
                }
                if (data is ArendLongName) {
                    val nextToLast = reference.prevSibling
                    if (nextToLast != null) {
                        holder.createInfoAnnotation(TextRange(data.textRange.startOffset, nextToLast.textRange.endOffset), null).textAttributes = ArendHighlightingColors.LONG_NAME.textAttributesKey
                    }
                }
                if (referent != originalRef) {
                    resolverCache.resolveCached({ if (referent is ErrorReference) null else referent.underlyingReferable }, reference)
                }
            }

            override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression) {
                resolveReference(refExpr.data, refExpr.referent, originalRef)

                var arg = argument
                while (arg is Concrete.AppExpression) {
                    for (arg1 in arg.arguments) {
                        (arg1.expression as? Concrete.ReferenceExpression)?.let {
                            resolveReference(it.data, it.referent, null)
                        }
                    }
                    arg = arg.function
                }
                (arg as? Concrete.ReferenceExpression)?.let {
                    resolveReference(it.data, it.referent, null)
                }
            }

            override fun patternResolved(originalRef: Referable, pattern: Concrete.ConstructorPattern) {
                resolveReference(pattern.data, pattern.constructor, originalRef)
            }

            private fun highlightParameters(definition: Concrete.ReferableDefinition) {
                for (parameter in Concrete.getParameters(definition, true) ?: emptyList()) {
                    if (parameter.type.underlyingTypeClass != null) {
                        val list = when (val param = parameter.data) {
                            is ArendFieldTele -> param.fieldDefIdentifierList
                            is ArendNameTele -> param.identifierOrUnknownList
                            is ArendTypeTele -> param.typedExpr?.identifierOrUnknownList
                            is TCReferableWrapper -> (param.data as? ArendFieldDefIdentifier)?.let { listOf(it) }
                            else -> null
                        }
                        for (id in list ?: emptyList()) {
                            holder.createInfoAnnotation(id, null).textAttributes = ArendHighlightingColors.CLASS_PARAMETER.textAttributesKey
                        }
                    }
                }
            }

            override fun definitionResolved(definition: Concrete.Definition) {
                progress.checkCanceled()
                (definition.data.underlyingReferable as? PsiLocatedReferable)?.defIdentifier?.let {
                    holder.createInfoAnnotation(it, null).textAttributes = ArendHighlightingColors.DECLARATION.textAttributesKey
                }

                highlightParameters(definition)
                if (definition is Concrete.DataDefinition) {
                    for (constructorClause in definition.constructorClauses) {
                        for (constructor in constructorClause.constructors) {
                            highlightParameters(constructor)
                        }
                    }
                }

                advanceProgress(1)
            }
        }).resolveGroup(group, WrapperReferableConverter, group.scope)
    }
}