package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.AnalyzerRequest
import com.github.simoant.drools.analyzer.model.AnalyzerResult
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.kie.api.KieServices
import org.kie.api.runtime.KieContainer
import org.kie.internal.io.ResourceFactory
import java.nio.file.Paths

const val sessionName = "defaultKieSession"

class AnalyzerTest {

    @Before
    fun init() {
//        Hooks.onOperatorDebug()
    }

    @Test
    fun `When one rule requests data and second supplies data Then get the resuult from third rule`() {
        //  given
        val kieContainer = createTestKieContainer("src/test/kotlin/resources/drools/test/GetDataTest.drl")
        val analyzerReactive = Analyzer(kieContainer, TestRequestProcessorReactive())

        //  when
        val resR = analyzerReactive.run(AnalyzerRequest(sessionName, listOf(TestInput())))

        //  then
        Assertions.assertThat(resR.block()?.data).isEqualTo(AnalyzerResult("response"))
    }


    @Test
    fun `When request optional data but get no data from provider Then still receive the result`() {
        //  given
        val kieContainer = createTestKieContainer("src/test/kotlin/resources/drools/test/TestNoResultOptional.drl")
        val analyzerR = Analyzer(kieContainer, TestRequestProcessorReactive())

        //  when
        val resR = analyzerR.run(AnalyzerRequest(sessionName, listOf(TestInput())))

        //  then
        Assertions.assertThat(resR.block()?.data).isNull()
    }

    @Test

    fun `When request mandatory data but get no data from provider Then receive empty result`() {
        //  given
        val kieContainer = createTestKieContainer("src/test/kotlin/resources/drools/test/TestNoResultMandatory.drl")
        val analyzerR = Analyzer(kieContainer, TestRequestProcessorReactive())

        //  when
        val resR = analyzerR.run(AnalyzerRequest(sessionName, listOf(TestInput())))

        // then

        Assertions.assertThat(resR.block()?.data).isNull()
    }

    private fun createTestKieContainer(drlPath: String): KieContainer {

        val resources = listOf(
            // loads file from "real" filesystem

            ResourceFactory
                .newFileResource(Paths.get(drlPath).toFile())


        )

        val ks = KieServices.Factory.get()
        val kfs = ks.newKieFileSystem()
        resources.forEach { kfs.write(it) }

        val builder = ks.newKieBuilder(kfs).buildAll()
        val kieContainer = ks.newKieContainer(builder.kieModule.releaseId)
        return kieContainer
    }
}





