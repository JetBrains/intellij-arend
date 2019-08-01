package org.arend.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElim
import org.arend.psi.ext.ArendCompositeElement
import org.arend.term.abs.Abstract
import org.arend.term.group.Group
import java.util.*

class ArendHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        /*
        if (element is ArendAtomPatternOrPrefix) {
            val parentPattern = element.parent as? ArendPattern ?: return
            val def = parentPattern.defIdentifier?.reference?.resolve() as? ArendConstructor ?: return
            checkPattern(element, def.typeTeleList, parentPattern.atomPatternOrPrefixList, holder)
            return
        }

        if (element is ArendPattern) {
            val clause = element.parent as? ArendClause
            if (clause != null) {
                val parent = clause.parent
                when (parent) {
                    is ArendFunctionClauses -> {
                        val body = parent.parent as? ArendFunctionBody
                        val func = body?.parent as? ArendDefFunction
                        if (func != null) {
                            checkPattern(element, body.elim, func.nameTeleList, clause.patternList, holder)
                        }
                    }
                    is ArendConstructor -> checkPattern(element, parent.elim, parent.typeTeleList, clause.patternList, holder)
                    is ArendCaseExpr -> checkPattern(element, parent.caseArgList.size, clause.patternList, holder)
                }
            } else {
                val conClause = element.parent as? ArendConstructorClause
                val dataBody = conClause?.parent as? ArendDataBody
                val typeTele = (dataBody?.parent as? ArendDefData)?.typeTeleList
                if (typeTele != null) {
                    checkPattern(element, dataBody.elim, typeTele, conClause.patternList, holder)
                }
            }
        }

        if (element is ArendPatternImplMixin) {
            val number = element.number
            if (number == Concrete.NumberPattern.MAX_VALUE || number == -Concrete.NumberPattern.MAX_VALUE) {
                element.getAtomPattern()?.let { holder.createErrorAnnotation(it, "Value too big") }
            }
            return
        }

        when {
            element is ArendDefIdentifier -> color = ArendHighlightingColors.DECLARATION
            element is ArendInfixArgument || element is ArendPostfixArgument -> color = ArendHighlightingColors.OPERATORS
            element is ArendRefIdentifier || element is LeafPsiElement && element.node.elementType == ArendElementTypes.DOT -> {
                val parent = element.parent as? ArendLongName
                if (parent != null) {
                    if (parent.parent is ArendStatCmd) return
                    val refList = parent.refIdentifierList
                    if (refList.isEmpty() || refList.last() != element) {
                        color = ArendHighlightingColors.LONG_NAME
                    }
                }
            }
        }

        val colorCopy = color
        if (colorCopy != null) {
            holder.createInfoAnnotation(element, null).textAttributes = colorCopy.textAttributesKey
        }

        if (element is ArendStatCmd) {
            var defined: MutableSet<String>? = null
            if (element.nsUsing?.nsIdList?.isEmpty() == false) {
                defined = (element.parentSourceNode as? Group)?.let { collectDefined(it) }
            }
            nameResolvingChecker.checkNamespaceCommand(element, defined)

            val nsCommands = (element.parentSourceNode as? Group)?.namespaceCommands ?: emptyList()
            if (nsCommands.size <= 1) {
                return
            }

            val scope = element.scope
            val commandRefs = HashMap<String,Referable>()
            for (ref in NamespaceCommandNamespace.resolveNamespace(if (element.kind == NamespaceCommand.Kind.IMPORT) scope.importedSubscope else scope, element).elements) {
                commandRefs[ref.textRepresentation()] = ref
            }
            if (commandRefs.isEmpty()) {
                return
            }

            for (cmd in nsCommands) {
                if (cmd == element) {
                    continue
                }
                for (other in NamespaceCommandNamespace.resolveNamespace(if (cmd.kind == NamespaceCommand.Kind.IMPORT) scope.importedSubscope else scope, cmd).elements) {
                    val otherName = other.textRepresentation()
                    val commandRef = commandRefs[otherName]
                    if (commandRef != null && commandRef != other) {
                        if (defined == null) {
                            defined = (element.parentSourceNode as? Group)?.let { collectDefined(it) }
                        }
                        if (defined == null || !defined.contains(otherName)) {
                            nameResolvingChecker.annotateNamespacesClash(element, cmd, otherName, Error.Level.WARNING)
                        }
                    }
                }
            }

            return
        }

        if (element is ArendLongName) {
            val parent = element.parent
            when (parent) {
                is ArendDefClass -> {
                    val superClass = element.refIdentifierList.lastOrNull()?.reference?.resolve()
                    if (superClass != null) {
                        if (superClass !is ArendDefClass) {
                            holder.createErrorAnnotation(element, "Expected a class")
                        }
                    }
                }
                is Abstract.ClassFieldImpl -> if ((parent is ArendCoClause && parent.getLbrace() != null || parent is ArendClassImplement && parent.getLbrace() != null) && parent.classReference == null) {
                    holder.createErrorAnnotation(element, "Expected either a class or a field which has a class as its type")
                }
            }
            return
        }

        if (element is LeafPsiElement) {
            when (element.node.elementType) {
                ArendElementTypes.USE_KW -> {
                    val parent = (element.parent as? ArendDefFunction)?.parentGroup
                    if (parent is ArendFile) {
                        holder.createErrorAnnotation(element as PsiElement, "\\use is not allowed on the top level")
                    } else if (parent !is ArendDefData && parent !is ArendDefClass) {
                        if (parent is ArendDefFunction) {
                            val coerceElement = element.findNextSibling()
                            if (coerceElement != null && coerceElement.node.elementType == ArendElementTypes.COERCE_KW) {
                                holder.createErrorAnnotation(TextRange(element.startOffset, coerceElement.textRange.endOffset), "\\use \\coerce is allowed only in \\where block of \\data or \\class")
                            }
                        } else {
                            holder.createErrorAnnotation(element as PsiElement, "\\use is allowed only in \\where block of \\data, \\class, or \\func")
                        }
                    }
                }
                ArendElementTypes.IMPORT_KW ->
                    if ((element.parent as? Abstract.NamespaceCommandHolder)?.parentSourceNode !is ArendFile) {
                        holder.createErrorAnnotation(element as PsiElement, "\\import is allowed only on the top level")
                    }
            }
            return
        }

        if (element is ArendExpr && element.topmostEquivalentSourceNode == element) {
            var expectedType = ExpectedTypeVisitor(element, holder).getExpectedType()
            if (expectedType is ExpectedTypeVisitor.Error) {
                expectedType.createErrorAnnotation(element, holder)
                expectedType = null
            }
            element.accept(TypecheckingVisitor(element, holder), expectedType)
        }
        */
    }

    private fun checkPattern(pattern: ArendCompositeElement, elim: ArendElim?, teleList: List<Abstract.Parameter>, patternList: List<Abstract.Pattern>, holder: AnnotationHolder) {
        if (elim == null || elim.withKw != null) {
            checkPattern(pattern, teleList, patternList, holder)
        } else {
            checkPattern(pattern, elim.refIdentifierList.size, patternList, holder)
        }
    }

    private fun checkPattern(element: ArendCompositeElement, teleList: List<Abstract.Parameter>, patternList: List<Abstract.Pattern>, holder: AnnotationHolder) {
        if (patternList.isEmpty()) {
            return
        }

        var i = 0
        var j = 0
        var refListSize = teleList.firstOrNull()?.referableList?.size ?: 0
        for (pattern in patternList) {
            val isExplicit = pattern.isExplicit
            if (isExplicit) {
                if (i < teleList.size && !teleList[i].isExplicit) {
                    i++
                    while (i < teleList.size && !teleList[i].isExplicit) {
                        i++
                    }
                    if (i < teleList.size) {
                        j = 0
                        refListSize = teleList[i].referableList.size
                    }
                }
            }

            while (j >= refListSize) {
                i++
                if (i >= teleList.size) {
                    break
                }
                j = 0
                refListSize = teleList[i].referableList.size
            }
            if (i >= teleList.size) {
                if (pattern == element) {
                    holder.createErrorAnnotation(TextRange(element.textRange.startOffset, (patternList.lastOrNull() as? PsiElement ?: element).textRange.endOffset), "Too many patterns. Expected " + teleList.sumBy { it.referableList.size } + " (including implicit)")
                }
                return
            }

            if (isExplicit == teleList[i].isExplicit) {
                j++
                while (j >= refListSize) {
                    i++
                    if (i >= teleList.size) {
                        break
                    }
                    j = 0
                    refListSize = teleList[i].referableList.size
                }
            } else {
                if (pattern == element) {
                    holder.createErrorAnnotation(element, "Expected an explicit pattern")
                    return
                }
            }
        }

        if (i < teleList.size && element == patternList[patternList.size - 1]) {
            while (i < teleList.size) {
                if (teleList[i].isExplicit) {
                    holder.createErrorAnnotation(TextRange((patternList.firstOrNull() as? PsiElement ?: element).textRange.startOffset, element.textRange.endOffset), "Not enough patterns. Expected " + teleList.sumBy { it.referableList.size })
                    return
                }
                i++
            }
        }
    }

    private fun checkPattern(element: ArendCompositeElement, numberOfPatterns: Int, patternList: List<Abstract.Pattern>, holder: AnnotationHolder) {
        if (numberOfPatterns == 0) {
            return
        }
        for (i in 0 until patternList.size) {
            if (element == patternList[i]) {
                if (!patternList[i].isExplicit) {
                    holder.createErrorAnnotation(element, "Expected an explicit pattern")
                }
                if (i == numberOfPatterns) {
                    holder.createErrorAnnotation(TextRange(element.textRange.startOffset, (patternList.lastOrNull() as? PsiElement ?: element).textRange.endOffset), "Too many patterns. Expected $numberOfPatterns")
                } else if (i == patternList.size - 1 && numberOfPatterns > patternList.size) {
                    holder.createErrorAnnotation(TextRange((patternList.firstOrNull() as? PsiElement ?: element).textRange.startOffset, element.textRange.endOffset), "Not enough patterns. Expected $numberOfPatterns")
                }
                return
            }
        }
    }

    private fun collectDefined(group: Group): HashSet<String> {
        val result = HashSet<String>()
        for (subgroup in group.subgroups) {
            result.add(subgroup.referable.textRepresentation())
            for (ref in subgroup.internalReferables) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
        }
        for (subgroup in group.dynamicSubgroups) {
            result.add(subgroup.referable.textRepresentation())
            for (ref in subgroup.internalReferables) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
        }
        for (ref in group.internalReferables) {
            result.add(ref.referable.textRepresentation())
        }
        return result
    }

    companion object {
    }
}
