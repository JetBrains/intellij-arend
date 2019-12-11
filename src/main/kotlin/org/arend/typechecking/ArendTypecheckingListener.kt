package org.arend.typechecking

import com.intellij.util.containers.ContainerUtil
import org.arend.core.context.binding.Binding
import org.arend.naming.reference.Referable
import org.arend.psi.ArendAtomPatternOrPrefix
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendPattern


object ArendTypecheckingListener : TypecheckingListener {
    val referableTypeCache: MutableMap<Referable, Binding> = ContainerUtil.createWeakKeySoftValueMap<Referable, Binding>()

    override fun referableTypechecked(referable: Referable, binding: Binding) {
        val defId = referable.underlyingReferable as? ArendDefIdentifier ?: return
        if (defId.parent?.let { it is ArendPattern || it is ArendAtomPatternOrPrefix } == true) {
            referableTypeCache[defId] = binding
        }
    }
}
