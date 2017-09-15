package org.vclang.resolving

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import org.vclang.VcIcons
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcCompositeElement

/* TODO[abstract]
object PreludeNamespace : Namespace {
    override fun getElements(): Set<GlobalReferable>
        get() = setOf(
                LookupElementBuilder.create("Nat").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("zero").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("suc").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("I").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("left").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("right").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("Path").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("path").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("=").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("@").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("coe").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("iso").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("TrP").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("inP").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("truncP").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("TrS").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("inS").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("truncS").withIcon(VcIcons.CONSTRUCTOR)
        )

    override fun resolve(name: String): PsiReferable? = null
}
*/
