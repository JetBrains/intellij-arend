package org.arend.navigation

import com.intellij.navigation.ChooseByNameContributor
import org.arend.ArendTestBase

class ArendFileNavigationContributorTest : ArendTestBase() {
    fun `test contains files with metas from arend-lib`() {
        withStdLib {
            assertNotEmpty(getByName("Meta.ard"))
            assertNotEmpty(getByName("Function.Meta.ard"))
            assertNotEmpty(getByName("Paths.Meta.ard"))
        }
    }

    private fun getByName(name: String): List<String> =
            ChooseByNameContributor.FILE_EP_NAME.extensionList.asSequence()
                    .flatMap { it.getItemsByName(name, name, project, true).map { it.name!! } }
                    .toList()
}