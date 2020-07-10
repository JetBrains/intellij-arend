package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.FullModuleReferable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendAlias
import org.arend.psi.ArendFile
import org.arend.psi.ArendPrec
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.ext.TCDefinition
import org.arend.psi.stubs.ArendNamedStub
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.FieldDataLocatedReferable
import org.arend.typechecking.TypeCheckingService

abstract class ReferableAdapter<StubT> : PsiStubbedReferableImpl<StubT>, PsiLocatedReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    abstract fun getPrec(): ArendPrec?

    abstract fun getAlias(): ArendAlias?

    override fun hasAlias() = getAlias() != null

    override fun getAliasName() = getAlias()?.aliasIdentifier?.id?.text

    override fun getAliasPrecedence() = calcPrecedence(getAlias()?.prec)

    override fun getPrecedence() = stub?.precedence ?: calcPrecedence(getPrec())

    override fun getTypecheckable(): PsiLocatedReferable = ancestor<TCDefinition>() ?: this

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

    private var tcReferableCache: TCReferable? = null

    override val tcReferable: TCReferable?
        get() = tcReferableCache ?: runReadAction {
            synchronized(this) {
                tcReferableCache ?: run {
                    val file = containingFile as? ArendFile ?: return@run null
                    val longName = refLongName
                    file.tcRefMap[longName]?.let { return@run it }
                    val locatedParent = locatedReferableParent
                    val parent = if (locatedParent is ArendFile) locatedParent.moduleLocation?.let { FullModuleReferable(it) } else locatedParent?.tcReferable
                    val pointer = SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer<PsiLocatedReferable>(this, file)
                    val ref = if (this is FieldReferable) FieldDataLocatedReferable(pointer, this, parent) else DataLocatedReferable(pointer, this, parent)
                    tcReferableCache = ref
                    file.tcRefMap[longName] = ref
                    ref
                }
            }
        }

    override fun dropTypechecked() {
        val service = project.service<TypeCheckingService>()
        val tcRef = tcReferableCache ?: run {
            val file = containingFile as? ArendFile ?: return
            service.tcRefMaps[file.moduleLocation]?.get(refLongName)
        } ?: return
        tcRef.typechecked = null
        service.dependencyListener.update(tcRef)
        tcReferableCache = null
    }

    override fun checkTCReferable() {
        val tcRef = tcReferableCache ?: return
        val refName = refName
        if (tcRef.refName != refName) synchronized(this) {
            val tcRef2 = tcReferableCache ?: return
            if (tcRef2.refName != refName) {
                dropTCRefCaches()
                return
            }
        }

        tcReferableCache = null
        if (this is ArendGroup) {
            for (referable in internalReferables) {
                (referable as? ReferableAdapter<*>)?.dropTCRefCaches()
            }
        }
    }

    private fun dropTCRefCaches() {
        tcReferableCache = null
        if (this !is ArendGroup) return

        for (referable in internalReferables) {
            (referable as? ReferableAdapter<*>)?.dropTCRefCaches()
        }
        for (subgroup in subgroups) {
            (subgroup as? ReferableAdapter<*>)?.dropTCRefCaches()
        }
        for (subgroup in dynamicSubgroups) {
            (subgroup as? ReferableAdapter<*>)?.dropTCRefCaches()
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
