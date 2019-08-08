package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.DataRequest
import com.github.simoant.drools.analyzer.model.DataResponse
import java.util.concurrent.CompletableFuture

interface IDataRequestProcessor {
    abstract fun executeAsync(request: DataRequest, trackId: String): CompletableFuture<DataResponse>
}

