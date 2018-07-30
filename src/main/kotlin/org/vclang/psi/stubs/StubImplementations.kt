package org.vclang.psi.stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import org.vclang.VcLanguage
import org.vclang.psi.*
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.impl.*

class VcFileStub(file: VcFile?) : PsiFileStubImpl<VcFile>(file) {

    override fun getType(): Type = Type

    object Type : IStubFileElementType<VcFileStub>(VcLanguage.INSTANCE) {

        override fun getStubVersion(): Int = 1

        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> =
                    VcFileStub(file as VcFile)
        }

        override fun serialize(stub: VcFileStub, dataStream: StubOutputStream) {
        }

        override fun deserialize(
                dataStream: StubInputStream,
                parentStub: StubElement<*>?
        ): VcFileStub = VcFileStub(null)

        override fun getExternalId(): String = "Vclang.file"
    }
}

fun factory(name: String): VcStubElementType<*, *> = when (name) {
    "DEF_CLASS" -> VcDefClassStub.Type
    "CLASS_FIELD" -> VcClassFieldStub.Type
    "FIELD_DEF_IDENTIFIER" -> VcClassFieldParamStub.Type
    "CLASS_FIELD_SYN" -> VcClassFieldSynStub.Type
    "CLASS_IMPLEMENT" -> VcClassImplementStub.Type
    "DEF_INSTANCE" -> VcDefInstanceStub.Type
    "CONSTRUCTOR" -> VcConstructorStub.Type
    "DEF_DATA" -> VcDefDataStub.Type
    "DEF_FUNCTION" -> VcDefFunctionStub.Type
    "DEF_MODULE" -> VcDefModuleStub.Type
    else -> error("Unknown element $name")
}

abstract class VcStub<T : VcCompositeElement>(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        override val name: String?
) : StubBase<T>(parent, elementType), VcNamedStub

class VcDefClassStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcDefClass>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefClassStub, VcDefClass>("DEF_CLASS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcDefClassStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefClassStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefClassStub): VcDefClass = VcDefClassImpl(stub, this)

        override fun createStub(psi: VcDefClass, parentStub: StubElement<*>?): VcDefClassStub =
                VcDefClassStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: VcDefClassStub, sink: IndexSink) = sink.indexClass(stub)
    }
}

class VcClassFieldStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcClassField>(parent, elementType, name) {

    object Type : VcStubElementType<VcClassFieldStub, VcClassField>("CLASS_FIELD") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcClassFieldStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcClassFieldStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcClassFieldStub): VcClassField = VcClassFieldImpl(stub, this)

        override fun createStub(psi: VcClassField, parentStub: StubElement<*>?): VcClassFieldStub =
                VcClassFieldStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: VcClassFieldStub, sink: IndexSink) = sink.indexClassField(stub)
    }
}

class VcClassFieldParamStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcStub<VcFieldDefIdentifier>(parent, elementType, name) {

    object Type : VcStubElementType<VcClassFieldParamStub, VcFieldDefIdentifier>("FIELD_DEF_IDENTIFIER") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcClassFieldParamStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcClassFieldParamStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcClassFieldParamStub): VcFieldDefIdentifier = VcFieldDefIdentifierImpl(stub, this)

        override fun createStub(psi: VcFieldDefIdentifier, parentStub: StubElement<*>?): VcClassFieldParamStub =
            VcClassFieldParamStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: VcClassFieldParamStub, sink: IndexSink) = sink.indexClassFieldParam(stub)
    }
}

class VcClassFieldSynStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcClassFieldSyn>(parent, elementType, name) {

    object Type : VcStubElementType<VcClassFieldSynStub, VcClassFieldSyn>("CLASS_FIELD_SYN") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcClassFieldSynStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcClassFieldSynStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcClassFieldSynStub): VcClassFieldSyn = VcClassFieldSynImpl(stub, this)

        override fun createStub(psi: VcClassFieldSyn, parentStub: StubElement<*>?): VcClassFieldSynStub =
                VcClassFieldSynStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: VcClassFieldSynStub, sink: IndexSink) = sink.indexClassFieldSyn(stub)
    }
}

class VcClassImplementStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcClassImplement>(parent, elementType, name) {

    object Type : VcStubElementType<VcClassImplementStub, VcClassImplement>("CLASS_IMPLEMENT") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcClassImplementStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcClassImplementStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcClassImplementStub): VcClassImplement =
                VcClassImplementImpl(stub, this)

        override fun createStub(
                psi: VcClassImplement,
                parentStub: StubElement<*>?
        ): VcClassImplementStub = VcClassImplementStub(parentStub, this, psi.longName.refIdentifierList.lastOrNull()?.referenceName)

        override fun indexStub(stub: VcClassImplementStub, sink: IndexSink) =
                sink.indexClassImplement(stub)
    }
}

class VcDefInstanceStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcDefInstance>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefInstanceStub, VcDefInstance>("DEF_INSTANCE") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcDefInstanceStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefInstanceStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefInstanceStub): VcDefInstance =
                VcDefInstanceImpl(stub, this)

        override fun createStub(
                psi: VcDefInstance,
                parentStub: StubElement<*>?
        ): VcDefInstanceStub = VcDefInstanceStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: VcDefInstanceStub, sink: IndexSink) =
                sink.indexClassInstance(stub)
    }
}

class VcConstructorStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcConstructor>(parent, elementType, name) {

    object Type : VcStubElementType<VcConstructorStub, VcConstructor>("CONSTRUCTOR") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcConstructorStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcConstructorStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcConstructorStub): VcConstructor =
                VcConstructorImpl(stub, this)

        override fun createStub(
                psi: VcConstructor,
                parentStub: StubElement<*>?
        ): VcConstructorStub = VcConstructorStub(parentStub, this, psi.textRepresentation())

        override fun indexStub(stub: VcConstructorStub, sink: IndexSink) =
                sink.indexConstructor(stub)
    }
}


class VcDefDataStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcDefData>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefDataStub, VcDefData>("DEF_DATA") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcDefDataStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefDataStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefDataStub): VcDefData = VcDefDataImpl(stub, this)

        override fun createStub(
                psi: VcDefData,
                parentStub: StubElement<*>?
        ): VcDefDataStub = VcDefDataStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefDataStub, sink: IndexSink) = sink.indexData(stub)
    }
}

class VcDefFunctionStub(
        parent: StubElement<*>?,
        elementType: IStubElementType<*, *>,
        name: String?
) : VcStub<VcDefFunction>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefFunctionStub, VcDefFunction>("DEF_FUNCTION") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
                VcDefFunctionStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefFunctionStub, dataStream: StubOutputStream) =
                with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefFunctionStub): VcDefFunction =
                VcDefFunctionImpl(stub, this)

        override fun createStub(
                psi: VcDefFunction,
                parentStub: StubElement<*>?
        ): VcDefFunctionStub = VcDefFunctionStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}

class VcDefModuleStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcStub<VcDefModule>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefModuleStub, VcDefModule>("DEF_MODULE") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcDefModuleStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefModuleStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefModuleStub): VcDefModule =
            VcDefModuleImpl(stub, this)

        override fun createStub(
            psi: VcDefModule,
            parentStub: StubElement<*>?
        ): VcDefModuleStub = VcDefModuleStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefModuleStub, sink: IndexSink) = sink.indexModule(stub)
    }
}
