package org.arend.psi.stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import org.arend.ArendFileType
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.impl.*
import org.arend.term.Precedence

class ArendFileStub(file: ArendFile?, override val name: String?) : PsiFileStubImpl<ArendFile>(file), ArendNamedStub {
    constructor (file: ArendFile?) : this(file, file?.name?.removeSuffix('.' + ArendFileType.defaultExtension))

    override val precedence: Precedence
        get() = Precedence.DEFAULT

    override fun getType(): Type = Type

    object Type : IStubFileElementType<ArendFileStub>(ArendLanguage.INSTANCE) {
        override fun getStubVersion() = 2

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
    "DEF_INSTANCE" -> ArendDefInstanceStub.Type
    "CONSTRUCTOR" -> ArendConstructorStub.Type
    "DEF_DATA" -> ArendDefDataStub.Type
    "DEF_FUNCTION" -> ArendDefFunctionStub.Type
    "DEF_MODULE" -> ArendDefModuleStub.Type
    else -> error("Unknown anchor $name")
}

abstract class ArendStub<T : PsiLocatedReferable>(parent: StubElement<*>?, elementType: IStubElementType<*, *>, override val name: String?)
    : StubBase<T>(parent, elementType), ArendNamedStub

class ArendDefClassStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendDefClass>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefClassStub, ArendDefClass>("DEF_CLASS") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendDefClassStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendDefClassStub) = ArendDefClassImpl(stub, this)

        override fun indexStub(stub: ArendDefClassStub, sink: IndexSink) = sink.indexClass(stub)
    }
}

class ArendClassFieldStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendClassField>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendClassFieldStub, ArendClassField>("CLASS_FIELD") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendClassFieldStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendClassFieldStub) = ArendClassFieldImpl(stub, this)

        override fun indexStub(stub: ArendClassFieldStub, sink: IndexSink) = sink.indexClassField(stub)
    }
}

class ArendClassFieldParamStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendFieldDefIdentifier>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendClassFieldParamStub, ArendFieldDefIdentifier>("FIELD_DEF_IDENTIFIER") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendClassFieldParamStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendClassFieldParamStub) = ArendFieldDefIdentifierImpl(stub, this)

        override fun indexStub(stub: ArendClassFieldParamStub, sink: IndexSink) = sink.indexClassFieldParam(stub)
    }
}

class ArendClassImplementStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendClassImplement>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendClassImplementStub, ArendClassImplement>("CLASS_IMPLEMENT") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendClassImplementStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendClassImplementStub) = ArendClassImplementImpl(stub, this)

        override fun indexStub(stub: ArendClassImplementStub, sink: IndexSink) = sink.indexClassImplement(stub)
    }
}

class ArendDefInstanceStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendDefInstance>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefInstanceStub, ArendDefInstance>("DEF_INSTANCE") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendDefInstanceStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendDefInstanceStub) = ArendDefInstanceImpl(stub, this)

        override fun indexStub(stub: ArendDefInstanceStub, sink: IndexSink) = sink.indexClassInstance(stub)
    }
}

class ArendConstructorStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendConstructor>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendConstructorStub, ArendConstructor>("CONSTRUCTOR") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendConstructorStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendConstructorStub) = ArendConstructorImpl(stub, this)

        override fun indexStub(stub: ArendConstructorStub, sink: IndexSink) = sink.indexConstructor(stub)
    }
}


class ArendDefDataStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendDefData>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefDataStub, ArendDefData>("DEF_DATA") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendDefDataStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendDefDataStub) = ArendDefDataImpl(stub, this)

        override fun indexStub(stub: ArendDefDataStub, sink: IndexSink) = sink.indexData(stub)
    }
}

class ArendDefFunctionStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, override val precedence: Precedence?)
    : ArendStub<ArendDefFunction>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefFunctionStub, ArendDefFunction>("DEF_FUNCTION") {
        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendDefFunctionStub(parentStub, this, name, prec)

        override fun createPsi(stub: ArendDefFunctionStub) = ArendDefFunctionImpl(stub, this)

        override fun indexStub(stub: ArendDefFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}

class ArendDefModuleStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : ArendStub<ArendDefModule>(parent, elementType, name) {

    override val precedence: Precedence?
        get() = null

    object Type : ArendStubElementType<ArendDefModuleStub, ArendDefModule>("DEF_MODULE") {
        override fun serialize(stub: ArendDefModuleStub, dataStream: StubOutputStream) {
            dataStream.writeName(stub.name)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            ArendDefModuleStub(parentStub, this, dataStream.readNameString())

        override fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?) =
            ArendDefModuleStub(parentStub, this, name)

        override fun createPsi(stub: ArendDefModuleStub) = ArendDefModuleImpl(stub, this)

        override fun indexStub(stub: ArendDefModuleStub, sink: IndexSink) = sink.indexModule(stub)
    }
}
