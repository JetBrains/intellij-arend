package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.NameResolvingChecker
import com.jetbrains.jetpad.vclang.term.NameRenaming
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcReferenceElement

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is VcReferenceElement) {
            if (VclangImportHintAction.referenceUnresolved(element)) {
                val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                annotation.highlightType = ProblemHighlightType.ERROR

                val fix = VclangImportHintAction(element)
                if (fix.isAvailable(element.project, null, element.containingFile))
                    annotation.registerFix(fix)
            }
        }

        if (element is Group) {
            object : NameResolvingChecker(true) {
                override fun definitionNamesClash(ref1: LocatedReferable, ref2: LocatedReferable, level: Error.Level) {
                    annotateDefinitionNamesClash(ref1, level)
                    annotateDefinitionNamesClash(ref2, level)
                }

                private fun annotateDefinitionNamesClash(ref: LocatedReferable, level: Error.Level) {
                    val psiRef = referableToPsi(ref) ?: return
                    holder.createAnnotation(levelToSeverity(level), psiRef.textRange, "Duplicate definition name '${ref.textRepresentation()}'")
                }

                override fun fieldNamesClash(ref1: LocatedReferable, superClass1: ClassReferable, ref2: LocatedReferable, superClass2: ClassReferable, currentClass: ClassReferable, level: Error.Level) {
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

                override fun namespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                    annotateNamespacesClash(cmd1, cmd2, name, level)
                    annotateNamespacesClash(cmd2, cmd1, name, level)
                }

                private fun annotateNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                    if (cmd1 is PsiElement) {
                        holder.createAnnotation(levelToSeverity(level), cmd1.textRange, "Definition '$name' is imported from ${LongName(cmd2.path)}")
                    }
                }

                override fun namespaceDefinitionNameClash(renaming: NameRenaming, ref: LocatedReferable, level: Error.Level) {
                    if (renaming is PsiElement) {
                        holder.createAnnotation(levelToSeverity(level), renaming.textRange, "Definition '${ref.textRepresentation()}' is not imported since it is defined in this module")
                    }
                }

                override fun nonTopLevelImport(command: NamespaceCommand?) {
                    if (command is PsiElement) {
                        holder.createErrorAnnotation(command.textRange, "\\import is allowed only on the top level")
                    }
                }
            }.checkGroup(element, (element as? VcCompositeElement)?.scope, element is VcFile)
            return
        }

        val color = when {
            element is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            element is VcInfixArgument || element is VcPostfixArgument -> VcHighlightingColors.OPERATORS
            element is VcRefIdentifier || element is LeafPsiElement && element.node.elementType == VcElementTypes.DOT -> {
                val parent = element.parent as? VcLongName ?: return
                if (parent.parent is VcStatCmd) return
                val refList = parent.refIdentifierList
                if (!refList.isEmpty() && refList.last() == element) return
                VcHighlightingColors.LONG_NAME
            }
            else -> return
        }

        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }

    private fun levelToSeverity(level: Error.Level) = when (level) {
        Error.Level.ERROR -> HighlightSeverity.ERROR
        Error.Level.WARNING -> HighlightSeverity.WARNING
        Error.Level.GOAL -> HighlightSeverity.WEAK_WARNING
        Error.Level.INFO -> HighlightSeverity.INFORMATION
    }
}
