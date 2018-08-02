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
import com.jetbrains.jetpad.vclang.naming.scope.NamespaceCommandNamespace
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalError
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcNewExprImplMixin
import org.vclang.psi.ext.VcPatternImplMixin
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.psi.ext.impl.InstanceAdapter
import org.vclang.quickfix.InstanceQuickFix
import org.vclang.resolving.PsiPartialConcreteProvider

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        var color: VcHighlightingColors? = null
        val resolved = if (element is VcReferenceElement) {
            val resolved = VclangImportHintAction.getResolved(element)
            when (resolved) {
                null -> {
                    val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                    annotation.highlightType = ProblemHighlightType.ERROR

                    val fix = VclangImportHintAction(element)
                    if (fix.isAvailable(element.project, null, element.containingFile))
                        annotation.registerFix(fix)

                    return
                }
                is PsiDirectory -> {
                    val refList = (element.parent as? VcLongName)?.refIdentifierList
                    if (refList == null || refList.indexOf(element) == refList.size - 1) {
                        holder.createErrorAnnotation(element, "Unexpected reference to a directory")
                    }
                    return
                }
                is VcFile -> {
                    val longName = element.parent as? VcLongName
                    if (longName == null || longName.parent !is VcStatCmd) {
                        val refList = longName?.refIdentifierList
                        if (refList == null || refList.indexOf(element) == refList.size - 1) {
                            holder.createErrorAnnotation(element, "Unexpected reference to a file")
                        }
                    }
                    return
                }
                is GlobalReferable -> {
                    if (resolved.precedence.isInfix) {
                        color = VcHighlightingColors.OPERATORS
                    }
                }
            }
            resolved
        } else null

        if (element is VcGoal) {
            holder.createWarningAnnotation(element, "goal")
            return
        }

        if (element is VcArgumentAppExpr) {
            val pElement = element.parent
            if (pElement is VcNewExprImplMixin) {
                InstanceQuickFix.annotateNewExpr(pElement, holder)
            }
            if (pElement is VcNewExpr && (pElement.newKw != null || pElement.lbrace != null) || pElement is VcNewArg || pElement is VcDefInstance) {
                val longName = element.longNameExpr?.longName ?: run {
                    val atomFieldsAcc = element.atomFieldsAcc
                    if (atomFieldsAcc != null && atomFieldsAcc.fieldAccList.isEmpty()) {
                        atomFieldsAcc.atom.literal?.longName
                    } else {
                        null
                    }
                }
                if (longName != null) {
                    val ref = longName.referent
                    val resolvedRef = (ref as? UnresolvedReference)?.resolve(element.scope) ?: ref
                    if (resolvedRef !is VcDefClass && resolvedRef !is UnresolvedReference && resolvedRef !is ErrorReference) {
                        holder.createErrorAnnotation(longName, "Expected a class")
                    }
                    if (pElement is VcDefInstance && resolvedRef is VcDefClass && resolvedRef.recordKw != null) {
                        holder.createErrorAnnotation(longName, "Expected a class, got a record")
                    }
                }
            }
            return
        }

        if (element is VcPatternImplMixin) {
            val number = element.number
            if (number == Concrete.NumberPattern.MAX_VALUE || number == -Concrete.NumberPattern.MAX_VALUE) {
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

        val nameResolvingChecker = object : NameResolvingChecker(false, false, PsiPartialConcreteProvider) {
            override fun onDefinitionNamesClash(ref1: LocatedReferable, ref2: LocatedReferable, level: Error.Level) {
                holder.createAnnotation(levelToSeverity(level), element.textRange, "Duplicate definition name '${ref2.textRepresentation()}'")
            }

            override fun onFieldNamesClash(ref1: LocatedReferable, superClass1: ClassReferable, ref2: LocatedReferable, superClass2: ClassReferable, currentClass: ClassReferable, level: Error.Level) {
                holder.createAnnotation(levelToSeverity(level), element.textRange, "Field is already defined in super class '${superClass1.textRepresentation()}'")
            }

            public override fun onNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                annotateNamespacesClash(cmd1, cmd2, name, level)
                annotateNamespacesClash(cmd2, cmd1, name, level)
            }

            fun annotateNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
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
                    holder.createAnnotation(levelToSeverity(error.level), ((cause as? VcDefFunction)?.defIdentifier ?: cause).textRange, error.shortMessage)
                }
            }
        }

        if (element is VcStatCmd) {
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
            val commandNames = NamespaceCommandNamespace.resolveNamespace(scope, element).elements.map { it.textRepresentation() }.toSet()
            if (commandNames.isEmpty()) {
                return
            }

            for (cmd in nsCommands) {
                if (cmd == element) {
                    continue
                }
                for (other in NamespaceCommandNamespace.resolveNamespace(scope, cmd).elements) {
                    val otherName = other.textRepresentation()
                    if (commandNames.contains(otherName)) {
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

        if (element is VcFieldTele) {
            val definition = element.parent
            if (definition is VcDefClass && definition.fatArrow != null && definition.fieldTeleList.firstOrNull() == element) {
                holder.createAnnotation(HighlightSeverity.ERROR, TextRange(element.textRange.startOffset, (definition.fieldTeleList.lastOrNull() ?: element).textRange.endOffset), "Class synonyms cannot have parameters")
            }
            return
        }

        if (element is VcRefIdentifier && element.parent is VcDefClass && resolved != null) {
            when {
                resolved !is VcDefClass -> "Expected a class"
                resolved.recordKw != null -> "Expected a class, got a record"
                resolved.fatArrow != null -> "Expected a class, got a class synonym"
                else -> null
            }?.let { msg -> holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, msg) }
            return
        }

        if (element is VcLongName) {
            val parent = element.parent
            when (parent) {
                is VcDefClass -> {
                    val superClass = element.refIdentifierList.lastOrNull()?.reference?.resolve()
                    if (superClass != null) {
                        if (superClass !is VcDefClass) {
                            holder.createErrorAnnotation(element, "Expected a class")
                        } else if (parent.fatArrow != null) {
                            nameResolvingChecker.checkSuperClassOfSynonym(superClass, parent.refIdentifier?.reference?.resolve() as? ClassReferable, element)
                        }
                    }
                }
                is Abstract.ClassFieldImpl -> if ((parent is VcCoClause && parent.lbrace != null || parent is VcClassImplement && parent.lbrace != null) && parent.classReference == null) {
                    holder.createErrorAnnotation(element, "Expected either a class or a field which has a class as its type")
                }
            }
            return
        }

        if (element is LeafPsiElement) {
            when (element.node.elementType) {
                VcElementTypes.COERCE_KW -> {
                    if (element.parent?.parent is VcFile) {
                        holder.createErrorAnnotation(element as PsiElement, "\\coerce is not allowed on the top level")
                    }
                    return
                }
                VcElementTypes.IMPORT_KW -> {
                    if ((element.parent as? Abstract.NamespaceCommandHolder)?.parentSourceNode !is VcFile) {
                        holder.createErrorAnnotation(element as PsiElement, "\\import is allowed only on the top level")
                    }
                    return
                }
            }
        }

        if (element is VcDefIdentifier) {
            val definition = element.parent as? PsiLocatedReferable ?: return

            if (definition is Abstract.ReferableDefinition && (definition is Abstract.ClassField || definition is Abstract.ClassFieldSynonym)) {
                val fieldRef = definition.referable
                if (fieldRef != null) {
                    val classRef = (definition.parentSourceNode as? Abstract.ClassDefinition)?.referable
                    nameResolvingChecker.checkField(fieldRef, NameResolvingChecker.collectClassFields(classRef), classRef)
                }
            }

            if (definition is InstanceAdapter) {
                InstanceQuickFix.annotateClassInstance(definition, holder)
            }

            fun checkReference(oldRef: LocatedReferable?, newRef: LocatedReferable, parentRef: LocatedReferable?): Boolean {
                if (oldRef == null || oldRef == newRef) {
                    return true
                }
                val newName = newRef.textRepresentation()
                if (newName.isEmpty() || newName == "_" || newName != oldRef.textRepresentation()) {
                    return true
                }
                return nameResolvingChecker.checkReference(oldRef, newRef, parentRef)
            }

            fun checkDuplicateDefinitions(parent: Abstract.ReferableDefinition, internal: Boolean) {
                val pparent = parent.parentSourceNode
                if (pparent is Group) {
                    val parentRef = if (internal) pparent.referable else null
                    for (ref in pparent.constructors) {
                        if (!checkReference(ref.referable, definition, parentRef)) return
                    }
                    for (ref in pparent.fields) {
                        if (!checkReference(ref.referable, definition, parentRef)) return
                    }

                    for (subgroup in pparent.subgroups) {
                        if (!checkReference(subgroup.referable, definition, parentRef)) return
                        if (internal) {
                            for (ref in subgroup.constructors) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                            for (ref in subgroup.fields) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                        }
                    }
                    for (subgroup in pparent.dynamicSubgroups) {
                        if (!checkReference(subgroup.referable, definition, parentRef)) return
                        if (internal) {
                            for (ref in subgroup.constructors) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                            for (ref in subgroup.fields) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                        }
                    }
                }
            }

            if (definition is Abstract.ReferableDefinition) {
                checkDuplicateDefinitions(definition, false)

                val parent = definition.parent
                if (definition !is Abstract.Definition && parent is Abstract.Definition) {
                    checkDuplicateDefinitions(parent, true)
                }
            }
        }
    }

    private fun collectDefined(group: Group): HashSet<String> {
        val result = HashSet<String>()
        for (subgroup in group.subgroups) {
            result.add(subgroup.referable.textRepresentation())
            for (ref in subgroup.constructors) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
            for (ref in subgroup.fields) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
        }
        for (subgroup in group.dynamicSubgroups) {
            result.add(subgroup.referable.textRepresentation())
            for (ref in subgroup.constructors) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
            for (ref in subgroup.fields) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
        }
        for (ref in group.constructors) {
            result.add(ref.referable.textRepresentation())
        }
        for (ref in group.fields) {
            result.add(ref.referable.textRepresentation())
        }
        return result
    }

    private fun levelToSeverity(level: Error.Level) = when (level) {
        Error.Level.ERROR -> HighlightSeverity.ERROR
        Error.Level.WARNING -> HighlightSeverity.WARNING
        Error.Level.GOAL -> HighlightSeverity.WARNING
        Error.Level.INFO -> HighlightSeverity.INFORMATION
    }
}
