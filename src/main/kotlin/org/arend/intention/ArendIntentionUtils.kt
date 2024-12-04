package org.arend.intention

fun checkNotGeneratePreview(): Boolean {
    return Thread.currentThread().stackTrace.find {
        it.methodName == "generatePreview" &&
        it.className == "com.intellij.codeInsight.intention.IntentionAction"
    } == null
}
