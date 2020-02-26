package org.arend.yaml.schema

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import org.arend.util.FileUtils

class ArendYamlSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile?) =
            FileUtils.LIBRARY_CONFIG_FILE == file?.name
}
