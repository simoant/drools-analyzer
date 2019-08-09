package com.github.simoant.drools.analyzer.camel

import com.github.simoant.drools.analyzer.IDataRequestProcessor
import com.github.simoant.drools.analyzer.X_UUID_NAME
import com.github.simoant.drools.analyzer.model.DataRequest
import com.github.simoant.drools.analyzer.utils.log
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import java.util.concurrent.CompletableFuture

data class CamelDataRequestProcessor (val camelContext: CamelContext) : IDataRequestProcessor {
    val producerTemplate: ProducerTemplate = camelContext.createProducerTemplate()

    override fun executeAsync(request: DataRequest, trackId: String): CompletableFuture<Any?> {
        val futureRes = producerTemplate.asyncRequestBodyAndHeader(request.uri, request.requestData,
            X_UUID_NAME, trackId)
        return futureRes
            .exceptionally {
                log.debug("CamelDataRequestProcessor: Null response for request: $request")
                null
            }
            .thenApply { it }
    }

}