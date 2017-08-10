package org.vclang.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.VcFileType
import org.vclang.lang.VcLanguage
import org.vclang.lang.core.Surrogate

class VcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VcLanguage),
                                               Abstract.ClassDefinition,
                                               Surrogate.StatementCollection {

    private var globalStatements = emptyList<Surrogate.Statement>()

    override fun getFileType(): FileType = VcFileType

    override fun getPolyParameters(): List<Abstract.TypeParameter> = emptyList()

    override fun getSuperClasses(): List<Abstract.SuperClass> = emptyList()

    override fun getFields(): List<Abstract.ClassField> = emptyList()

    override fun getImplementations(): List<Abstract.Implementation> = emptyList()

    override fun getInstanceDefinitions(): List<Abstract.Definition> = emptyList()

    override fun getGlobalStatements(): List<Surrogate.Statement> = globalStatements

    fun setGlobalStatements(globalStatements: List<Surrogate.Statement>) {
        this.globalStatements = globalStatements
    }

    override fun getPrecedence(): Abstract.Precedence = Abstract.Precedence.DEFAULT

    override fun getParentDefinition(): Abstract.Definition? = null

    override fun isStatic(): Boolean = true

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClass(this, params)
}
