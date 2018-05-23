package org.vclang.psi

import com.intellij.psi.tree.IElementType
import org.vclang.VcLanguage

class VcCompositeElementType(debugName: String) : IElementType(debugName, VcLanguage.INSTANCE)
