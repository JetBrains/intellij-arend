package org.arend.psi

import com.intellij.psi.tree.IElementType
import org.arend.ArendLanguage

class ArendCompositeElementType(debugName: String) : IElementType(debugName, ArendLanguage.INSTANCE)
