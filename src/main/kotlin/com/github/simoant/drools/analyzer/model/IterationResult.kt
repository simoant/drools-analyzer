package com.github.simoant.drools.analyzer.model

data class IterationResult(
    val iterationNumber: Int,
    val countFiredRules: Int,
    val facts: List<Any>,
) {
    val dataRequests: List<DataRequest>
        get() = facts
            .mapNotNull { it as? DataRequest }
    val markers: List<Any>
        get() = facts
            .mapNotNull { it as? InjectMarker }
            .map { it.body }

    val decisions: List<IDroolsDecision>
        get() = facts.mapNotNull { it as? IDroolsDecision }

    val value: AnalyzerResult?
        get() = facts.mapNotNull { it as? AnalyzerResult }.firstOrNull()

}
