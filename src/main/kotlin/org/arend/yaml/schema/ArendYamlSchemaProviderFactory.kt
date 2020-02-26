package org.arend.yaml.schema

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import org.arend.yaml.schema.ArendYamlSchemaProvider

class ArendYamlSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project) =
            listOf(ArendYamlSchemaProvider(project))
}
