package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract


abstract class ArendNameTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendNameTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr

    override fun isStrict() = strictKw != null
}

abstract class ArendLamTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendLamTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr

    override fun isStrict() = false
}

abstract class ArendNameTeleUntypedImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendNameTeleUntyped {
    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList(): List<Referable?> = listOf(defIdentifier)

    override fun getType(): ArendExpr? = null

    override fun isStrict() = false
}

abstract class ArendTypeTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendTypeTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> =
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier }?.ifEmpty { listOf(null) } ?: listOf(null)

    override fun getType(): Abstract.Expression? = typedExpr?.expr ?: literal ?: universeAtom

    override fun isStrict() = strictKw != null
}

abstract class ArendFieldTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendFieldTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable> = fieldDefIdentifierList

    override fun getType() = expr

    override fun isStrict() = false

    override fun isClassifying() = classifyingKw != null

    override fun isCoerce() = coerceKw != null
}
