package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.stubs.ArendDefClassStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.resolving.util.Universe
import javax.swing.Icon

class ArendDefClass : ArendDefinition<ArendDefClassStub>, ClassReferable, TCDefinition, StubBasedPsiElement<ArendDefClassStub>, Abstract.ClassDefinition, ClassReferenceHolder {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val superClassList: List<ArendSuperClass>
        get() = getChildrenOfType()

    val classStatList: List<ArendClassStat>
        get() = getChildrenOfType()

    val fieldTeleList: List<ArendFieldTele>
        get() = getChildrenOfType()

    val classFieldList: List<ArendClassField>
        get() = getChildrenOfType()

    val classImplementList: List<ArendClassImplement>
        get() = getChildrenOfType()

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    val rbrace: PsiElement?
        get() = findChildByType(RBRACE)

    val noClassifyingKw: PsiElement?
        get() = findChildByType(NO_CLASSIFYING_KW)

    val extendsKw: PsiElement?
        get() = findChildByType(EXTENDS_KW)

    override fun getReferable() = this

    override fun isRecord(): Boolean = hasChildOfType(RECORD_KW)

    override fun withoutClassifying(): Boolean = noClassifyingKw != null

    override fun getSuperClassReferences(): List<ClassReferable> = superClassList.mapNotNull { it.longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? ClassReferable }

    override fun getDynamicSubgroups(): List<ArendGroup> = classStatList.mapNotNull { it.group ?: it.coClause?.functionReference }

    override fun getUsedDefinitions(): List<LocatedReferable> = dynamicSubgroups.mapNotNull {
        if (it is ArendDefinition<*> && it.withUse()) it.referable else null
    } + super.getUsedDefinitions()

    private inline val parameterFields: List<ArendFieldDefIdentifier>
        get() = fieldTeleList.flatMap { it.referableList }

    override fun getInternalReferables(): List<ArendInternalReferable> =
        (parameterFields as List<ArendInternalReferable> ) +
            classStatList.mapNotNull { it.classField } +
            classFieldList

    override fun getFields() = internalReferables

    override fun getFieldReferables(): List<FieldReferable> =
        (parameterFields as List<FieldReferable>) +
            classStatList.mapNotNull { it.classField } +
            classFieldList

    override fun getImplementedFields(): List<LocatedReferable> =
        coClauseElements.mapNotNull { it.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable }

    override fun getDynamicReferables() = classStatList.mapNotNull { it.group }

    override fun getParameters(): List<Abstract.FieldParameter> = fieldTeleList

    override fun getSuperClasses(): List<ArendSuperClass> = superClassList

    override fun getClassElements(): List<Abstract.ClassElement> = children.mapNotNull {
        when (it) {
            is ArendClassField -> it
            is ArendClassImplement -> it
            is ArendClassStat -> it.classField ?: it.classImplement ?: it.overriddenField ?: it.coClause
            else -> null
        }
    }

    override fun getCoClauseElements(): List<ArendClassImplement> = classStatList.mapNotNull { it.classImplement } + classImplementList

    override fun getClassReference() = this

    override fun getClassReferenceData(onlyClassRef: Boolean) = ClassReferenceData(this, emptyList(), emptySet(), false)

    override val typeOf: Abstract.Expression
        get() = Universe

    override fun getKind() = GlobalReferable.Kind.CLASS

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = if (isRecord) ArendIcons.RECORD_DEFINITION else ArendIcons.CLASS_DEFINITION

    override val tcReferable: TCDefReferable?
        get() = super.tcReferable as TCDefReferable?
}
