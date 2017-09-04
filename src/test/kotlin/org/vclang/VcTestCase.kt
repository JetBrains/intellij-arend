package org.vclang

interface VcTestCase {

    fun getTestDataPath(): String

    companion object {
        val testResourcesPath = "src/test/resources"
    }
}
