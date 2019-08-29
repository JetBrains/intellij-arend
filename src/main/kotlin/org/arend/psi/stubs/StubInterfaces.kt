package org.arend.psi.stubs

import org.arend.term.Precedence

interface ArendNamedStub {
    val name: String?
    val precedence: Precedence?
}
