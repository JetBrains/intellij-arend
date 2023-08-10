package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.ext.module.LongName
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.stubs.ArendNamedStub
import org.arend.resolving.IntellijTCReferable
import org.arend.term.group.AccessModifier
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentHashMap

abstract class ReferableBase<StubT> : PsiStubbedReferableImpl<StubT>, PsiDefReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    open val prec: ArendPrec?
        get() = childOfType()

    open val alias: ArendAlias?
        get() = childOfType()

    override fun hasAlias() = alias != null

    override fun getAliasName() = alias?.aliasIdentifier?.id?.text

    override fun getAliasPrecedence() = calcPrecedence(alias?.prec)

    override fun getPrecedence() = stub?.precedence ?: calcPrecedence(prec)

    override fun getTypecheckable(): PsiLocatedReferable = ancestor<PsiConcreteReferable>() ?: this

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

    override fun getAccessModifier() =
        parent?.childOfType<ArendAccessMod>()?.accessModifier ?: ancestor<ArendStatAccessMod>()?.accessModifier ?: AccessModifier.PUBLIC

    override val defIdentifier: ArendDefIdentifier?
        get() = childOfType()

    protected var tcReferableCache: TCReferable? = null
    private var tcRefMapCache: ConcurrentHashMap<LongName, IntellijTCReferable>? = null

    private val tcRefMap: ConcurrentHashMap<LongName, IntellijTCReferable>?
        get() {
            tcRefMapCache?.let { return it }
            val file = if (isValid) containingFile as? ArendFile else null
            return file?.getTCRefMap(Referable.RefKind.EXPR)
        }

    fun dropTCCache() {
        tcReferableCache = null
    }

    override val tcReferableCached: TCReferable?
        get() = tcReferableCache

    protected abstract fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?): IntellijTCReferable

    override val tcReferable: TCReferable?
        get() = tcReferableCache ?: runReadAction {
            synchronized(this) {
                tcReferableCache ?: run {
                    val file = (if (isValid) containingFile as? ArendFile else null) ?: return@run null
                    val longName = refLongName
                    val tcRefMap = tcRefMap ?: return@run null
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

    override fun dropTCReferable() {
        tcReferableCache = null
        val tcRefMap = tcRefMap
        val name = refLongName
        tcRefMap?.remove(name)
        if (this is ArendGroup) {
            val list = ArrayList<String>(name.toList().size)
            list.addAll(name.toList())
            list.add("")
            val internalName = LongName(list)
            for (referable in internalReferables) {
                (referable as? ReferableBase<*>)?.tcReferableCache = null
                list[list.size - 1] = referable.refName
                tcRefMap?.remove(internalName)
            }
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
