package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.AnalyzerResponse
import com.github.simoant.drools.analyzer.model.DataRequest
import com.github.simoant.drools.analyzer.utils.log
import org.assertj.core.api.Assertions
import org.junit.Test
import org.kie.api.KieServices
import org.kie.api.runtime.KieContainer
import org.kie.internal.io.ResourceFactory
import java.util.concurrent.CompletableFuture


class AnalyzerTest {

    @Test
    fun `test one rule requests data and second supplies data`() {
        //  given
        val kieContainer = createTestKieContainer("resources/drools/test/GetDataTest.drl")
        val analyzer = Analyzer(kieContainer, TestRequestProcessor(), null)

        //  when
        val res = analyzer.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))

        //  then
        Assertions.assertThat(res.get()).isEqualTo(AnalyzerResponse("response"))
    }

    @Test
    fun `test request optional data but get no data from provider`() {
        //  given
        val kieContainer = createTestKieContainer("resources/drools/test/TestNoResultOptional.drl")
        val analyzer = Analyzer(kieContainer, TestRequestProcessor(), null )

        //  when
        val res = analyzer.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))

        //  then
        Assertions.assertThat(res.get()).isNull()


    }
    @Test

    fun `test request mandatory data but get no data from provider`() {
        //  given
        val kieContainer = createTestKieContainer("resources/drools/test/TestNoResultMandatory.drl")
        val analyzer = Analyzer(kieContainer, TestRequestProcessor(), null )

        //  when
        val res = analyzer.run(AnalyzerRequest("defaultKieSession", listOf(TestInput())))

        // then

        Assertions.assertThatThrownBy {
            res.get()
        }
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

    class TestRequestProcessor : IDataRequestProcessor {
        override fun executeAsync(request: DataRequest, trackId: String): CompletableFuture<Any?> {
            log.debug("start $request")
            return CompletableFuture.completedFuture(
                when (request.uri) {
                    "first" -> {
                        FirstTestObject().also { log.debug("finish $it") }
                    }
                    "second" -> {
                        SecondTestObject().also { log.debug("finish $it") }
                    }
                    "first_delayed" -> {
                        Thread.sleep(100)
                        SecondTestObject().also { log.debug("finish $it") }
                    }
                    "error" -> {
                        throw java.lang.RuntimeException("Test Exception")
                    }
                    "no_data" -> {
                        null
                    }
                    else -> throw RuntimeException("Invalid uri ${request.uri}")
                })

        }
    }
}

data class TestInput(val value: String = "input")
data class FirstTestObject(val value: String = "first")
data class SecondTestObject(val value: String = "second")

