package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.*
import com.github.simoant.drools.analyzer.utils.Logger
import com.github.simoant.drools.analyzer.utils.listToIndentedString
import com.github.simoant.drools.analyzer.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.drools.core.impl.AbstractRuntime
import org.kie.api.logger.KieRuntimeLogger
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.rule.FactHandle
import java.util.concurrent.CompletableFuture


data class Context(val request: AnalyzerRequest,
                   val kieContainer: KieContainer,
                   val auditLoggerFactory: ((session: KieSession, request: AnalyzerRequest) -> KieRuntimeLogger?)? = null) {



    val kieSession: KieSession

    var aggrPhase: Boolean = false
    var aggrPhaseHandle: FactHandle? = null
    var countFired = -1
    var factObjects: Collection<Any> = listOf()
    var prevFactObjects: Collection<Any> = listOf()
    var prevDecisions: Collection<IDroolsDecision> = listOf()
    var dataRequests: List<DataRequest> = listOf()
    var markers: List<Any> = listOf()
    var prevResponses: List<DataResponse> = listOf()

    var iterationCount = 0

    val logger = Logger()

    private val startTime: Long

    init {
        this.kieSession = kieContainer.newKieSession(request.sessionName)
        kieSession.setGlobal("ctx", this)

        if (auditLoggerFactory != null && request.id != null)
            (kieSession as AbstractRuntime).logger = auditLoggerFactory.invoke(kieSession, request)

        request.input.forEach { kieSession.insert(it) }
        startTime = System.currentTimeMillis()
    }

    val ready: Boolean
        get() = countFired == 0 && isAggrPhase()

    fun log(msg: String, vararg args: Any) {
        logger.log(msg, *args)
    }

    fun logWithIndent(msg: String, indents: Int, vararg args: Any) {

        logger.logWithIndent(msg, indents, *args)
    }

    fun isAggrPhase(): Boolean {
        return aggrPhase
    }

    fun aggrPhaseOn() {
        aggrPhaseHandle = kieSession.insert(AggregationPhase())
        aggrPhase = true;
    }

    fun aggrPhaseOff() {
        if (aggrPhaseHandle != null && aggrPhase) {
            kieSession.delete(aggrPhaseHandle)
            aggrPhase = false
        } else {
            log.error("Context#aggrPhaseOff(): aggr phase is off: handle: $aggrPhaseHandle, aggrPhase: $aggrPhase")
        }
    }


    fun fireAllRules(maxRules: Int): Int {
        iterationCount++
        countFired = kieSession.fireAllRules(maxRules)
        prevFactObjects = factObjects
        factObjects = kieSession.getFactHandles<FactHandle>({ true })
            .map { kieSession.getObject(it) }

        dataRequests = factObjects
            .mapNotNull { it as? DataRequest }

        markers = factObjects
            .mapNotNull { it as? InjectMarker }
            .map { it.body }
//        log.trace("Analyzer#run(): rules fired: $countFired when executing drools request $request")
//
//        log.trace("Analyzer#run(): data requests received after drools execution: $ctx.dataRequests")
        logIterationResult()
        return countFired

    }

    suspend fun getAllData(dataProvider: (DataRequest) -> CompletableFuture<Any?>): List<DataResponse> {

        val reqResp = dataRequests
            .map { request -> DataRequestDefferedResponse(request, dataProvider(request).asDeferred()) }
            .map {
                val resp = it
                it.deferredResponse.invokeOnCompletion {
                    resp.et = System.currentTimeMillis() - resp.startTime
                }
                resp
            }

        withContext(Dispatchers.IO + MDCContext()) {
            reqResp.map { it.deferredResponse }
                .awaitAll()
        }

        val requestsResponses = reqResp.map {
            val exception = it.deferredResponse.getCompletionExceptionOrNull()
            val data = it.deferredResponse.getCompleted()

            if (exception != null) {
                val msg = "Exception occured when processing request ${it.request}, Exception: $exception"
                if (it.request.required) {
                    flashLog()
                    throw DataMissingException(msg)
                } else {
                    log.debug(msg)
                    return@map DataRequestResponse(it.request, DataResponse(it.request.uri, null, it.et))
                }
            }
            if (data == null) {
                val msg = "No data received when processing request ${it.request}"
                if (it.request.required) {
                    flashLog()
                    throw DataMissingException(msg)
                } else {
                    log.debug(msg)
                    return@map DataRequestResponse(it.request, DataResponse(it.request.uri, data, it.et))
                }

            }
            return@map DataRequestResponse(it.request, DataResponse(it.request.uri, data, it.et))
        }


        kieSession.getFactHandles<FactHandle>({ true })
            .filter { kieSession.getObject(it) is DataRequest }
            .forEach { kieSession.delete(it) }

        prevResponses = requestsResponses.map { it.response }

        requestsResponses.forEach {
            val data = it.response.data
            if (data is List<*> && it.request.splitList)
                data.forEach { kieSession.insert(it) }
            else
                kieSession.insert(data)
        }
        markers.forEach { kieSession.insert(it) }

        return requestsResponses.map { it.response }
    }

    fun getResponse(): AnalyzerResponse? {
        val res = kieSession.getFactHandles<FactHandle> { true }
            .mapNotNull { kieSession.getObject(it) as? AnalyzerResponse }.firstOrNull()
            .also { logger.log("Analyzer Result: ${it?.data}") }

        return res

    }

    fun flashLog() {
        logger.log("Drools Execution Time: ${System.currentTimeMillis() - startTime} ms")
        logger.flushLog("\nDROOLS:${request.sessionName}")
    }


    private fun logIterationResult() {
        val initialRequest = if (iterationCount == 1) request.input else listOf()
        val inputData =
            prevResponses.map { it } + initialRequest + markers
        val prevRespDataString =
            listToIndentedString(inputData, logger.DEFAULT_INDENTS * 2)
        val dataRequestsString =
            listToIndentedString(dataRequests, logger.DEFAULT_INDENTS * 2)

        val decisions = factObjects
            .mapNotNull { it as? IDroolsDecision }

        val newDecisionsStr =
            decisions
                .filter { !prevDecisions.contains(it) }
                .let { listToIndentedString(it, logger.DEFAULT_INDENTS * 2) }


        val removedDecisions =
            prevDecisions
                .filter { !decisions.contains(it) }
                .let { listToIndentedString(it, logger.DEFAULT_INDENTS * 2) }

        prevDecisions = decisions

        logger.log("-Input data:\n{}", prevRespDataString)
        logger.log("-New Decisions:\n{}", newDecisionsStr)
        logger.log("-Removed Decisions:\n{}", removedDecisions)
        logger.log("-Data requests:\n{}", dataRequestsString)
        logger.log("-Rules fired:{}", countFired)
    }

    fun close() {
        flashLog()
        kieSession.dispose()
    }


}