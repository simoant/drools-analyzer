package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.AnalyzerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.slf4j.MDCContext
import mu.withLoggingContext
import org.kie.api.runtime.KieContainer
import java.util.*
import java.util.concurrent.CompletableFuture


class Analyzer(val kieContainer: KieContainer, val requestProcessor: IDataRequestProcessor) {
    private val MAX_RULES: Int = 100;

    fun run(request: AnalyzerRequest, trackId: String = UUID.randomUUID().toString(),
            onComplete: (ctx: Context) -> Any? = {}): CompletableFuture<AnalyzerResponse?> {
        val ctx = Context(request, kieContainer)

        val res = withLoggingContext(X_UUID_NAME to trackId) {
            CoroutineScope(Dispatchers.Default)
                .future(MDCContext()) {
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

                        ctx.fireAllRules(MAX_RULES)
                        //val countFired = session.fireAllRules(MAX_RULES)


                        ctx.getAllData()
                        { dataRequest ->
                            withLoggingContext(X_UUID_NAME to trackId) {
                                    requestProcessor.executeAsync(dataRequest, trackId)
                            }
                        }
                    }

                    onComplete(ctx)
                    ctx.flashLog()
                    ctx.getResponse()
                }
        }
        return res

    }

}