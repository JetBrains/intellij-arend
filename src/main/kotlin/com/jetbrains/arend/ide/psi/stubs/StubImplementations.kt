package com.jetbrains.arend.ide.psi.stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ext.ArdCompositeElement
import com.jetbrains.arend.ide.psi.impl.*

class ArdFileStub(file: ArdFile?) : PsiFileStubImpl<ArdFile>(file) {

    override fun getType(): Type = Type

    object Type : IStubFileElementType<ArdFileStub>(com.jetbrains.arend.ide.ArdLanguage.INSTANCE) {

        override fun getStubVersion(): Int = 1

        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> =
                    ArdFileStub(file as ArdFile)
        }

        override fun serialize(stub: ArdFileStub, dataStream: StubOutputStream) {
        }

        override fun deserialize(
                dataStream: StubInputStream,
                parentStub: StubElement<*>?
        ): ArdFileStub = ArdFileStub(null)

        override fun getExternalId(): String = "Arend.file"
    }
}

fun factory(name: String): ArdStubElementType<*, *> = when (name) {
    "DEF_CLASS" -> ArdDefClassStub.Type
    "CLASS_FIELD" -> ArdClassFieldStub.Type
    "FIELD_DEF_IDENTIFIER" -> ArdClassFieldParamStub.Type
    "CLASS_FIELD_SYN" -> ArdClassFieldSynStub.Type
    "CLASS_IMPLEMENT" -> ArdClassImplementStub.Type
    "DEF_INSTANCE" -> ArdDefInstanceStub.Type
    "CONSTRUCTOR" -> ArdConstructorStub.Type
    "DEF_DATA" -> ArdDefDataStub.Type
    "DEF_FUNCTION" -> ArdDefFunctionStub.Type
    "DEF_MODULE" -> ArdDefModuleStub.Type
    else -> error("Unknown element $name")
}

abstract class ArdStub<T : ArdCompositeElement>(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        override val name: String?
) : StubBase<T>(parent, elementType), ArdNamedStub

class ArdDefClassStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdDefClass>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdDefClassStub, ArdDefClass>("DEF_CLASS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdDefClassStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdDefClassStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdDefClassStub): ArdDefClass = ArdDefClassImpl(stub, this)

        override fun createStub(psi: ArdDefClass, parentStub: StubElement<*>?): ArdDefClassStub =
                ArdDefClassStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArdDefClassStub, sink: IndexSink) = sink.indexClass(stub)
    }
}

class ArdClassFieldStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdClassField>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdClassFieldStub, ArdClassField>("CLASS_FIELD") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdClassFieldStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdClassFieldStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdClassFieldStub): ArdClassField = ArdClassFieldImpl(stub, this)

        override fun createStub(psi: ArdClassField, parentStub: StubElement<*>?): ArdClassFieldStub =
                ArdClassFieldStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArdClassFieldStub, sink: IndexSink) = sink.indexClassField(stub)
    }
}

class ArdClassFieldParamStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdFieldDefIdentifier>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdClassFieldParamStub, ArdFieldDefIdentifier>("FIELD_DEF_IDENTIFIER") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdClassFieldParamStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdClassFieldParamStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdClassFieldParamStub): ArdFieldDefIdentifier = ArdFieldDefIdentifierImpl(stub, this)

        override fun createStub(psi: ArdFieldDefIdentifier, parentStub: StubElement<*>?): ArdClassFieldParamStub =
                ArdClassFieldParamStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArdClassFieldParamStub, sink: IndexSink) = sink.indexClassFieldParam(stub)
    }
}

class ArdClassFieldSynStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdClassFieldSyn>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdClassFieldSynStub, ArdClassFieldSyn>("CLASS_FIELD_SYN") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdClassFieldSynStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdClassFieldSynStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdClassFieldSynStub): ArdClassFieldSyn = ArdClassFieldSynImpl(stub, this)

        override fun createStub(psi: ArdClassFieldSyn, parentStub: StubElement<*>?): ArdClassFieldSynStub =
                ArdClassFieldSynStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArdClassFieldSynStub, sink: IndexSink) = sink.indexClassFieldSyn(stub)
    }
}

class ArdClassImplementStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdClassImplement>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdClassImplementStub, ArdClassImplement>("CLASS_IMPLEMENT") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdClassImplementStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdClassImplementStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdClassImplementStub): ArdClassImplement =
                ArdClassImplementImpl(stub, this)

        override fun createStub(
                psi: ArdClassImplement,
                parentStub: StubElement<*>?
        ): ArdClassImplementStub = ArdClassImplementStub(parentStub, this, psi.longName.refIdentifierList.lastOrNull()?.referenceName)

        override fun indexStub(stub: ArdClassImplementStub, sink: IndexSink) =
                sink.indexClassImplement(stub)
    }
}

class ArdDefInstanceStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdDefInstance>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdDefInstanceStub, ArdDefInstance>("DEF_INSTANCE") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdDefInstanceStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdDefInstanceStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdDefInstanceStub): ArdDefInstance =
                ArdDefInstanceImpl(stub, this)

        override fun createStub(
                psi: ArdDefInstance,
                parentStub: StubElement<*>?
        ): ArdDefInstanceStub = ArdDefInstanceStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArdDefInstanceStub, sink: IndexSink) =
                sink.indexClassInstance(stub)
    }
}

class ArdConstructorStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdConstructor>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdConstructorStub, ArdConstructor>("CONSTRUCTOR") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdConstructorStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdConstructorStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdConstructorStub): ArdConstructor =
                ArdConstructorImpl(stub, this)

        override fun createStub(
                psi: ArdConstructor,
                parentStub: StubElement<*>?
        ): ArdConstructorStub = ArdConstructorStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArdConstructorStub, sink: IndexSink) =
                sink.indexConstructor(stub)
    }
}


class ArdDefDataStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdDefData>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdDefDataStub, ArdDefData>("DEF_DATA") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdDefDataStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdDefDataStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdDefDataStub): ArdDefData = ArdDefDataImpl(stub, this)

        override fun createStub(
                psi: ArdDefData,
                parentStub: StubElement<*>?
        ): ArdDefDataStub = ArdDefDataStub(parentStub, this, psi.name)

        override fun indexStub(stub: ArdDefDataStub, sink: IndexSink) = sink.indexData(stub)
    }
}

class ArdDefFunctionStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdDefFunction>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdDefFunctionStub, ArdDefFunction>("DEF_FUNCTION") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdDefFunctionStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdDefFunctionStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdDefFunctionStub): ArdDefFunction =
                ArdDefFunctionImpl(stub, this)

        override fun createStub(
                psi: ArdDefFunction,
                parentStub: StubElement<*>?
        ): ArdDefFunctionStub = ArdDefFunctionStub(parentStub, this, psi.name)

        override fun indexStub(stub: ArdDefFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}

class ArdDefModuleStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArdStub<ArdDefModule>(parent, elementType, name) {

    object Type : ArdStubElementType<ArdDefModuleStub, ArdDefModule>("DEF_MODULE") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArdDefModuleStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArdDefModuleStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArdDefModuleStub): ArdDefModule =
                ArdDefModuleImpl(stub, this)

        override fun createStub(
                psi: ArdDefModule,
                parentStub: StubElement<*>?
        ): ArdDefModuleStub = ArdDefModuleStub(parentStub, this, psi.name)

        override fun indexStub(stub: ArdDefModuleStub, sink: IndexSink) = sink.indexModule(stub)
    }
}
