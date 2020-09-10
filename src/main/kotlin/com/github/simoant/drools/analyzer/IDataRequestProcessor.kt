package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.DataRequest
import java.util.concurrent.CompletableFuture

interface IDataRequestProcessor {
    fun executeAsync(request: DataRequest, trackId: String): CompletableFuture<out Any?>
}

