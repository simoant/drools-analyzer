package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.slf4j.MDCContext
import org.drools.core.impl.AbstractRuntime
import org.kie.api.logger.KieRuntimeLogger
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.rule.AgendaFilter
import org.kie.api.runtime.rule.FactHandle
import reactor.core.publisher.Mono


data class DroolsRunner(val request: AnalyzerRequest,
                        val kieSession: KieSession,
                        val auditLoggerFactory: ((session: KieSession, request: AnalyzerRequest) -> KieRuntimeLogger?)? = null,
                        val logger: Logger? = null) {

    private val startTime: Long

    init {
        if (auditLoggerFactory != null && request.id != null)
            (kieSession as AbstractRuntime).logger = auditLoggerFactory.invoke(kieSession, request)

        pushToDrools(request.input)
        startTime = System.currentTimeMillis()
    }

    fun runIteration(iterationNumber: Int, maxRules: Int, agendaFilter: AgendaFilter? = null): IterationResult {

        val countFired = kieSession.fireAllRules(agendaFilter, maxRules)
        val factObjects = kieSession.getFactHandles<FactHandle>({ true })
            .map { kieSession.getObject(it) }

        return IterationResult(iterationNumber, countFired, factObjects)

    }


    suspend fun getAllDataReactive(dataProvider: IDataRequestProcessor.IDataRequestProcessorReactive,
                                   prevIterResult: IterationResult): List<DataRequestResponse> {


        return getAllDataInternal(prevIterResult) {
            val startTime = System.currentTimeMillis()
            val res = prevIterResult.dataRequests
                .map { request ->
                    async(MDCContext()) {
                        dataProvider.executeAsync(request)
                            .map { data ->
                                val et = System.currentTimeMillis() - startTime
                                DataRequestResponse(request, data, null, et)
                            }
                            .onErrorResume { t: Throwable ->
                                val et = System.currentTimeMillis() - startTime
                                Mono.just(DataRequestResponse(request, null, t, et))
                            }
                            .switchIfEmpty(Mono.defer {
                                val et = System.currentTimeMillis() - startTime
                                Mono.just(DataRequestResponse(request, null, null, et))
                            })
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

    private suspend fun getAllDataInternal(prevIterResult: IterationResult,
                                           rawDataSupplier: suspend CoroutineScope.() -> List<DataRequestResponse>)
        : List<DataRequestResponse> = coroutineScope {

        val responseList = rawDataSupplier()

        removeDataRequestsFromDrools(kieSession)
        pushToDrools(extractResponseData(responseList))
        pushToDrools(prevIterResult.markers)

        responseList
    }

//    private fun prepareDataList(reqResp: List<DataRequestRawResponse>): List<DataRequestRawResponse> {
//        return reqResp.map {
//            prepareResponseData(it)
//        }
//    }

//    private fun prepareResponseData(rr: DataRequestRawResponse): DataRequestRawResponse {
//        if (rr.error != null) {
//            val msg = "Exception occured when processing request ${rr.request}, Exception: ${rr.error}"
//            if (rr.request.required) {
////                flashLog()
//                log.error(msg, rr.error)
////                throw DataMissingException(msg)
//            } else {
//                log.warn(msg, rr.error)
//                return rr
//            }
//        } else if (rr.data == null) {
//            val msg = "No data received when processing request $rr.dataRequest"
//            if (rr.request.required) {
//
//                log.error(msg)
////                throw DataMissingException(msg)
//            } else {
//                log.warn(msg)
//                return DataRequestResponse(dataRequest, DataResponse(dataRequest.uri, data, et))
//            }
//
//        }
//        return DataRequestResponse(dataRequest, DataResponse(dataRequest.uri, data, et))
//    }

    private fun extractResponseData(requestsResponses: List<DataRequestResponse>): List<Any> {
        return requestsResponses
            .filter { it.data != null }
            .flatMap {
                val data = it.data
                if (data is List<*> && it.request.splitList)
                    data
                else
                    listOf(data)
            }.filterNotNull()
    }

    private fun pushToDrools(objects: List<Any>) {
        objects.forEach { kieSession.insert(it) }
    }

}