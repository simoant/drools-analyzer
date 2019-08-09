package com.github.simoant.drools.analyzer.model

import kotlinx.coroutines.Deferred

data class DataRequest (
    val uri: String,
    val requestData: Any? = null,
    val required: Boolean = true,
    val splitList: Boolean = false
)

data class DataResponse (
    val uri: String,
    val data: Any?,
    val et: Long?
) {
    override fun toString(): String {
        return "$data from uri=$uri et=$et ms"
    }
}

data class DataRequestDefferedResponse(
    val request: DataRequest,
    val deferredResponse: Deferred<Any?>,
    val startTime: Long = System.currentTimeMillis(),
    var et: Long? = null

)
data class DataRequestResponse(
    val request: DataRequest,
    val response: DataResponse
)
