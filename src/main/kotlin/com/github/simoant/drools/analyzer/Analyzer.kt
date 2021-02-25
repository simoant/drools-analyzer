package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.AnalyzerResponse
import com.github.simoant.drools.analyzer.utils.log
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.slf4j.MDCContext
import org.kie.api.logger.KieRuntimeLogger
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import org.slf4j.MDC
import reactor.core.publisher.Mono


typealias KieRuntimeLoggerFactory =
    ((session: KieSession, request: AnalyzerRequest) -> KieRuntimeLogger?)

class Analyzer(private val kieContainer: KieContainer,
               private val requestProcessor: IDataRequestProcessor,
               private val auditLogFactory: KieRuntimeLoggerFactory? = null,
               private val profile: Boolean = false,
               private val mdcContextReactorKey: String = "MDC_CONTEXT_REACTOR_KEY"
) {
    private val MAX_RULES: Int = 100

    fun run(request: AnalyzerRequest, onComplete: (ctx: Context) -> Any? = {}): Mono<AnalyzerResponse> {

        val ctx = Context(request, kieContainer, auditLogFactory, profile)

        val contextMap = MDC.getCopyOfContextMap()

        return mono(MDCContext()) {
            try {
                while (!ctx.ready) {

                    ctx.logWithIndent("Iteration #${ctx.iterationCount}", indents = 0)

                    if (!ctx.ready) {
                        if (ctx.countFired == 0) {
                            if (ctx.isAggrPhase()) {
                                ctx.aggrPhaseOff()
                                ctx.log(" Switch to Normal Phase")
                            } else {
                                ctx.aggrPhaseOn()
                                ctx.log(" Switch to Aggregation Phase")
                            }
                        } else {
                            if (ctx.isAggrPhase()) {
                                ctx.aggrPhaseOff()
                            }
                        }
                    }

                    ctx.fireAllRules(MAX_RULES, request.agendaFilter)

                    when (requestProcessor) {
                        is IDataRequestProcessor.IDataRequestProcessorFuture ->
                            ctx.getAllDataFuture(requestProcessor)
                        is IDataRequestProcessor.IDataRequestProcessorReactive ->
                            ctx.getAllDataReactive(requestProcessor)
                        else -> log.error("Unsupported request processor: $requestProcessor")
                    }
                }

                onComplete(ctx)

                val res = ctx.getResponse()
                res
            } catch (t: Throwable) {
                log.error("Error during drools analyzer execution", t)
                null
            } finally {
                ctx.close()
            }
        }
            .contextWrite { it.put(mdcContextReactorKey, contextMap) }
    }
}