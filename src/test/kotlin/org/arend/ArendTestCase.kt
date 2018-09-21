package org.arend

interface ArendTestCase {

    fun getTestDataPath(): String

    companion object {
        const val testResourcesPath = "src/test/resources"
    }
}
