package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.AnalyzerResponse
import com.github.simoant.drools.analyzer.model.DataRequest
import com.github.simoant.drools.analyzer.model.IDroolsDecision
import com.github.simoant.drools.analyzer.utils.log
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.kie.api.KieServices
import org.kie.api.runtime.KieContainer
import org.kie.internal.io.ResourceFactory
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.util.concurrent.CompletableFuture


class AnalyzerTest {

    @Before
    fun init() {
//        Hooks.onOperatorDebug()
    }
    @Test
    fun `test one rule requests data and second supplies data (Future Processor)`() {
        //  given
        val kieContainer = createTestKieContainer("resources/drools/test/GetDataTest.drl")
        val analyzerFuture = Analyzer(kieContainer, TestRequestProcessorFuture(), null, true)
        val analyzerReactive = Analyzer(kieContainer, TestRequestProcessorReactive(), null, true)

        //  when
        log.debug("FUTURE")
        val resF = analyzerFuture.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())),
            onComplete = { log.debug("On complete: $it") })
        log.debug("REACTIVE")
        val resR = analyzerReactive.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())),
            onComplete = { log.debug("On complete: $it") })

        //  then
        Assertions.assertThat(resF.block()).isEqualTo(AnalyzerResponse("response"))
        Assertions.assertThat(resR.block()).isEqualTo(AnalyzerResponse("response"))
    }


    @Test
    fun `test request optional data but get no data from provider`() {
        //  given
        val kieContainer = createTestKieContainer("resources/drools/test/TestNoResultOptional.drl")
        val analyzerF = Analyzer(kieContainer, TestRequestProcessorFuture(), null)
        val analyzerR = Analyzer(kieContainer, TestRequestProcessorReactive(), null)

        //  when
        log.debug("FUTURE")
//        val resF = analyzerF.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))
        log.debug("REACTIVE")
        val resR = analyzerR.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))

        //  then
//        Assertions.assertThat(resF.get()).isNull()
        Assertions.assertThat(resR.block()).isNull()
    }

    @Test

    fun `test request mandatory data but get no data from provider`() {
        //  given
        val kieContainer = createTestKieContainer("resources/drools/test/TestNoResultMandatory.drl")
        val analyzerF = Analyzer(kieContainer, TestRequestProcessorFuture(), null)
        val analyzerR = Analyzer(kieContainer, TestRequestProcessorFuture(), null)

        //  when
        val resF = analyzerF.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))
        val resR = analyzerR.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))

        // then

        Assertions.assertThat(resF.block()).isNull()
        Assertions.assertThat(resR.block()).isNull()
    }

    private fun createTestKieContainer(drlPath: String): KieContainer {

        val resources = listOf(
            // loads file from "real" filesystem

            ResourceFactory
                .newFileResource(javaClass.classLoader.getResource(drlPath)!!.file)


        )

        val ks = KieServices.Factory.get()
        val kfs = ks.newKieFileSystem()
        resources.forEach { kfs.write(it) }

        val builder = ks.newKieBuilder(kfs).buildAll()
        val kieContainer = ks.newKieContainer(builder.kieModule.releaseId)
        return kieContainer
    }

    class TestRequestProcessorFuture : IDataRequestProcessor.IDataRequestProcessorFuture {
        override fun executeAsync(request: DataRequest): CompletableFuture<Any?> {
            log.debug("TestRequestProcessor: start $request")
            return CompletableFuture.supplyAsync {
                when (request.uri) {
                    "first" -> {
                        Thread.sleep(100)
                        FirstTestObject().also { log.debug("TestRequestProcessor: finish $it") }
                    }
                    "second" -> {
                        Thread.sleep(100)
                        SecondTestObject().also { log.debug("TestRequestProcessor: finish $it") }
                    }
                    "first_delayed" -> {
                        Thread.sleep(100)
                        SecondTestObject().also { log.debug("TestRequestProcessor: finish $it") }
                    }
                    "error" -> {
                        Thread.sleep(100)
                        throw java.lang.RuntimeException("TestRequestProcessor: Test Exception")
                    }
                    "no_data" -> {
                        Thread.sleep(100)
                        null
                    }
                    else -> throw RuntimeException("TestRequestProcessor: Invalid uri ${request.uri}")
                }

            }
        }
    }

    class TestRequestProcessorReactive : IDataRequestProcessor.IDataRequestProcessorReactive {
        private val delegate = TestRequestProcessorFuture()
        override fun executeAsync(request: DataRequest): Mono<Any> {
            return delegate.executeAsync(request).toMono()
        }
    }
}


data class TestInput(val value: String = "input")
data class TestDecision1(val value: String = "Test decision 1") : IDroolsDecision
data class TestDecision2(val value: String = "Test decision 2") : IDroolsDecision
data class FirstTestObject(val value: String = "first")
data class SecondTestObject(val value: String = "second")

