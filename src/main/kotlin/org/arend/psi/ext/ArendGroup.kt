package org.arend.psi.ext

import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import org.arend.term.group.Statement

interface ArendStatement : Statement, ArendCompositeElement {
    override fun getGroup(): ArendGroup?
    override fun getNamespaceCommand(): ArendStatCmd?
}

interface ArendGroup: ChildGroup, PsiDefReferable, ArendSourceNode {
    val where: ArendWhere?

    override fun getStatements(): List<ArendStatement>

    override fun getParentGroup(): ArendGroup?

    override fun getDynamicSubgroups(): List<ArendGroup>

    override fun getInternalReferables(): List<ArendInternalReferable>
}

interface ArendInternalReferable: Group.InternalReferable, PsiDefReferable {
    override fun getReferable(): PsiDefReferable
}

fun fillAdditionalNames(group: ArendGroup, names: HashMap<String, ArrayList<PsiLocatedReferable>>) {
    for (statement in group.statements) {
        val subgroup = statement.group ?: continue
        names.computeIfAbsent(subgroup.refName) { ArrayList() }.add(subgroup)
        subgroup.aliasName?.let {
            names.computeIfAbsent(it) { ArrayList() }.add(subgroup)
        }
        fillAdditionalNames(subgroup, names)
    }
    for (referable in group.internalReferables) {
        names.computeIfAbsent(referable.refName) { ArrayList() }.add(referable)
        referable.aliasName?.let {
            names.computeIfAbsent(it) { ArrayList() }.add(referable)
        }
    }
}
