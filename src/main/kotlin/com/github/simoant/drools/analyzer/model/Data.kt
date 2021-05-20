package com.github.simoant.drools.analyzer.model


data class DataRequest (
    val uri: String,
    val requestData: Any? = null,
    val required: Boolean = true,
    val splitList: Boolean = false
)

data class DataRequestResponse(
    val request: DataRequest,
    val data: Any?,
    val error: Throwable?,
    var et: Long? = null

) {
    override fun toString(): String {
        return "uri=${request.uri} data=$data et=$et ms"
    }

}
