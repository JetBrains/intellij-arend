package org.arend.yaml

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler

class ArendYamlSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile?) =
            file?.name == "arend.yaml"
}
