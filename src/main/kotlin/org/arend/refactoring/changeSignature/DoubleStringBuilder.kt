package org.arend.refactoring.changeSignature

class DoubleStringBuilder {
    val defaultBuilder = StringBuilder()
    val alternativeBuilder = StringBuilder()

    fun append(text: String) {
        defaultBuilder.append(text)
        alternativeBuilder.append(text)
    }

    fun append(text: String, alternativeText: String) {
        defaultBuilder.append(text)
        alternativeBuilder.append(alternativeText)
    }
}