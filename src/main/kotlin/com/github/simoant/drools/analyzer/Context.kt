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
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.rule.FactHandle
import java.util.concurrent.CompletableFuture


data class Context(val request: AnalyzerRequest, val kieContainer: KieContainer) {

    val kieSession: KieSession

    var aggrPhase: Boolean = false
    var aggrPhaseHandle: FactHandle? = null
    var countFired = -1
    var factHandles: Collection<FactHandle> = listOf()
    var dataRequests: List<DataRequest> = listOf()
    var markers: List<Any> = listOf()
    var prevResponses: List<DataResponse> = listOf()
    var iterationCount = 0

    val logger = Logger()

    init {
        this.kieSession = kieContainer.newKieSession(request.sessionName)
        kieSession.setGlobal("ctx", this)
        request.input.forEach { kieSession.insert(it) }
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
        factHandles = kieSession.getFactHandles<FactHandle> { true }
        dataRequests = factHandles
            .mapNotNull { kieSession.getObject(it) as? DataRequest }

        markers = factHandles
            .mapNotNull { kieSession.getObject(it) as? InjectMarker }
            .map { it.body }
//        log.trace("Analyzer#run(): rules fired: $countFired when executing drools request $request")
//
//        log.trace("Analyzer#run(): data requests received after drools execution: $ctx.dataRequests")
        logIterationResult()
        return countFired

    }

    suspend fun getAllData(dataProvider: (DataRequest) -> CompletableFuture<DataResponse>): List<DataResponse> {

        val reqResp = dataRequests
            .map { request -> DataRequestDefferedResponse(request, dataProvider(request).asDeferred()) }

        withContext(Dispatchers.Default + MDCContext()) {
            reqResp.map { it.deferredResponse }
                .awaitAll()
        }

        val requestsResponses = reqResp.map {
            val exception = it.deferredResponse.getCompletionExceptionOrNull()
            val response = it.deferredResponse.getCompleted().data
            if (exception != null) {
                val msg = "Exception occured when processing request ${it.request}, Exception: $exception"
                if (it.request.required) {
                    flashLog()
                    throw DataMissingException(msg)
                } else {
                    log.debug(msg)
                    return@map DataRequestResponse(it.request, DataResponse(null))
                }
            }
            if (response == null) {
                val msg = "No data received when processing request ${it.request}"
                if (it.request.required) {
                    flashLog()
                    throw DataMissingException(msg)
                } else {
                    log.debug(msg)
                    return@map DataRequestResponse(it.request, DataResponse(response))
                }

            }
            return@map DataRequestResponse(it.request, DataResponse(response))
        }


        factHandles
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
        logger.flushLog("\nDROOLS:${request.sessionName}")
    }


    private fun logIterationResult() {
        val initialRequest = if (iterationCount == 1) request.input else listOf()
        val inputData =
            prevResponses.map { it.data } + initialRequest + markers
        val prevRespDataString =
            listToIndentedString(inputData, 5)
        val dataResponsesString =
            listToIndentedString(dataRequests, 5)

        val factHandles =
            factHandles
                .map { kieSession.getObject(it)}
                .filter {it is IDroolsDecision }
                .let { listToIndentedString(it, 5) }
//                .joinToString(",")
//                .let { listToIndentedString(listOf(it), 5) }

        logger.log("-Input data:\n{}", prevRespDataString)
        logger.log("-Fact handles:\n{}", factHandles)
        logger.log("-Output data requests:\n{}", dataResponsesString)
        logger.log("-Rules fired:{}", countFired)
    }


}