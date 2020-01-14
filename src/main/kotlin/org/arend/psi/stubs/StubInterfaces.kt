package org.arend.psi.stubs

import org.arend.ext.reference.Precedence

interface ArendNamedStub {
    val name: String?
    val precedence: Precedence?
}
