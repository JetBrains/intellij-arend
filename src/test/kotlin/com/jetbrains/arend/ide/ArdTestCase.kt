package com.jetbrains.arend.ide

interface ArdTestCase {

    fun getTestDataPath(): String

    companion object {
        val testResourcesPath = "src/test/resources"
    }
}
