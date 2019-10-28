package com.github.simoant.drools.analyzer.utils

import com.github.simoant.drools.analyzer.MAX_LOG_LIST_SIZE
import org.slf4j.LoggerFactory
import java.util.*

val Any.log
    get() = LoggerFactory.getLogger(this.javaClass)

fun listToIndentedString(list: List<Any?>, indents: Int = 0): String {
    val indent = " ".repeat(indents)
    return list
        .groupBy { it?.javaClass ?: "null" }
        .flatMap {
            if (it.value.size > MAX_LOG_LIST_SIZE)
                listOf(it.value)
            else
                it.value
        }
        .joinToString(separator = "\n" + indent) {
            if (it is Collection<*>) {
                val size = it.size
                it.firstOrNull()
                    ?.let { "${size} ${it.javaClass.simpleName}'s" }
                    ?: ""
            } else it.toString()
        }
        .let { indent + if (it.isNotEmpty()) it else "<empty>" }
}

class Logger() {
    val DEFAULT_INDENTS: Int = 3
    var logStrBuffer: String = ""
    var logObjBuffer: List<Any> = LinkedList()
    val startTime = System.currentTimeMillis()

    fun log(msg: String, vararg args: Any) {
        logWithIndent(msg, DEFAULT_INDENTS, *args)
    }

    fun logWithIndent(msg: String, indents: Int, vararg args: Any) {
        val list = args.toList().plus(System.currentTimeMillis() - startTime)

        logStrBuffer += " ".repeat(indents) + msg + " ts:{}" + "\n"
        logObjBuffer = logObjBuffer.plus(list.map { it.toString() })
    }

    fun flushLog(prefix: String, vararg args: Any) {
        val list = (args.map { it.toString() }.plus(logObjBuffer)).toTypedArray()
        log.debug(prefix + "\n" + logStrBuffer, *list)
        logStrBuffer = ""
        logObjBuffer = LinkedList()
    }


}