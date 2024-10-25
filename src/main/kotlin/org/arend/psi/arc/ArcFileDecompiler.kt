package org.arend.psi.arc

import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.visitor.VoidExpressionVisitor
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.serialization.DeserializationException
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.error.DeserializationError
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.LexicalScope
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.fullName
import org.arend.source.StreamBinarySource
import org.arend.term.group.Group
import org.arend.term.group.Statement
import org.arend.term.group.StaticGroup
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.TypeCheckingService
import org.arend.util.arendModules
import java.util.zip.GZIPInputStream
import kotlin.collections.forEach

class ArcFileDecompiler : BinaryFileDecompiler {
    override fun decompile(file: VirtualFile): CharSequence {
        val decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Decompiler::class.java)
        if (decompiler is ArcDecompiler) {
            return Companion.decompile(file)
        }

        if (decompiler is ClassFileDecompilers.Full) {
            val manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().defaultProject)
            return decompiler.createFileViewProvider(file, manager, true).contents
        }

        if (decompiler is ClassFileDecompilers.Light) {
            return try {
                decompiler.getText(file)
            } catch (e: ClassFileDecompilers.Light.CannotDecompileException) {
                ClsFileImpl.decompile(file)
            }
        }

        throw IllegalStateException(decompiler.javaClass.name +
                    " should be on of " +
                    ClassFileDecompilers.Full::class.java.name +
                    " or " +
                    ClassFileDecompilers.Light::class.java.name
        )
    }

    companion object {
        fun decompile(virtualFile: VirtualFile): String {
            val project = ProjectLocator.getInstance().guessProjectForFile(virtualFile) ?: return ""

            val builder = StringBuilder()

            val group = getGroup(virtualFile, project) ?: return builder.toString()
            val definitions = getDefinitions(group)

            val statementVisitor = object : VoidExpressionVisitor<Void>() {
                val referables = mutableSetOf<PsiLocatedReferable>()

                override fun visitDefCall(expr: DefCallExpression?, params: Void?): Void? {
                    val referable = expr?.definition?.referable?.underlyingReferable as? PsiLocatedReferable? ?: return null
                    referables.add(referable)
                    return null
                }
            }
            definitions.forEach { it.accept(statementVisitor, null) }

            val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
            val definitionsToFiles = mutableSetOf<String>()
            for (referable in statementVisitor.referables) {
                val file = referable.containingFile as ArendFile
                if (file == project.service<TypeCheckingService>().prelude) {
                    continue
                }
                filesToDefinitions.getOrPut(file) { mutableListOf() }.add(referable.fullName)
                definitionsToFiles.add(referable.fullName)
            }

            val fullFiles = mutableMapOf<ArendFile, Boolean>()
            filesLoop@ for ((file, definitions) in filesToDefinitions) {
                for (element in LexicalScope.opened(file).elements) {
                    val fullName = (element as? PsiLocatedReferable?)?.fullName ?: continue
                    if (!definitions.contains(fullName) && definitionsToFiles.contains(fullName)) {
                        fullFiles[file] = false
                        continue@filesLoop
                    }
                    fullFiles[file] = true
                }
            }

            val allElements = mutableSetOf<String>()
            for ((file, full) in fullFiles) {
                if (full) {
                    for (element in LexicalScope.opened(file).elements) {
                        val fullName = (element as? PsiLocatedReferable?)?.fullName ?: continue
                        if (allElements.contains(fullName)) {
                            fullFiles[file] = false
                        }
                    }
                }
            }

            for ((file, definitions) in filesToDefinitions) {
                if (fullFiles[file] == true) {
                    builder.append("\\import ${file.fullName}\n")
                } else {
                    builder.append("\\import ${file.fullName}(${definitions.joinToString(",")})")
                }
            }
            if (filesToDefinitions.isNotEmpty()) {
                builder.append("\n")
            }

            val lastStatement = group.statements.lastOrNull()
            for (statement in group.statements.dropLast(1)) {
                if (addStatement(statement, builder)) {
                    builder.append("\n\n")
                }
            }
            addStatement(lastStatement, builder)
            return builder.toString()
        }

        private fun getGroup(virtualFile: VirtualFile, project: Project): Group? {
            val psiManager = PsiManager.getInstance(project)
            psiManager.findFile(virtualFile) ?: return null

            val libraryManager = project.service<TypeCheckingService>().libraryManager
            val config = project.arendModules.map { ArendModuleConfigService.getInstance(it) }.find {
                it?.root?.let { root -> VfsUtilCore.isAncestor(root, virtualFile, true) } ?: false
            } ?: ArendModuleConfigService.getInstance(project.arendModules.getOrNull(0))
            val library = config?.library ?: return null

            try {
                virtualFile.inputStream.use { inputStream ->
                    val group = StreamBinarySource.getGroup(GZIPInputStream(inputStream), libraryManager, library)
                    return group
                }
            } catch (e : DeserializationException) {
                libraryManager.libraryErrorReporter.report(DeserializationError(ModulePath(virtualFile.name), e))
            }
            return null
        }

        private fun getDefinitions(group: Group): List<Definition> {
            return group.statements.mapNotNull {
                ((it as? StaticGroup?)?.referable as? TCDefReferable?)?.typechecked
            }
        }

        private fun addStatement(statement: Statement?, builder: StringBuilder): Boolean {
            ((statement as? StaticGroup?)?.referable as? TCDefReferable?)?.typechecked?.let {
                ToAbstractVisitor.convert(it, PrettyPrinterConfig.DEFAULT)
                    .prettyPrint(builder, PrettyPrinterConfig.DEFAULT)
            } ?: return false
            return true
        }
    }
}
