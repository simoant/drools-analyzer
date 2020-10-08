package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.AnalyzerResponse
import com.github.simoant.drools.analyzer.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.slf4j.MDCContext
import mu.withLoggingContext
import org.kie.api.logger.KieRuntimeLogger
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import java.util.*
import java.util.concurrent.CompletableFuture


typealias KieRuntimeLoggerFactory =
    ((session: KieSession, request: AnalyzerRequest) -> KieRuntimeLogger?)

class Analyzer(val kieContainer: KieContainer,
               val requestProcessor: IDataRequestProcessor,
               val auditLogFactory: KieRuntimeLoggerFactory? = null,
               val profile: Boolean = false) {
    private val MAX_RULES: Int = 100

    fun run(request: AnalyzerRequest, trackId: String = UUID.randomUUID().toString(),
            onComplete: (ctx: Context) -> Any? = {}): CompletableFuture<AnalyzerResponse?> {

        val ctx = Context(request, kieContainer, auditLogFactory, profile)

        val res = withLoggingContext(X_UUID_NAME to trackId) {
            CoroutineScope(Dispatchers.Default)
                .future(MDCContext()) {
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
                                    ctx.getAllDataFuture(requestProcessor, trackId)
                                is IDataRequestProcessor.IDataRequestProcessorReactive ->
                                    ctx.getAllDataReactive(requestProcessor, trackId)
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
        }
        return res

    }

}