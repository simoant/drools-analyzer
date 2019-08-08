package com.github.simoant.drools.analyzer.model

import kotlinx.coroutines.Deferred

data class DataRequest (
    val routeName: String,
    val requestData: Any? = null,
    val required: Boolean = true,
    val splitList: Boolean = false
)

data class DataResponse (
    val data: Any?
)

data class DataRequestDefferedResponse(
    val request: DataRequest,
    val deferredResponse: Deferred<DataResponse>
)
data class DataRequestResponse(
    val request: DataRequest,
    val response: DataResponse
)
