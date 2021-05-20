package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.PhaseManager.Phases.*
import com.github.simoant.drools.analyzer.model.*
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.slf4j.MDCContext
import org.kie.api.logger.KieRuntimeLogger
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.rule.FactHandle
import reactor.core.publisher.Mono


typealias KieRuntimeLoggerFactory =
    ((session: KieSession, request: AnalyzerRequest) -> KieRuntimeLogger?)

val LOG = "logger"

class Analyzer(private val kieContainer: KieContainer,
               private val requestProcessor: IDataRequestProcessor,
               private val auditLogFactory: KieRuntimeLoggerFactory? = null,
               private val maxRules: Int = 100) {

    fun run(request: AnalyzerRequest): Mono<AnalyzerResponse> {

//        val kieSession = kieContainer.getKieBase(request.sessionName).newKieSession()
        val kieSession = kieContainer.newKieSession(request.sessionName)


        val runner = DroolsRunner(request, kieSession, auditLogFactory)
        val logger = Logger()
        kieSession.setGlobal(LOG, logger)

        //  Internal State
        var iterationNumber = 0
        var prevResult = IterationResult(iterationNumber, 0, listOf())
        var prevDataResponses = listOf<DataRequestResponse>()
        val phaseManager = PhaseManager(kieSession)
        var error = false


        fun finished() = (prevResult.countFiredRules == 0 && phaseManager.currPhase == aggregation) || error

        return mono(MDCContext()) {
            val startTime = System.currentTimeMillis()
            try {
                while (!finished()) {
                    iterationNumber++
                    logger.logWithIndent("Iteration #${iterationNumber}", indents = 0)

                    if (!finished()) {
                        if (prevResult.countFiredRules == 0) {
                            if (phaseManager.currPhase == aggregation) {
                                phaseManager.set(normal)
                                logger.log(" Switch to Normal Phase")
                            } else {
                                phaseManager.set(aggregation)
                                logger.log(" Switch to Aggregation Phase")
                            }
                        } else {
                            if (phaseManager.currPhase == aggregation) {
                                phaseManager.set(normal)
                                logger.log(" Switch to Aggregation Phase")
                            }
                        }
                    }


                    prevResult = runner.runIteration(iterationNumber, maxRules, request.agendaFilter)
                        .also { logger.logIterationResult(request, it, prevResult, prevDataResponses) }

                    prevDataResponses = when (requestProcessor) {
                        is IDataRequestProcessor.IDataRequestProcessorReactive ->
                            runner.getAllDataReactive(requestProcessor, prevResult)
                        else -> listOf<DataRequestResponse>()
                            .also { log.error("Analyzer#run(): Unsupported request processor: $requestProcessor") }
                    }
                    prevDataResponses.filter { it.data == null && it.request.required }
                        .map {
                            log.error("Analyzer#run(): No data received for data request% ${it.request}")
                            error = true
                        }
                }

                val res = AnalyzerResponse(prevResult)
                    .also { logger.log("Analyzer Result: ${it.data}") }
                res
            } catch (t: Throwable) {
                log.error("Error during drools analyzer execution", t)
                null
            } finally {
                logger.log("Drools Execution Time: ${System.currentTimeMillis() - startTime} ms")
                logger.flushLog("\nDROOLS:${request.sessionName}")
                kieSession.dispose()
            }
        }
    }


}

class PhaseManager(val kieSession: KieSession) {
    enum class Phases { normal, aggregation }

    var currPhase = normal
    var aggrPhaseHandle: FactHandle? = null

    fun set(phase: Phases) {
        when (phase) {
            aggregation -> aggrPhaseHandle = kieSession.insert(AggregationPhase())
            normal -> {
                if (aggrPhaseHandle != null && currPhase == aggregation) {
                    kieSession.delete(aggrPhaseHandle)
                    currPhase = normal
                } else {
                    log.error("PhaseManager#set(): aggr phase is off: handle: $aggrPhaseHandle, " +
                        "phase: $phase, currPhase: $currPhase")
                }

            }
        }

        currPhase = phase
    }

}