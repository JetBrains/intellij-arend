package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.psi.ArendAlias
import org.arend.psi.ArendFile
import org.arend.psi.ArendPrec
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiDefReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.stubs.ArendNamedStub
import org.arend.typechecking.TypeCheckingService

abstract class ReferableAdapter<StubT> : PsiStubbedReferableImpl<StubT>, PsiDefReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    abstract fun getPrec(): ArendPrec?

    abstract fun getAlias(): ArendAlias?

    override fun hasAlias() = getAlias() != null

    override fun getAliasName() = getAlias()?.aliasIdentifier?.id?.text

    override fun getAliasPrecedence() = calcPrecedence(getAlias()?.prec)

    override fun getPrecedence() = stub?.precedence ?: calcPrecedence(getPrec())

    override fun getTypecheckable(): PsiLocatedReferable = ancestor<PsiConcreteReferable>() ?: this

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

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
                (referable as? ReferableAdapter<*>)?.tcReferableCache = null
            }
        }
    }

    private fun dropTCRefCachesRecursively() {
        tcReferableCache = null
        if (this !is ArendGroup) return

        for (referable in internalReferables) {
            (referable as? ReferableAdapter<*>)?.dropTCRefCachesRecursively()
        }
        for (statement in statements) {
            (statement.group as? ReferableAdapter<*>)?.dropTCRefCachesRecursively()
        }
        for (subgroup in dynamicSubgroups) {
            (subgroup as? ReferableAdapter<*>)?.dropTCRefCachesRecursively()
        }
    }

    companion object {
        fun calcPrecedence(prec: ArendPrec?): Precedence {
            if (prec == null) return Precedence.DEFAULT
            val assoc = when {
                prec.rightAssocKw != null || prec.infixRightKw != null -> Precedence.Associativity.RIGHT_ASSOC
                prec.leftAssocKw != null || prec.infixLeftKw != null -> Precedence.Associativity.LEFT_ASSOC
                prec.nonAssocKw != null || prec.infixNonKw != null -> Precedence.Associativity.NON_ASSOC
                else -> return Precedence.DEFAULT
            }
            val priority = prec.number
            return Precedence(assoc, if (priority == null) Precedence.MAX_PRIORITY else priority.text?.toByteOrNull()
                ?: (Precedence.MAX_PRIORITY + 1).toByte(), prec.infixRightKw != null || prec.infixLeftKw != null || prec.infixNonKw != null)
        }
    }
}
