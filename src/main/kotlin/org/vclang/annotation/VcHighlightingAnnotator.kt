package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.naming.error.NotInScopeError
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.resolving.NameResolvingChecker
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalError
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.*
import org.vclang.psi.ext.impl.InstanceAdapter
import org.vclang.quickfix.InstanceQuickFix
import org.vclang.resolving.PsiPartialConcreteProvider

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        var color: VcHighlightingColors? = null
        if (element is VcReferenceElement) {
            val resolved = VclangImportHintAction.getResolved(element)
            if (resolved == null) {
                val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                annotation.highlightType = ProblemHighlightType.ERROR

                val fix = VclangImportHintAction(element)
                if (fix.isAvailable(element.project, null, element.containingFile))
                    annotation.registerFix(fix)

                return
            } else if (resolved is PsiDirectory) {
                val refList = (element.parent as? VcLongName)?.refIdentifierList
                if (refList == null || refList.indexOf(element) == refList.size - 1) {
                    holder.createErrorAnnotation(element, "Unexpected reference to a directory")
                }
            } else if (resolved is VcFile) {
                val longName = element.parent as? VcLongName
                if (longName == null || longName.parent !is VcStatCmd) {
                    val refList = longName?.refIdentifierList
                    if (refList == null || refList.indexOf(element) == refList.size - 1) {
                        holder.createErrorAnnotation(element, "Unexpected reference to a file")
                    }
                }
            } else if (resolved is GlobalReferable) {
                if (resolved.precedence.isInfix) {
                    color = VcHighlightingColors.OPERATORS
                }
            }
        }

        if (element is VcDefIdentifier) {
            val parent = element.parent
            if (parent is InstanceAdapter) {
                InstanceQuickFix.annotateClassInstance(parent, holder)
            }
        }
        if (element is VcNewExprImplMixin) InstanceQuickFix.annotateNewExpr(element, holder)

        if (element is Group) {
            object : NameResolvingChecker(true, element is VcFile, PsiPartialConcreteProvider) {
                override fun onDefinitionNamesClash(ref1: LocatedReferable, ref2: LocatedReferable, level: Error.Level) {
                    annotateDefinitionNamesClash(ref1, level)
                    annotateDefinitionNamesClash(ref2, level)
                }

                private fun annotateDefinitionNamesClash(ref: LocatedReferable, level: Error.Level) {
                    val psiRef = referableToPsi(ref) ?: return
                    holder.createAnnotation(levelToSeverity(level), psiRef.textRange, "Duplicate definition name '${ref.textRepresentation()}'")
                }

                override fun onFieldNamesClash(ref1: LocatedReferable, superClass1: ClassReferable, ref2: LocatedReferable, superClass2: ClassReferable, currentClass: ClassReferable, level: Error.Level) {
                    if (superClass2 == currentClass) {
                        val psiRef = referableToPsi(ref2) ?: return
                        holder.createAnnotation(levelToSeverity(level), psiRef.textRange, "Field '${ref2.textRepresentation()}' is already defined in super class ${superClass1.textRepresentation()}")
                    } else {
                        val psiRef = referableToPsi(currentClass) ?: return
                        holder.createAnnotation(levelToSeverity(level), psiRef.textRange, "Field '${ref2.textRepresentation()}' is defined in super classes ${superClass1.textRepresentation()} and ${superClass2.textRepresentation()}")
                    }
                }

                private fun referableToPsi(ref: LocatedReferable): PsiElement? =
                    (ref as? PsiLocatedReferable)?.children?.firstOrNull { it is VcDefIdentifier } ?: (ref as? PsiElement)

                override fun onNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                    annotateNamespacesClash(cmd1, cmd2, name, level)
                    annotateNamespacesClash(cmd2, cmd1, name, level)
                }

                private fun annotateNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                    if (cmd1 is PsiElement) {
                        holder.createAnnotation(levelToSeverity(level), cmd1.textRange, "Definition '$name' is imported from ${LongName(cmd2.path)}")
                    }
                }

                override fun onError(error: LocalError) {
                    if (error is NotInScopeError) {
                        return
                    }

                    val cause = error.cause
                    if (cause is PsiElement) {
                        holder.createAnnotation(levelToSeverity(error.level), cause.textRange, error.shortMessage)
                    }
                }

                override fun checkDefinition(definition: LocatedReferable?, scope: Scope?) {
                    if (definition is VcDefClass && definition.fatArrow != null) {
                        val fieldTele = definition.fieldTeleList.firstOrNull()
                        if (fieldTele != null) {
                            holder.createAnnotation(HighlightSeverity.ERROR, TextRange(fieldTele.textRange.startOffset, (definition.fieldTeleList.lastOrNull() ?: fieldTele).textRange.endOffset), "Class synonyms cannot have parameters")
                        }
                    }
                    super.checkDefinition(definition, scope)
                }
            }.checkGroup(element, (element as? VcCompositeElement)?.scope)
            return
        }

        if (element is VcNewExpr && element.newKw != null || element is VcNewArg) {
            val argumentAppExpr = (element as? VcNewExpr)?.argumentAppExpr ?: (element as? VcNewArg)?.argumentAppExpr
            if (argumentAppExpr != null) {
                val longName = argumentAppExpr.longNameExpr?.longName ?: run {
                    val atomFieldsAcc = argumentAppExpr.atomFieldsAcc
                    if (atomFieldsAcc != null && atomFieldsAcc.fieldAccList.isEmpty()) {
                        atomFieldsAcc.atom.literal?.longName
                    } else {
                        null
                    }
                }
                if (longName != null) {
                    val ref = longName.referent
                    val resolved = (ref as? UnresolvedReference)?.resolve(argumentAppExpr.scope) ?: ref
                    if (resolved !is VcDefClass && resolved !is UnresolvedReference && resolved !is ErrorReference) {
                        holder.createErrorAnnotation(longName, "Expected a class")
                    }
                }
            }
            return
        }

        if (element is VcPatternImplMixin) {
            val number = element.number
            if (number == Concrete.NumberPattern.MAX_VALUE) {
                element.getAtomPattern()?.let { holder.createErrorAnnotation(it, "Value too big") }
            }
            return
        }

        when {
            element is VcDefIdentifier -> color = VcHighlightingColors.DECLARATION
            element is VcInfixArgument || element is VcPostfixArgument -> color = VcHighlightingColors.OPERATORS
            element is VcRefIdentifier || element is LeafPsiElement && element.node.elementType == VcElementTypes.DOT -> {
                val parent = element.parent as? VcLongName
                if (parent != null) {
                    if (parent.parent is VcStatCmd) return
                    val refList = parent.refIdentifierList
                    if (refList.isEmpty() || refList.last() != element) {
                        color = VcHighlightingColors.LONG_NAME
                    }
                }
            }
        }

        if (color != null) {
            holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
        }
    }

    private fun levelToSeverity(level: Error.Level) = when (level) {
        Error.Level.ERROR -> HighlightSeverity.ERROR
        Error.Level.WARNING -> HighlightSeverity.WARNING
        Error.Level.GOAL -> HighlightSeverity.WEAK_WARNING
        Error.Level.INFO -> HighlightSeverity.INFORMATION
    }
}
