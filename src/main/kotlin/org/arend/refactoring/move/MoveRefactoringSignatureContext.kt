package org.arend.refactoring.move

import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.PsiLocatedReferable

data class MoveRefactoringSignatureContext(
    val myThisVars: Map<PsiLocatedReferable, String>,
    val membersEnvelopingClasses: Map<PsiLocatedReferable, ArendDefClass>
)