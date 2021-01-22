package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.DataRequest
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

interface IDataRequestProcessor {
    interface IDataRequestProcessorFuture: IDataRequestProcessor {
        fun executeAsync(request: DataRequest, trackId: String): CompletableFuture<out Any?>
    }

    interface IDataRequestProcessorReactive: IDataRequestProcessor {
        fun executeAsync(request: DataRequest, trackId: String): Mono<out Any>
    }
}
