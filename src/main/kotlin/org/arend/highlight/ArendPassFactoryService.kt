package org.arend.highlight

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class ArendPassFactoryService {
    @Volatile
    var highlightingPassId = -1

    @Volatile
    var typecheckerPassId = -1
}