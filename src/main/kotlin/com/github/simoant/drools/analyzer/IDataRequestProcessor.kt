package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.DataRequest
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

interface IDataRequestProcessor {
    interface IDataRequestProcessorReactive: IDataRequestProcessor {
        fun executeAsync(request: DataRequest): Mono<out Any>
    }
}

