package org.arend.psi.stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import org.arend.ArendFileType
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.impl.*

class ArendFileStub(file: ArendFile?, override val name: String?) : PsiFileStubImpl<ArendFile>(file), ArendNamedStub {

    constructor (file: ArendFile?) : this(file, file?.name?.removeSuffix('.' + ArendFileType.defaultExtension))

    override fun getType(): Type = Type

    object Type : IStubFileElementType<ArendFileStub>(ArendLanguage.INSTANCE) {

        override fun getStubVersion(): Int = 1

        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> =
                    ArendFileStub(file as ArendFile)
        }

        override fun serialize(stub: ArendFileStub, dataStream: StubOutputStream) {
        }

        override fun deserialize(
                dataStream: StubInputStream,
                parentStub: StubElement<*>?
        ): ArendFileStub = ArendFileStub(null)

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

abstract class ArendStub<T : ArendCompositeElement>(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        override val name: String?
) : StubBase<T>(parent, elementType), ArendNamedStub

class ArendDefClassStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendDefClass>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefClassStub, ArendDefClass>("DEF_CLASS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendDefClassStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendDefClassStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendDefClassStub): ArendDefClass = ArendDefClassImpl(stub, this)

        override fun createStub(psi: ArendDefClass, parentStub: StubElement<*>?): ArendDefClassStub =
                ArendDefClassStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArendDefClassStub, sink: IndexSink) = sink.indexClass(stub)
    }
}

class ArendClassFieldStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendClassField>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendClassFieldStub, ArendClassField>("CLASS_FIELD") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendClassFieldStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendClassFieldStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendClassFieldStub): ArendClassField = ArendClassFieldImpl(stub, this)

        override fun createStub(psi: ArendClassField, parentStub: StubElement<*>?): ArendClassFieldStub =
                ArendClassFieldStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArendClassFieldStub, sink: IndexSink) = sink.indexClassField(stub)
    }
}

class ArendClassFieldParamStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : ArendStub<ArendFieldDefIdentifier>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendClassFieldParamStub, ArendFieldDefIdentifier>("FIELD_DEF_IDENTIFIER") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            ArendClassFieldParamStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendClassFieldParamStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendClassFieldParamStub): ArendFieldDefIdentifier = ArendFieldDefIdentifierImpl(stub, this)

        override fun createStub(psi: ArendFieldDefIdentifier, parentStub: StubElement<*>?): ArendClassFieldParamStub =
            ArendClassFieldParamStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArendClassFieldParamStub, sink: IndexSink) = sink.indexClassFieldParam(stub)
    }
}

class ArendClassImplementStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendClassImplement>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendClassImplementStub, ArendClassImplement>("CLASS_IMPLEMENT") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendClassImplementStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendClassImplementStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendClassImplementStub): ArendClassImplement =
                ArendClassImplementImpl(stub, this)

        override fun createStub(
                psi: ArendClassImplement,
                parentStub: StubElement<*>?
        ): ArendClassImplementStub = ArendClassImplementStub(parentStub, this, psi.longName.refIdentifierList.lastOrNull()?.referenceName)

        override fun indexStub(stub: ArendClassImplementStub, sink: IndexSink) =
                sink.indexClassImplement(stub)
    }
}

class ArendDefInstanceStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendDefInstance>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefInstanceStub, ArendDefInstance>("DEF_INSTANCE") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendDefInstanceStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendDefInstanceStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendDefInstanceStub): ArendDefInstance =
                ArendDefInstanceImpl(stub, this)

        override fun createStub(
                psi: ArendDefInstance,
                parentStub: StubElement<*>?
        ): ArendDefInstanceStub = ArendDefInstanceStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArendDefInstanceStub, sink: IndexSink) =
                sink.indexClassInstance(stub)
    }
}

class ArendConstructorStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendConstructor>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendConstructorStub, ArendConstructor>("CONSTRUCTOR") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendConstructorStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendConstructorStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendConstructorStub): ArendConstructor =
                ArendConstructorImpl(stub, this)

        override fun createStub(
                psi: ArendConstructor,
                parentStub: StubElement<*>?
        ): ArendConstructorStub = ArendConstructorStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: ArendConstructorStub, sink: IndexSink) =
                sink.indexConstructor(stub)
    }
}


class ArendDefDataStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendDefData>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefDataStub, ArendDefData>("DEF_DATA") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendDefDataStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendDefDataStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendDefDataStub): ArendDefData = ArendDefDataImpl(stub, this)

        override fun createStub(
                psi: ArendDefData,
                parentStub: StubElement<*>?
        ): ArendDefDataStub = ArendDefDataStub(parentStub, this, psi.name)

        override fun indexStub(stub: ArendDefDataStub, sink: IndexSink) = sink.indexData(stub)
    }
}

class ArendDefFunctionStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : ArendStub<ArendDefFunction>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefFunctionStub, ArendDefFunction>("DEF_FUNCTION") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                ArendDefFunctionStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendDefFunctionStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendDefFunctionStub): ArendDefFunction =
                ArendDefFunctionImpl(stub, this)

        override fun createStub(
                psi: ArendDefFunction,
                parentStub: StubElement<*>?
        ): ArendDefFunctionStub = ArendDefFunctionStub(parentStub, this, psi.name)

        override fun indexStub(stub: ArendDefFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}

class ArendDefModuleStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : ArendStub<ArendDefModule>(parent, elementType, name) {

    object Type : ArendStubElementType<ArendDefModuleStub, ArendDefModule>("DEF_MODULE") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            ArendDefModuleStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: ArendDefModuleStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: ArendDefModuleStub): ArendDefModule =
            ArendDefModuleImpl(stub, this)

        override fun createStub(
            psi: ArendDefModule,
            parentStub: StubElement<*>?
        ): ArendDefModuleStub = ArendDefModuleStub(parentStub, this, psi.name)

        override fun indexStub(stub: ArendDefModuleStub, sink: IndexSink) = sink.indexModule(stub)
    }
}
