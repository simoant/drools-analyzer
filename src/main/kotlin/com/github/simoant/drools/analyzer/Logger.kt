package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.DataRequestResponse
import com.github.simoant.drools.analyzer.model.IterationResult
import org.slf4j.LoggerFactory
import java.util.*

val Any.log
    get() = LoggerFactory.getLogger(this.javaClass)

const val MAX_LOG_LIST_SIZE = 10

class Logger() {
    val DEFAULT_INDENTS: Int = 3
    var logStrBuffer: String = ""
    var logObjBuffer: List<Any> = LinkedList()
    val startTime = System.currentTimeMillis()

    fun invoke(msg: String, vararg args: Any) = log(msg, args)
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


    fun logIterationResult(request: AnalyzerRequest, result: IterationResult, prevIterResult: IterationResult,
                                  prevDataResponses: List<DataRequestResponse>) {
        val initialRequest = if (result.iterationNumber == 1) request.input else listOf()
        val inputData =
            prevDataResponses + initialRequest + result.markers
        val prevRespDataString =
            listToIndentedString(inputData, DEFAULT_INDENTS * 2)
        val dataRequestsString =
            listToIndentedString(result.dataRequests, DEFAULT_INDENTS * 2)

        val decisions = result.decisions

        val newDecisionsStr =
            decisions
                .filter { !prevIterResult.decisions.contains(it) }
                .let { listToIndentedString(it, DEFAULT_INDENTS * 2) }

        val removedDecisions =
            prevIterResult.decisions
                .filter { !decisions.contains(it) }
                .let { listToIndentedString(it, DEFAULT_INDENTS * 2) }


        log("-Input data:\n{}", prevRespDataString)
        log("-New Decisions:\n{}", newDecisionsStr)
        log("-Removed Decisions:\n{}", removedDecisions)
        log("-Data requests:\n{}", dataRequestsString)
        log("-Rules fired:{}", result.countFiredRules)
    }



}