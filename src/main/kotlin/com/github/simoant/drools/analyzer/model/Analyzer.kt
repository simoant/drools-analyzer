package com.github.simoant.drools.analyzer.model

import org.kie.api.runtime.rule.AgendaFilter

data class AnalyzerRequest(
    val sessionName: String,
    val input: List<Any>,
    val id: String? = null,
    val agendaFilter: AgendaFilter? = null
)
data class AnalyzerResult(val data: Any?)
data class AnalyzerResponse(
    val lastIterationResult: IterationResult,

): IDroolsDecision {
    val data: Any?
        get() = lastIterationResult.value
}
