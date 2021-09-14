package org.arend.yaml.schema

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

class ArendYamlSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project) =
            listOf(ArendYamlSchemaProvider(project))
}
