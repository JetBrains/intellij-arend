package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.concurrency.ThreadingAssertions
import org.arend.ArendIcons
import org.arend.codeInsight.completion.ReplaceInsertHandler
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.*
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.PrivateFilteredScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendDefMeta
import org.arend.psi.ext.ReferableBase
import org.arend.refactoring.ArendNamesValidator
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.toolWindow.repl.getReplCompletion
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils

interface ArendReference : PsiReference {
    override fun getElement(): ArendReferenceElement

    override fun resolve(): PsiElement?
}

abstract class ArendReferenceBase<T : ArendReferenceElement>(element: T, range: TextRange, private val beforeImportDot: Boolean = false, protected val scopeContext: Scope.ScopeContext = Scope.ScopeContext.STATIC) : PsiReferenceBase<T>(element, range), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    private fun getPsi(referable: Referable?): Any? {
        if (referable is DataContainer) {
            val data = referable.data
            if (data is PsiReferable) return data
        }
        return referable
    }

    override fun resolve(): PsiElement? {
        val service = element.project.service<ArendServerService>()
        val ref = if (ApplicationManager.getApplication().isDispatchThread) {
            // if on dispatch thread and the reference is not resolved already, run resolver in the background thread and return null
            val ref = service.server.getCachedReferable(element)
            if (ref != null) ref else {
                ApplicationManager.getApplication().executeOnPooledThread {
                    service.server.resolveReference(element)
                }
                null
            }
        } else {
            service.server.resolveReference(element)
        }
        return when (ref) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            else -> null
        }
    }

    companion object {
        fun createArendLookUpElement(origElement: Referable, containingFile: PsiFile?, fullName: Boolean, clazz: Class<*>?, notARecord: Boolean, lookup: String? = null): LookupElementBuilder? {
            if (origElement is ModuleReferable && containingFile is ArendFile && origElement.path == containingFile.moduleLocation?.modulePath) {
                return null
            }
            val ref = origElement.underlyingReferable
            return if (origElement is AliasReferable || ref !is ModuleReferable && (clazz != null && !clazz.isInstance(ref) || notARecord && (ref as? ArendDefClass)?.isRecord == true)) {
                null
            } else when (ref) {
                is PsiNamedElement -> {
                    val alias = (ref as? ReferableBase<*>)?.alias?.aliasIdentifier?.id?.text
                    val aliasString = if (alias == null) "" else " $alias"
                    val elementName = if (origElement is IntellijTCReferable) {
                        origElement.displayName
                    } else {
                        origElement.refName
                    }
                    val lookupString = lookup ?: (elementName + aliasString)
                    var builder = LookupElementBuilder.create(ref, lookupString).withIcon(ref.getIcon(0))
                    if (fullName) {
                        builder = builder.withPresentableText(((ref as? PsiLocatedReferable)?.fullName ?: elementName) + aliasString)
                    }
                    if (alias != null) {
                        builder = builder.withInsertHandler(ReplaceInsertHandler(alias))
                    }
                    (ref as? Abstract.ParametersHolder)?.parametersText?.let {
                        builder = builder.withTailText(it, true)
                    }
                    (ref as? PsiReferable)?.psiElementType?.let { builder = builder.withTypeText(it.oneLineText) } ?:
                    (if (ref is ArendFile) ref.moduleLocation?.modulePath?.toList()?.dropLast(1)?.let { ModulePath(it).toString() }
                    else ((ref as? PsiReferable)?.containingFile as? ArendFile)?.fullName)?.let { builder = builder.withTypeText("from $it") }
                    builder
                }
                is ModuleReferable -> {
                    val module = if (ref is PsiModuleReferable) {
                        ref.modules.firstOrNull()
                    } else {
                        (containingFile as? ArendFile)?.arendLibrary?.config?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                    }
                    when (module) {
                        null -> LookupElementBuilder.create(ref, ref.path.toString()).withIcon(ArendIcons.DIRECTORY)
                        is ArendFile -> LookupElementBuilder.create(module, ref.path.toString()).withIcon(ArendIcons.AREND_FILE)
                        else -> LookupElementBuilder.createWithIcon(module)
                    }
                }
                else -> LookupElementBuilder.create(ref, origElement.textRepresentation())
            }
        }
    }
}

open class ArendDefReferenceImpl<T : ArendReferenceElement>(element: T) : ArendReferenceBase<T>(element, TextRange(0, element.textLength)), ArendReference {
    override fun getVariants() = if (element.parent is ArendPattern) {
        val file = element.containingFile
        element.scope.globalSubscope.elements.mapNotNull {
            createArendLookUpElement(it, file, false, ArendConstructor::class.java, false)
        }.toTypedArray()
    } else emptyArray()

    override fun resolve() = when (val parent = element.parent) {
        is PsiReferable -> parent
        is ArendPattern -> super.resolve() ?: element
        else -> element
    }
}

class TemporaryLocatedReferable(private val referable: LocatedReferable) : LocatedReferable by referable, TCDefReferable {
    override fun getData() = referable

    override fun setTypechecked(definition: Definition?) {}

    override fun getTypechecked(): Definition? = null

    override fun getUnderlyingReferable() = referable

    override fun getAccessModifier() = referable.accessModifier
}

object ArendIdReferableConverter : ReferableConverter {
    override fun toDataLocatedReferable(referable: LocatedReferable?) = when (referable) {
        null -> null
        is TCReferable -> referable
        is FieldReferable -> FieldReferableImpl(referable.accessModifier, referable.precedence, referable.refName, referable.isExplicitField, referable.isParameterField, TCDefReferable.NULL_REFERABLE)
        is ArendDefMeta -> referable.metaRef ?: referable.makeTCReferable(TCDefReferable.NULL_REFERABLE)
        else -> TemporaryLocatedReferable(referable)
    }

    override fun convert(referable: Referable?) = (referable as? ArendDefMeta)?.metaRef ?: referable
}

open class ArendReferenceImpl<T : ArendReferenceElement>(element: T, beforeImportDot: Boolean = false, scopeContext: Scope.ScopeContext = Scope.ScopeContext.STATIC) : ArendReferenceBase<T>(element, element.rangeInElement, beforeImportDot, scopeContext), ArendReference {
    override fun bindToElement(element: PsiElement) = element

    override fun getVariants(): Array<Any> {
        // TODO[server2]
        return emptyArray()
        /*
        var notARecord = false
        var clazz: Class<*>? = null
        val element = element
        val parent = element.parent
        val pParent = parent?.parent
        if (pParent is ArendSuperClass) {
            clazz = ArendDefClass::class.java
        } else {
            val atomFieldsAcc = ((pParent as? ArendLiteral)?.parent as? ArendAtom)?.parent as? ArendAtomFieldsAcc
            val argParent = when {
                atomFieldsAcc == null -> (pParent as? ArendLongNameExpr)?.parent
                atomFieldsAcc.fieldAccList.isNotEmpty() -> null
                else -> atomFieldsAcc.parent
            }
            val newExprParent = ((argParent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr)?.parent
            if (newExprParent is ArendReplLine) {
                val commandName = newExprParent.replCommand?.text?.drop(1)
                if (commandName != null) return getReplCompletion(commandName)
            }
            if ((newExprParent as? ArendReturnExpr)?.parent is ArendDefInstance) {
                clazz = ArendDefClass::class.java
                notARecord = true
            }
        }

        val expr = if (element is ArendIPName && element.longName.size > 1 || parent is ArendLongName && pParent !is ArendLocalCoClause && element.findPrevSibling { (it as? LeafPsiElement)?.elementType == ArendElementTypes.DOT } == null || element is ArendRefIdentifier && parent is ArendAtomLevelExpr) {
            element.ancestor<ArendExpr>()
        } else null
        val def = expr?.ancestor<PsiConcreteReferable>()
        var elements = if (expr == null) PrivateFilteredScope(element.scope, true).getElements(scopeContext) else emptyList()
        val resolverListener = if (expr == null) null else object : ResolverListener {
            override fun referenceResolved(expr: Concrete.Expression?, originalRef: Referable?, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>, scope: Scope) {
                if (refExpr.data == parent || refExpr.data == element) {
                    elements = scope.getElements(scopeContext)
                }
            }

            override fun levelResolved(originalRef: Referable?, refExpr: Concrete.VarLevelExpression, resolvedRef: Referable?, availableRefs: Collection<Referable>) {
                if (refExpr.data == parent || refExpr.data == element) {
                    elements = ArrayList(availableRefs)
                }
            }
        }
        when {
            def != null -> PsiConcreteProvider(def.project, DummyErrorReporter.INSTANCE, null, true, resolverListener, ArendIdReferableConverter).getConcrete(def)
            expr != null -> ConcreteBuilder.convertExpression(expr).accept(ExpressionResolveNameVisitor(ArendIdReferableConverter, CachingScope.make(element.scope), ArrayList<Referable>(), DummyErrorReporter.INSTANCE, resolverListener), null)
            else -> {}
        }

        val file = element.containingFile
        return elements.mapNotNull { origElement ->
            createArendLookUpElement(origElement, file, false, clazz, notARecord)
        }.toTypedArray()
        */
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix(FileUtils.EXTENSION)
    if (!ArendNamesValidator.INSTANCE.isIdentifier(name, oldNameIdentifier.project)) return
    val factory = ArendPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is ArendDefIdentifier, is ArendFieldDefIdentifier -> factory.createDefIdentifier(name)
        is ArendRefIdentifier -> factory.createRefIdentifier(name)
        is ArendIPName -> if (oldNameIdentifier.postfix != null) factory.createPostfixName(name) else factory.createInfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}
