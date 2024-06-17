package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.ArendIcons
import org.arend.codeInsight.completion.ReplaceInsertHandler
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.naming.reference.*
import org.arend.naming.reference.Referable.RefKind
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendDefMeta
import org.arend.psi.ext.ReferableBase
import org.arend.refactoring.ArendNamesValidator
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

open class ArendDefReferenceImpl<T : ArendReferenceElement>(element: T) : PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun resolve(): PsiElement = element.parent as? PsiReferable ?: element

    override fun isReferenceTo(element: PsiElement): Boolean = false
}

class ArendPatternDefReferenceImpl<T : ArendReferenceElement>(element: T) : ArendReferenceImpl<T>(element) {
    override fun resolve() = resolve(true)
}

class TemporaryLocatedReferable(private val referable: LocatedReferable) : LocatedReferable by referable, TCDefReferable {
    override fun getData() = referable

    override fun setTypechecked(definition: Definition?) {}

    override fun getTypechecked(): Definition? = null

    override fun getUnderlyingReferable() = referable
}

object ArendIdReferableConverter : ReferableConverter {
    override fun toDataLocatedReferable(referable: LocatedReferable?) = when (referable) {
        null -> null
        is TCReferable -> referable
        is FieldReferable -> FieldReferableImpl(referable.precedence, referable.refName, referable.isExplicitField, referable.isParameterField, TCDefReferable.NULL_REFERABLE)
        is ArendDefMeta -> referable.metaRef ?: referable.makeTCReferable(TCDefReferable.NULL_REFERABLE)
        else -> TemporaryLocatedReferable(referable)
    }

    override fun convert(referable: Referable?) = (referable as? ArendDefMeta)?.metaRef ?: referable
}

open class ArendReferenceImpl<T : ArendReferenceElement>(element: T, private val beforeImportDot: Boolean = false, private val refKind: RefKind = RefKind.EXPR) : PsiReferenceBase<T>(element, element.rangeInElement), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun bindToElement(element: PsiElement) = element

    override fun getVariants(): Array<Any> {
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
                atomFieldsAcc.numberList.isNotEmpty() -> null
                else -> atomFieldsAcc.parent
            }
            val newExprParent = ((argParent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr)?.parent
            if (newExprParent is ArendReplLine) {
                val commandName = newExprParent.replCommand?.text?.drop(1)
                return if (commandName == null) emptyArray() else getReplCompletion(commandName)
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
        var elements = if (expr == null) element.scope.getElements(refKind) else emptyList()
        val resolverListener = if (expr == null) null else object : ResolverListener {
            override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable?, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>, scope: Scope) {
                if (refExpr.data == parent || refExpr.data == element) {
                    elements = scope.getElements(refKind)
                }
            }

            override fun levelResolved(originalRef: Referable?, refExpr: Concrete.IdLevelExpression, resolvedRef: Referable?, availableRefs: Collection<Referable>) {
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
    }

    override fun resolve() = resolve(false)

    protected fun resolve(onlyConstructor: Boolean): PsiElement? {
        val cache = element.project.service<ArendResolveCache>()
        val resolver = { when {
            beforeImportDot -> {
                val refName = element.referenceName
                var result: Referable? = null
                for (ref in element.scope.getElements(refKind)) {
                    val name = if (ref is ModuleReferable) ref.path.lastName else ref.refName
                    if (name == refName) {
                        result = ref
                        if (ref !is PsiModuleReferable || ref.modules.firstOrNull() is PsiDirectory) {
                            break
                        }
                    }
                }
                result
            }
            onlyConstructor -> {
                val ref = element.scope.globalSubscope.resolveName(element.referenceName, refKind)
                if (ref is GlobalReferable && ref.kind.isConstructor) ref else element as? ArendDefIdentifier
            }
            else -> {
                val expr = element.ancestor<ArendExpr>()
                val def = expr?.ancestor<PsiConcreteReferable>()
                when {
                    def != null -> {
                        val project = def.project
                        PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true, ArendResolverListener(cache)).getConcrete(def)
                        cache.getCached(element)
                    }
                    expr != null -> {
                        ConcreteBuilder.convertExpression(expr).accept(ExpressionResolveNameVisitor(ArendReferableConverter, CachingScope.make(element.scope), ArrayList<Referable>(), DummyErrorReporter.INSTANCE, ArendResolverListener(cache)), null)
                        cache.getCached(element)
                    }
                    else -> element.scope.resolveName(element.referenceName, refKind)
                }
            }
        } }

        return when (val ref = cache.resolveCached(resolver, element)?.underlyingReferable) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is ModuleReferable -> {
                if (ref.path == Prelude.MODULE_PATH) {
                    element.project.service<TypeCheckingService>().prelude
                } else {
                    (element.containingFile as? ArendFile)?.arendLibrary?.config?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                }
            }
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
                    val elementName = origElement.refName
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
                    (ref as? PsiReferable)?.psiElementType?.let { builder = builder.withTypeText(it.oneLineText) }
                    builder
                }
                is ModuleReferable -> {
                    val module = if (ref is PsiModuleReferable) {
                        ref.modules.firstOrNull()
                    } else {
                        (containingFile as? ArendFile)?.arendLibrary?.config?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                    }
                    when (module) {
                        null -> LookupElementBuilder.create(ref, ref.path.lastName).withIcon(ArendIcons.DIRECTORY)
                        is ArendFile -> LookupElementBuilder.create(module, ref.path.lastName).withIcon(ArendIcons.AREND_FILE)
                        else -> LookupElementBuilder.createWithIcon(module)
                    }
                }
                else -> LookupElementBuilder.create(ref, origElement.textRepresentation())
            }
        }
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix(FileUtils.EXTENSION)
    if (!ArendNamesValidator.isIdentifier(name, oldNameIdentifier.project)) return
    val factory = ArendPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is ArendDefIdentifier -> factory.createDefIdentifier(name)
        is ArendRefIdentifier -> factory.createRefIdentifier(name)
        is ArendIPName -> if (oldNameIdentifier.postfix != null) factory.createPostfixName(name) else factory.createInfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}
