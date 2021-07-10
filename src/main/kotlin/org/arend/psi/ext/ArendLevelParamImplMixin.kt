package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.DataLevelReferable
import org.arend.naming.reference.LevelReferable
import org.arend.psi.ArendLevelParam

abstract class ArendLevelParamImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendLevelParam {
    private var levelRefCache: LevelReferable? = null

    val levelRef: LevelReferable
        get() = runReadAction {
            val ref = levelRefCache
            val defId = defIdentifier
            val name = defId.refName
            if (ref?.data == defId && ref.refName == name) ref else synchronized(this) {
                val newRef = DataLevelReferable(defId, name)
                levelRefCache = newRef
                newRef
            }
        }
}