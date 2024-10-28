package org.arend.settings

import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "ArendStatistics", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ArendProjectStatistics : SimplePersistentStateComponent<ArendProjectStatisticsState>(ArendProjectStatisticsState())

class ArendProjectStatisticsState : BaseState() {
    var implementFieldsStatistics by map<String, List<String>>()
}
