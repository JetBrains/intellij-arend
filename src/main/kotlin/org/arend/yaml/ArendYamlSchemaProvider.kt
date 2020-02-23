package org.arend.yaml

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion

class ArendYamlSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
    private val schemaFileLazy: VirtualFile? by lazy {
        JsonSchemaProviderFactory.getResourceFile(this::class.java, "/jsonSchema/arend.yaml.json")
    }

    override fun getName() = "arend.yaml.json"

    override fun getSchemaVersion() = JsonSchemaVersion.SCHEMA_4

    override fun getSchemaType() = SchemaType.embeddedSchema

    override fun isAvailable(file: VirtualFile) = !project.isDisposed
            && JsonSchemaService.Impl.get(project).isApplicableToFile(file)

    override fun getSchemaFile() = schemaFileLazy
}