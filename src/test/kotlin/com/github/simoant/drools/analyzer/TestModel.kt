package com.github.simoant.drools.analyzer

import com.github.simoant.drools.analyzer.model.IDroolsDecision

data class TestInput(val value: String = "input")
data class TestDecision1(val value: String = "Test decision 1") : IDroolsDecision
data class TestDecision2(val value: String = "Test decision 2") : IDroolsDecision
data class FirstTestObject(val value: String = "first")
data class SecondTestObject(val value: String = "second")
