package com.example.gitserver.common.util

import org.slf4j.MDC
import java.util.concurrent.Executor
import kotlin.collections.iterator

object LogContext {
    fun wrap(task: Runnable): Runnable {
        val contextMap = MDC.getCopyOfContextMap()
        return Runnable {
            val previous = MDC.getCopyOfContextMap()
            if (contextMap == null) {
                MDC.clear()
            } else {
                MDC.setContextMap(contextMap)
            }
            try {
                task.run()
            } finally {
                if (previous == null) {
                    MDC.clear()
                } else {
                    MDC.setContextMap(previous)
                }
            }
        }
    }

    inline fun <T> with(vararg entries: Pair<String, String?>, block: () -> T): T {
        val previous = mutableMapOf<String, String?>()
        for ((key, value) in entries) {
            previous[key] = MDC.get(key)
            if (value == null) {
                MDC.remove(key)
            } else {
                MDC.put(key, value)
            }
        }

        try {
            return block()
        } finally {
            for ((key, value) in previous) {
                if (value == null) {
                    MDC.remove(key)
                } else {
                    MDC.put(key, value)
                }
            }
        }
    }

    fun wrappingExecutor(delegate: Executor): Executor {
        return Executor { task ->
            delegate.execute(wrap(task))
        }
    }
}