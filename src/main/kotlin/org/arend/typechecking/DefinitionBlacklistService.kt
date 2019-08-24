package org.arend.typechecking

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import org.arend.editor.ArendOptions
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class DefinitionBlacklistService {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val blackList = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    fun runTimed(any: Any, progress: ProgressIndicator, action: () -> Unit): Boolean {
        var timedOut = false
        val arendOptions = service<ArendOptions>()
        val handler = if (arendOptions.withTimeLimit) {
            scheduler.schedule({
                progress.cancel()
                timedOut = true
            }, arendOptions.typecheckingTimeLimit.toLong(), TimeUnit.SECONDS)
        } else null
        action()
        handler?.cancel(true)
        if (timedOut) {
            blackList.add(any)
        }
        return !timedOut
    }

    fun isBlacklisted(any: Any) = blackList.contains(any)

    fun removeFromBlacklist(any: Any, time: Int) =
        time < service<ArendOptions>().typecheckingTimeLimit && blackList.remove(any)
}