package com.github.simoant.drools.analyzer.utils

import org.slf4j.LoggerFactory
import java.util.*

val Any.log
    get() = LoggerFactory.getLogger(this.javaClass)

fun listToIndentedString(list: List<Any?>, indents: Int = 0): String {
    val indent = " ".repeat(indents)
    return list.joinToString(separator = "\n" + indent).let { indent + if (it.isNotEmpty()) it else "<empty>" }
}

class Logger() {
    var logStrBuffer: String = ""
    var logObjBuffer: List<Any> = LinkedList()

    fun log(msg: String, vararg args: Any) {
        logWithIndent(msg, 1, *args)
    }

    fun logWithIndent(msg: String, indents: Int, vararg args: Any) {
        logStrBuffer += " ".repeat(indents) + msg + "\n"
        logObjBuffer = logObjBuffer.plus(args.map { it.toString() })
    }

    fun flushLog(prefix: String, vararg args: Any) {
        val list = (args.map { it.toString() }.plus(logObjBuffer)).toTypedArray()
        log.debug(prefix + "\n" + logStrBuffer, *list)
        logStrBuffer = ""
        logObjBuffer = LinkedList()
    }


}