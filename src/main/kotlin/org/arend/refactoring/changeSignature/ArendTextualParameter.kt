package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.term.group.AccessModifier

data class ArendTextualParameter(
    private var name: String?,
    private var type: String?,
    private val oldIndex: Int, /* == -1, if does not correspond to an old parameter */
    private var isExplicit: Boolean,
    val isClassifying: Boolean = false,
    val isCoerce: Boolean = false,
    val isProperty: Boolean = false,
    val accessModifier: AccessModifier = AccessModifier.PUBLIC,
    val correspondingReferable: PsiElement?) : ParameterInfo {
    override fun getName(): String = name ?: "_"

    override fun getOldIndex(): Int = oldIndex

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
        this.name = name ?: return
    }

    override fun getTypeText(): String? = type

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}

    fun setType(type: String?) {
        this.type = type ?: return
    }

    fun isExplicit(): Boolean = isExplicit

    fun switchExplicit() {
        isExplicit = !isExplicit
    }

    companion object {
        fun createEmpty(): ArendTextualParameter = ArendTextualParameter("", "", ParameterInfo.NEW_PARAMETER, true, correspondingReferable = null)
    }
}