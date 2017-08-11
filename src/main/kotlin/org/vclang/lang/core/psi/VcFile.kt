package org.vclang.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.VcFileType
import org.vclang.lang.VcLanguage
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.parser.fullyQualifiedName
import java.nio.file.Paths

class VcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VcLanguage),
                                               Abstract.ClassDefinition,
                                               Surrogate.StatementCollection {
    private var globalStatements = emptyList<Surrogate.Statement>()
    val modulePath: ModulePath
        get() {
            val sourceRoot = sourceRoot ?: contentRoot
            val sourcePath = sourceRoot?.let { Paths.get(it.path) }
            val parentPath = Paths.get(virtualFile.parent.path)
            val parentRelativePath = sourcePath?.relativize(parentPath)
            val parentModulePath = parentRelativePath?.let { ModulePath(it.map { it.toString() }) }
            return if (parentModulePath != null) {
                ModulePath(parentModulePath, virtualFile.nameWithoutExtension)
            } else {
                ModulePath(virtualFile.nameWithoutExtension)
            }
        }

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

    fun findDefinitionByFQName(name: String): Abstract.Definition? =
            accept(FindDefinitionVisitor, name)
}

internal object FindDefinitionVisitor : AbstractDefinitionVisitor<String, Abstract.Definition?> {

    override fun visitFunction(
            definition: Abstract.FunctionDefinition,
            params: String
    ): Abstract.Definition? {
        if (definition.fullyQualifiedName == params) return definition
        definition.globalDefinitions.forEach { it.accept(this, params)?.let { return it } }
        return null
    }

    override fun visitClassField(
            definition: Abstract.ClassField,
            params: String
    ): Abstract.Definition? {
        if (definition.fullyQualifiedName == params) return definition
        return null
    }

    override fun visitData(
            definition: Abstract.DataDefinition,
            params: String
    ): Abstract.Definition? {
        if (definition.fullyQualifiedName == params) return definition
        definition.constructorClauses
                .flatMap { it.constructors }
                .forEach { it.accept(this, params)?.let { return it } }
        return null
    }

    override fun visitConstructor(
            definition: Abstract.Constructor,
            params: String
    ): Abstract.Definition? {
        if (definition.fullyQualifiedName == params) return definition
        return null
    }

    override fun visitClass(
            definition: Abstract.ClassDefinition,
            params: String
    ): Abstract.Definition? {
        if (definition.fullyQualifiedName == params) return definition
        definition.globalDefinitions.forEach { it.accept(this, params)?.let { return it } }
        definition.instanceDefinitions.forEach { it.accept(this, params)?.let { return it } }
        definition.fields.forEach { it.accept(this, params)?.let { return it } }
        return null
    }

    override fun visitImplement(
            definition: Abstract.Implementation,
            params: String
    ): Abstract.Definition? = null

    override fun visitClassView(
            definition: Abstract.ClassView,
            params: String
    ): Abstract.Definition? = null

    override fun visitClassViewField(
            definition: Abstract.ClassViewField,
            params: String
    ): Abstract.Definition? = null

    override fun visitClassViewInstance(
            definition: Abstract.ClassViewInstance,
            params: String
    ): Abstract.Definition? {
        if (definition.fullyQualifiedName == params) return definition
        return null
    }
}
