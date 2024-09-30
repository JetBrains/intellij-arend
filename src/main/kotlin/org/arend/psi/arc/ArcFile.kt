package org.arend.psi.arc

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiBinaryFileImpl
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.serialization.DeserializationException
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.error.DeserializationError
import org.arend.naming.reference.TCDefReferable
import org.arend.source.StreamBinarySource
import org.arend.term.group.Group
import org.arend.term.group.Statement
import org.arend.term.group.StaticGroup
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.TypeCheckingService
import org.arend.util.arendModules
import java.util.zip.GZIPInputStream

class ArcFile(viewProvider: FileViewProvider) : PsiBinaryFileImpl(viewProvider.manager as PsiManagerImpl, viewProvider) {
    companion object {
        fun decompile(virtualFile: VirtualFile): String {
            val builder = StringBuilder()

            val group = getGroup(virtualFile) ?: return builder.toString()

            val lastStatement = group.statements.lastOrNull()
            for (statement in group.statements.dropLast(1)) {
                if (addStatement(statement, builder)) {
                    builder.append("\n\n")
                }
            }
            addStatement(lastStatement, builder)
            return builder.toString()
        }

        private fun getGroup(virtualFile: VirtualFile): Group? {
            val project = ProjectLocator.getInstance().guessProjectForFile(virtualFile) ?: return null
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

        private fun addStatement(statement: Statement?, builder: StringBuilder): Boolean {
            ((statement as? StaticGroup?)?.referable as? TCDefReferable?)?.typechecked?.let {
                ToAbstractVisitor.convert(it, PrettyPrinterConfig.DEFAULT)
                    .prettyPrint(builder, PrettyPrinterConfig.DEFAULT)
            } ?: return false
            return true
        }

        fun buildFileStub(file: VirtualFile, bytes: ByteArray): PsiJavaFileStub? {
            // TODO like .class files
            return null
        }
    }
}
