package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.DataRequest
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class TestRequestProcessorReactive : IDataRequestProcessor.IDataRequestProcessorReactive {
    val processors = mapOf(
        "first" to
            { request: Any? ->
                Mono.fromSupplier {
                    Thread.sleep(100)
                    FirstTestObject().also { log.debug("TestRequestProcessor: finish $it") }
                }.subscribeOn(Schedulers.boundedElastic())
            },
        "second" to
            { request: Any? ->
                Mono.fromSupplier {
                    Thread.sleep(100)
                    SecondTestObject().also { log.debug("TestRequestProcessor: finish $it") }
                }.subscribeOn(Schedulers.boundedElastic())
            },
        "first_delayed" to
            { request: Any? ->
                Mono.fromSupplier {
                    Thread.sleep(100)
                    SecondTestObject().also { log.debug("TestRequestProcessor: finish $it") }
                }.subscribeOn(Schedulers.boundedElastic())
            }
    )

    override fun executeAsync(request: DataRequest): Mono<out Any> {
        val pr = processors.get(request.uri)
        return pr?.invoke(request.requestData) ?: throw RuntimeException("processor not found")
    }
}
