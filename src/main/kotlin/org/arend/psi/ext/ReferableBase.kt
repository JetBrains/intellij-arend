package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.stubs.ArendNamedStub
import org.arend.typechecking.TypeCheckingService

abstract class ReferableBase<StubT> : PsiStubbedReferableImpl<StubT>, PsiDefReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    open val prec: ArendPrec?
        get() = getChildOfType()

    open val alias: ArendAlias?
        get() = getChildOfType()

    override fun hasAlias() = alias != null

    override fun getAliasName() = alias?.aliasIdentifier?.id?.text

    override fun getAliasPrecedence() = calcPrecedence(alias?.prec)

    override fun getPrecedence() = stub?.precedence ?: calcPrecedence(prec)

    override fun getTypecheckable(): PsiLocatedReferable = ancestor<PsiConcreteReferable>() ?: this

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

    override val defIdentifier: ArendDefIdentifier?
        get() = getChildOfType()

    protected var tcReferableCache: TCReferable? = null

    fun dropTCCache() {
        tcReferableCache = null
    }

    protected abstract fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?): TCReferable

    override val tcReferable: TCReferable?
        get() = tcReferableCache ?: runReadAction {
            synchronized(this) {
                tcReferableCache ?: run {
                    val file = (if (isValid) containingFile as? ArendFile else null) ?: return@run null
                    val longName = refLongName
                    val tcRefMap = file.getTCRefMap(Referable.RefKind.EXPR)
                    tcRefMap[longName]?.let {
                        tcReferableCache = it
                        return@run it
                    }
                    val locatedParent = locatedReferableParent
                    val parent = if (locatedParent is ArendFile) locatedParent.moduleLocation?.let { FullModuleReferable(it) } else locatedParent?.tcReferable
                    val pointer = SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer<PsiLocatedReferable>(this, file)
                    val ref = makeTCReferable(pointer, parent)
                    tcReferableCache = ref
                    tcRefMap[longName] = ref
                    ref
                }
            }
        }

    override fun dropTypechecked() {
        val service = project.service<TypeCheckingService>()
        val tcRef = tcReferableCache ?: run {
            val location = (containingFile as? ArendFile)?.moduleLocation ?: return
            service.getTCRefMaps(Referable.RefKind.EXPR)[location]?.get(refLongName)
        } ?: return
        service.dependencyListener.update(tcRef)
        (tcRef as? TCDefReferable)?.typechecked = null
        tcReferableCache = null
    }

    override fun checkTCReferable(): Boolean {
        val tcRef = tcReferableCache ?: return true
        return if (tcRef.underlyingReferable != this) {
            dropTCReferable()
            true
        } else false
    }

    override fun checkTCReferableName() {
        val tcRef = tcReferableCache ?: return
        val refName = refName
        if (tcRef.refName != refName) synchronized(this) {
            val tcRef2 = tcReferableCache ?: return
            if (tcRef2.refName != refName) {
                dropTCRefCachesRecursively()
                return
            }
        }
        dropTCReferable()
    }

    override fun dropTCReferable() {
        tcReferableCache = null
        if (this is ArendGroup) {
            for (referable in internalReferables) {
                (referable as? ReferableBase<*>)?.tcReferableCache = null
            }
        }
    }

    private fun dropTCRefCachesRecursively() {
        tcReferableCache = null
        if (this !is ArendGroup) return

        for (referable in internalReferables) {
            (referable as? ReferableBase<*>)?.dropTCRefCachesRecursively()
        }
        for (statement in statements) {
            (statement.group as? ReferableBase<*>)?.dropTCRefCachesRecursively()
        }
        for (subgroup in dynamicSubgroups) {
            (subgroup as? ReferableBase<*>)?.dropTCRefCachesRecursively()
        }
    }

    companion object {
        fun calcPrecedence(prec: ArendPrec?): Precedence {
            if (prec == null) return Precedence.DEFAULT
            val type = prec.firstRelevantChild.elementType
            val assoc = when (type) {
                RIGHT_ASSOC_KW, INFIX_RIGHT_KW -> Precedence.Associativity.RIGHT_ASSOC
                LEFT_ASSOC_KW, INFIX_LEFT_KW -> Precedence.Associativity.LEFT_ASSOC
                NON_ASSOC_KW, INFIX_NON_KW -> Precedence.Associativity.NON_ASSOC
                else -> return Precedence.DEFAULT
            }
            val priority = prec.number
            return Precedence(assoc, if (priority == null) Precedence.MAX_PRIORITY else priority.text?.toByteOrNull()
                ?: (Precedence.MAX_PRIORITY + 1).toByte(), type == INFIX_RIGHT_KW || type == INFIX_LEFT_KW || type == INFIX_NON_KW)
        }
    }
}
