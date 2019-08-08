package com.github.simoant.drools.analyzer.model

data class AnalyzerRequest(
    val sessionName: String,
    val input: List<Any>
) {

}

data class AnalyzerResponse(
    val data: Any? = null
)
