package org.arend.module.orderRoot

import com.intellij.openapi.roots.OrderRootType


class ArendConfigOrderRootType : OrderRootType("AREND_CONFIG") {
    companion object {
        @JvmField
        val INSTANCE = ArendConfigOrderRootType()
    }
}
