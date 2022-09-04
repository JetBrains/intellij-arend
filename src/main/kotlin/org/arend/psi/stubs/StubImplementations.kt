package org.arend.psi.stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import org.arend.ArendLanguage
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.ext.*

class ArendFileStub(file: ArendFile?, override val name: String?) : PsiFileStubImpl<ArendFile>(file), ArendNamedStub {
    constructor (file: ArendFile?) : this(file, file?.name)

    override val precedence: Precedence
        get() = Precedence.DEFAULT

    override fun getType(): Type = Type

    object Type : IStubFileElementType<ArendFileStub>(ArendLanguage.INSTANCE) {
        override fun getStubVersion() = 6

        override fun getBuilder() = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> =
                    ArendFileStub(file as ArendFile)
        }

        override fun serialize(stub: ArendFileStub, dataStream: StubOutputStream) {
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) = ArendFileStub(null)

        override fun getExternalId(): String = "Arend.file"

        override fun indexStub(stub: PsiFileStub<*>, sink: IndexSink) {
            if (stub is ArendFileStub) sink.indexFile(stub)
        }
    }
}

fun factory(name: String): ArendStubElementType<*, *> = when (name) {
    "DEF_CLASS" -> ArendDefClassStub.Type
    "CLASS_FIELD" -> ArendClassFieldStub.Type
    "FIELD_DEF_IDENTIFIER" -> ArendClassFieldParamStub.Type
    "CLASS_IMPLEMENT" -> ArendClassImplementStub.Type
    "CO_CLAUSE_DEF" -> ArendCoClauseDefStub.Type
    "DEF_INSTANCE" -> ArendDefInstanceStub.Type
    "CONSTRUCTOR" -> ArendConstructorStub.Type
    "DEF_DATA" -> ArendDefDataStub.Type
    "DEF_FUNCTION" -> ArendDefFunctionStub.Type
    "DEF_META" -> ArendDefMetaStub.Type
    "DEF_MODULE" -> ArendDefModuleStub.Type
    else -> error("Unknown anchor $name")
}

abstract class ArendStub<T : PsiLocatedReferable>(parent: StubElement<*>?, elementType: IStubElementType<*, *>, override val name: String?, open val aliasName: String?)
    : StubBase<T>(parent, elementType), ArendNamedStub

class ArendDefClassStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendDefClass>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendDefClassStub, ArendDefClass>("DEF_CLASS") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendDefClassStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendDefClassStub) = ArendDefClass(stub, this)

        override fun indexStub(stub: ArendDefClassStub, sink: IndexSink) = sink.indexClass(stub)
    }
}

class ArendClassFieldStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendClassField>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendClassFieldStub, ArendClassField>("CLASS_FIELD") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendClassFieldStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendClassFieldStub) = ArendClassField(stub, this)

        override fun indexStub(stub: ArendClassFieldStub, sink: IndexSink) = sink.indexClassField(stub)
    }
}

class ArendClassFieldParamStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendFieldDefIdentifier>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendClassFieldParamStub, ArendFieldDefIdentifier>("FIELD_DEF_IDENTIFIER") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendClassFieldParamStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendClassFieldParamStub) = ArendFieldDefIdentifier(stub, this)

        override fun indexStub(stub: ArendClassFieldParamStub, sink: IndexSink) = sink.indexClassFieldParam(stub)
    }
}

class ArendClassImplementStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendClassImplement>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendClassImplementStub, ArendClassImplement>("CLASS_IMPLEMENT") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendClassImplementStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendClassImplementStub) = ArendClassImplement(stub, this)

        override fun indexStub(stub: ArendClassImplementStub, sink: IndexSink) = sink.indexClassImplement(stub)
    }
}

class ArendCoClauseDefStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendCoClauseDef>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendCoClauseDefStub, ArendCoClauseDef>("CO_CLAUSE_DEF") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendCoClauseDefStub(parentStub, this, name, prec, aliasName)

        override fun createStub(psi: ArendCoClauseDef, parentStub: StubElement<*>?) =
            createStub(parentStub, psi.name, (psi as? ArendCoClauseDef)?.parentCoClause?.prec?.let { ReferableBase.calcPrecedence(it) }, (psi as? GlobalReferable)?.aliasName)

        override fun createPsi(stub: ArendCoClauseDefStub) = ArendCoClauseDef(stub, this)

        override fun indexStub(stub: ArendCoClauseDefStub, sink: IndexSink) = sink.indexCoClauseDef(stub)
    }
}

class ArendDefInstanceStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendDefInstance>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendDefInstanceStub, ArendDefInstance>("DEF_INSTANCE") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendDefInstanceStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendDefInstanceStub) = ArendDefInstance(stub, this)

        override fun indexStub(stub: ArendDefInstanceStub, sink: IndexSink) = sink.indexClassInstance(stub)
    }
}

class ArendConstructorStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendConstructor>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendConstructorStub, ArendConstructor>("CONSTRUCTOR") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendConstructorStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendConstructorStub) = ArendConstructor(stub, this)

        override fun indexStub(stub: ArendConstructorStub, sink: IndexSink) = sink.indexConstructor(stub)
    }
}


class ArendDefDataStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendDefData>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendDefDataStub, ArendDefData>("DEF_DATA") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendDefDataStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendDefDataStub) = ArendDefData(stub, this)

        override fun indexStub(stub: ArendDefDataStub, sink: IndexSink) = sink.indexData(stub)
    }
}

class ArendDefFunctionStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendDefFunction>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendDefFunctionStub, ArendDefFunction>("DEF_FUNCTION") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendDefFunctionStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendDefFunctionStub) = ArendDefFunction(stub, this)

        override fun indexStub(stub: ArendDefFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}

class ArendDefMetaStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?, aliasName: String?)
    : ArendStub<ArendDefMeta>(parent, elementType, name, aliasName) {

    object Type : ArendStubElementType<ArendDefMetaStub, ArendDefMeta>("DEF_META") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendDefMetaStub(parentStub, this, name, prec, aliasName)

        override fun createPsi(stub: ArendDefMetaStub) = ArendDefMeta(stub, this)

        override fun indexStub(stub: ArendDefMetaStub, sink: IndexSink) = sink.indexMeta(stub)
    }
}

class ArendDefModuleStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val aliasName: String?)
    : ArendStub<ArendDefModule>(parent, elementType, name, aliasName) {

    override val precedence: Precedence?
        get() = null

    object Type : ArendStubElementType<ArendDefModuleStub, ArendDefModule>("DEF_MODULE") {
        override fun serialize(stub: ArendDefModuleStub, dataStream: StubOutputStream) {
            dataStream.writeName(stub.name)
            dataStream.writeName(stub.aliasName)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            ArendDefModuleStub(parentStub, this, dataStream.readNameString(), dataStream.readNameString())

        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?) =
            ArendDefModuleStub(parentStub, this, name, aliasName)

        override fun createPsi(stub: ArendDefModuleStub) = ArendDefModule(stub, this)

        override fun indexStub(stub: ArendDefModuleStub, sink: IndexSink) = sink.indexModule(stub)
    }
}