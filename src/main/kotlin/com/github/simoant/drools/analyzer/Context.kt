package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.*
import com.github.simoant.drools.analyzer.utils.Logger
import com.github.simoant.drools.analyzer.utils.listToIndentedString
import com.github.simoant.drools.analyzer.utils.log
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.slf4j.MDCContext
import org.drools.core.impl.AbstractRuntime
import org.kie.api.logger.KieRuntimeLogger
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.rule.AgendaFilter
import org.kie.api.runtime.rule.FactHandle
import org.slf4j.MDC
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty



data class Context(val request: AnalyzerRequest,
                   val kieContainer: KieContainer,
                   val auditLoggerFactory: ((session: KieSession, request: AnalyzerRequest) -> KieRuntimeLogger?)? = null,
                   val profiling: Boolean = false,
                   val mdcContextReactorKey: String = "MDC_CONTEXT_REACTOR_KEY") {


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
//        this.kieSession = kieContainer.newKieSession(request.sessionName)
        this.kieSession = kieContainer.getKieBase(request.sessionName).newKieSession()
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

    fun profile(msg: String, vararg args: Any) {
        if (profiling)
            log(msg, *args)
    }

    fun logWithIndent(msg: String, indents: Int, vararg args: Any) {
        logger.logWithIndent(msg, indents, *args)
    }

    fun isAggrPhase(): Boolean {
        return aggrPhase
    }

    fun aggrPhaseOn() {
        aggrPhaseHandle = kieSession.insert(AggregationPhase())
        aggrPhase = true
    }

    fun aggrPhaseOff() {
        if (aggrPhaseHandle != null && aggrPhase) {
            kieSession.delete(aggrPhaseHandle)
            aggrPhase = false
        } else {
            log.error("Context#aggrPhaseOff(): aggr phase is off: handle: $aggrPhaseHandle, aggrPhase: $aggrPhase")
        }
    }


    fun fireAllRules(maxRules: Int, agendaFilter: AgendaFilter? = null): Int {
        iterationCount++

        countFired = kieSession.fireAllRules(agendaFilter, maxRules)
        profile("Completed drools execution")
        prevFactObjects = factObjects
        factObjects = kieSession.getFactHandles<FactHandle>({ true })
            .map { kieSession.getObject(it) }

        profile("Retrieved fact objects from kie session")
        dataRequests = factObjects
            .mapNotNull { it as? DataRequest }
        profile("Retrieved data requests objects from kie session")

        markers = factObjects
            .mapNotNull { it as? InjectMarker }
            .map { it.body }

        profile("Retrieved markers from kie session")

        logIterationResult()
        return countFired

    }

    suspend fun getAllDataFuture(dataProvider: IDataRequestProcessor.IDataRequestProcessorFuture) =
        getAllDataInternal() {
            val res = dataRequests
                .map { request ->
                    DataRequestDefferedResponse(request, dataProvider.executeAsync(request).asDeferred())
                }
                .map {
                    val resp = it
                    it.deferredResponse.invokeOnCompletion {
                        resp.et = System.currentTimeMillis() - resp.startTime
                    }
                    resp
                }

            res.map { it.deferredResponse }.awaitAll()
            res.map {
                DataRequestRawResponse(
                    it.request, it.deferredResponse.getCompleted(), it.deferredResponse.getCompletionExceptionOrNull(), it.et)
            }
        }


    suspend fun getAllDataReactive(dataProvider: IDataRequestProcessor.IDataRequestProcessorReactive) {
        val log = this.log
        val copyOfContextMap = MDC.getCopyOfContextMap()

        getAllDataInternal {
            val startTime = System.currentTimeMillis()
            val res = dataRequests
                .map { request ->
                    async(MDCContext()) {
                        dataProvider.executeAsync(request)
                            .map { data ->
                                val et = System.currentTimeMillis() - startTime
                                DataRequestRawResponse(request, data, null, et)
                            }
                            .onErrorResume { t: Throwable ->
                                val et = System.currentTimeMillis() - startTime
                                Mono.just(DataRequestRawResponse(request, null, t, et))
                            }

                            .switchIfEmpty {
                                val et = System.currentTimeMillis() - startTime
                                Mono.just(DataRequestRawResponse(request, null, null, et))

                            }
                            .contextWrite {
                                it.put(mdcContextReactorKey, copyOfContextMap)
                            }
                            .awaitFirst()
                    }

                }.awaitAll()
            res
        }
    }


    private fun removeDataRequestsFromDrools(kieSession: KieSession) {
        kieSession.getFactHandles<FactHandle>({ true })
            .filter { kieSession.getObject(it) is DataRequest }
            .forEach { kieSession.delete(it) }
    }

    private suspend fun getAllDataInternal(rawDataSupplier: suspend CoroutineScope.() -> List<DataRequestRawResponse>)
        : List<DataResponse> {
//        withLoggingContext(X_UUID_NAME to trackId) {


        val rawResponseList = rawDataSupplier
        //withContext(Dispatchers.IO + MDCContext(), rawDataSupplier)

        profile("Received all data")
        val responseList = prepareDataList(coroutineScope { rawResponseList() })

        profile("Validated data")

        removeDataRequestsFromDrools(kieSession)

        profile("Cleared kie session of Data Requests")

        prevResponses = responseList.map { it.response }

        pushDataToDrools(responseList, kieSession)

        profile("Inserted new data to kie session")

        return responseList.map { it.response }


//        }
    }

    private fun prepareDataList(reqResp: List<DataRequestRawResponse>): List<DataRequestResponse> {
        return reqResp.map {
            return@map prepareResponseData(it.data, it.error, it.request, it.et)
        }
    }

    private fun prepareResponseData(data: Any?, exception: Throwable?,
                                    dataRequest: DataRequest, et: Long?): DataRequestResponse {
        if (exception != null) {
            val msg = "Exception occured when processing request $dataRequest, Exception: $exception"
            if (dataRequest.required) {
                flashLog()
                log.error(msg, exception)
                throw DataMissingException(msg)
            } else {
//                log.warn(msg)
                log.error(msg, exception)
                return DataRequestResponse(dataRequest, DataResponse(dataRequest.uri, null, et))
            }
        } else if (data == null) {
            val msg = "No data received when processing request $dataRequest"
            if (dataRequest.required) {
                flashLog()
                log.error(msg)
                throw DataMissingException(msg)
            } else {
                log.warn(msg)
                return DataRequestResponse(dataRequest, DataResponse(dataRequest.uri, data, et))
            }

        }
        return DataRequestResponse(dataRequest, DataResponse(dataRequest.uri, data, et))
    }

    private fun pushDataToDrools(requestsResponses: List<DataRequestResponse>, ks: KieSession) {
        requestsResponses.forEach {
            val data = it.response.data
            if (data is List<*> && it.request.splitList)
                data.forEach { ks.insert(it) }
            else
                ks.insert(data)
        }
        markers.forEach { ks.insert(it) }
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

        profile("Retrieved decisions from kie session")

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