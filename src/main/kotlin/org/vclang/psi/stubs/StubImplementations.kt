package org.vclang.psi.stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IStubFileElementType
import org.vclang.VcLanguage
import org.vclang.psi.VcClassField
import org.vclang.psi.VcClassImplement
import org.vclang.psi.VcClassViewField
import org.vclang.psi.VcConstructor
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefClassView
import org.vclang.psi.VcDefData
import org.vclang.psi.VcDefFunction
import org.vclang.psi.VcDefInstance
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.impl.VcClassFieldImpl
import org.vclang.psi.impl.VcClassImplementImpl
import org.vclang.psi.impl.VcClassViewFieldImpl
import org.vclang.psi.impl.VcConstructorImpl
import org.vclang.psi.impl.VcDefClassImpl
import org.vclang.psi.impl.VcDefClassViewImpl
import org.vclang.psi.impl.VcDefDataImpl
import org.vclang.psi.impl.VcDefFunctionImpl
import org.vclang.psi.impl.VcDefInstanceImpl

class VcFileStub(file: VcFile?) : PsiFileStubImpl<VcFile>(file) {

    override fun getType(): Type = Type

    object Type : IStubFileElementType<VcFileStub>(VcLanguage) {

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
    "CLASS_IMPLEMENT" -> VcClassImplementStub.Type
    "DEF_CLASS_VIEW" -> VcDefClassViewStub.Type
    "CLASS_VIEW_FIELD" -> VcClassViewFieldStub.Type
    "DEF_INSTANCE" -> VcDefInstanceStub.Type
    "CONSTRUCTOR" -> VcConstructorStub.Type
    "DEF_DATA" -> VcDefDataStub.Type
    "DEF_FUNCTION" -> VcDefFunctionStub.Type
    else -> error("Unknown element $name")
}

abstract class VcDefinitionStub<DefT>(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<DefT>(parent, elementType), VcNamedStub where DefT : VcDefinition

class VcDefClassStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcDefClass>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefClassStub, VcDefClass>("DEF_CLASS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcDefClassStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefClassStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefClassStub): VcDefClass = VcDefClassImpl(stub, this)

        override fun createStub(psi: VcDefClass, parentStub: StubElement<*>?): VcDefClassStub =
            VcDefClassStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefClassStub, sink: IndexSink) = sink.indexClass(stub)
    }
}

class VcClassFieldStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcClassField>(parent, elementType, name) {

    object Type : VcStubElementType<VcClassFieldStub, VcClassField>("CLASS_FIELD") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcClassFieldStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcClassFieldStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcClassFieldStub): VcClassField = VcClassFieldImpl(stub, this)

        override fun createStub(psi: VcClassField, parentStub: StubElement<*>?): VcClassFieldStub =
            VcClassFieldStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcClassFieldStub, sink: IndexSink) = sink.indexClassField(stub)
    }
}

class VcClassImplementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcClassImplement>(parent, elementType, name) {

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
        ): VcClassImplementStub = VcClassImplementStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcClassImplementStub, sink: IndexSink) =
            sink.indexClassImplement(stub)
    }
}

class VcDefClassViewStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcDefClassView>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefClassViewStub, VcDefClassView>("DEF_CLASS_VIEW") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcDefClassViewStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefClassViewStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefClassViewStub): VcDefClassView =
            VcDefClassViewImpl(stub, this)

        override fun createStub(
            psi: VcDefClassView,
            parentStub: StubElement<*>?
        ): VcDefClassViewStub = VcDefClassViewStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefClassViewStub, sink: IndexSink) =
            sink.indexClassView(stub)
    }
}

class VcClassViewFieldStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcClassViewField>(parent, elementType, name) {

    object Type : VcStubElementType<VcClassViewFieldStub, VcClassViewField>("CLASS_VIEW_FIELD") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcClassViewFieldStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcClassViewFieldStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcClassViewFieldStub): VcClassViewField =
            VcClassViewFieldImpl(stub, this)

        override fun createStub(
            psi: VcClassViewField,
            parentStub: StubElement<*>?
        ): VcClassViewFieldStub = VcClassViewFieldStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcClassViewFieldStub, sink: IndexSink) =
            sink.indexClassViewField(stub)
    }
}

class VcDefInstanceStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcDefInstance>(parent, elementType, name) {

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
        ): VcDefInstanceStub = VcDefInstanceStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefInstanceStub, sink: IndexSink) =
            sink.indexClassViewImplement(stub)
    }
}

class VcConstructorStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcConstructor>(parent, elementType, name) {

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
        ): VcConstructorStub = VcConstructorStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcConstructorStub, sink: IndexSink) =
            sink.indexConstructor(stub)
    }
}


class VcDefDataStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcDefData>(parent, elementType, name) {

    object Type : VcStubElementType<VcDefDataStub, VcDefData>("DEF_DATA") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            VcDefDataStub(parentStub, this, dataStream.readName()?.string)

        override fun serialize(stub: VcDefDataStub, dataStream: StubOutputStream) =
            with(dataStream) { writeName(stub.name) }

        override fun createPsi(stub: VcDefDataStub): VcDefData =
            VcDefDataImpl(stub, this)

        override fun createStub(
            psi: VcDefData,
            parentStub: StubElement<*>?
        ): VcDefDataStub = VcDefDataStub(parentStub, this, psi.name)

        override fun indexStub(stub: VcDefDataStub, sink: IndexSink) =
            sink.indexData(stub)
    }
}

class VcDefFunctionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?
) : VcDefinitionStub<VcDefFunction>(parent, elementType, name) {

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

        override fun indexStub(stub: VcDefFunctionStub, sink: IndexSink) =
            sink.indexFunction(stub)
    }
}
