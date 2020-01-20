package com.github.simoant.drools.analyzer.model

import org.kie.api.runtime.rule.AgendaFilter

data class AnalyzerRequest(
    val sessionName: String,
    val input: List<Any>,
    val id: String? = null,
    val agendaFilter: AgendaFilter? = null
)
data class AnalyzerResponse(
    val data: Any? = null
): IDroolsDecision
