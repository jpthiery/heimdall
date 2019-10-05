package com.github.jpthiery.heimdall.domain
/*
    Copyright 2019 Jean-Pascal Thiery

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import assertk.assertThat
import assertk.assertions.hasSameSizeAs
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.fail
import kotlin.reflect.KClass


data class DecideFixture<C : Command, S : State, E : Event>(val command: C, val initialState: S, val expectedResult: DecideResultExpected<E>)


sealed class DecideResultExpected<E : Event>

data class FailedDecideResultExpected<E : Event> private constructor(val reason: String?) : DecideResultExpected<E>() {
    companion object {
        fun <E : Event> failedWithoutCheckedReason(): FailedDecideResultExpected<E> = FailedDecideResultExpected(null)
        fun <E : Event> failWithReason(reason: String): FailedDecideResultExpected<E> = FailedDecideResultExpected(reason)
    }
}

data class SuccessDecideResultExpected<E : Event> private constructor(val events: List<KClass<out Event>>) : DecideResultExpected<E>() {
    companion object {
        fun <E : Event> commandSucceeded(events: List<KClass<out Event>>): SuccessDecideResultExpected<E> = SuccessDecideResultExpected(events)
        fun <E : Event> commandSucceeded(event: KClass<out Event>): SuccessDecideResultExpected<E> = SuccessDecideResultExpected(listOf(event))
    }
}

data class NoopDecideResultExpected<E : Event>(val reason: String?) : DecideResultExpected<E>() {
    companion object {
        fun <E : Event> commandNoop(): NoopDecideResultExpected<E> = NoopDecideResultExpected(null)
    }
}

fun <C : Command, S : State, E : Event> assertOnDecideFixture(decideFixture: DecideFixture<C, S, E>, aggregate: Aggregate<C, S, E>): () -> Unit {
    return {
        val result = aggregate.decide(decideFixture.command, decideFixture.initialState)
        when (val expected = decideFixture.expectedResult) {
            is SuccessDecideResultExpected -> {
                when (result) {
                    is Either.Left<String> -> fail("Expecting a success command handle, obtain a failed")
                    is Either.Right<List<Event>> -> {
                        val actualEvents = result.right
                        assertThat(actualEvents).hasSameSizeAs(expected.events)
                        for ((index, expectedEntry) in expected.events.withIndex()) {
                            assertThat(actualEvents.size).isGreaterThan(index)
                            val actualEntry = actualEvents[index]
                            assertThat(actualEntry).isInstanceOf(expectedEntry)
                        }
                    }
                }
            }
            is FailedDecideResultExpected -> {
                when (result) {
                    is Either.Left<String> -> {
                        if (expected.reason != null) {
                            assertThat(result.left).isEqualTo(expected.reason)
                        }
                    }
                    is Either.Right<List<Event>> -> fail("Expecting a failed command handle, obtain a success")
                }
            }
            is NoopDecideResultExpected -> {
                when (result) {
                    is Either.Left<String> -> fail("Expecting handle command no return events, obtain a failed")
                    is Either.Right<List<Event>> -> {
                        if (result.right.isNotEmpty()) {
                            fail("Expecting handle command no return events, obtain a success with ${result.right.size} events")
                        }
                    }
                }
            }
        }
    }
}

//  Builders

interface StateAppender<C : Command, S : State, E : Event> {
    fun  given(initialState: S): CommandAppender<C, S, E>
}

interface CommandAppender<C : Command, S : State, E : Event> {
    fun  whenApplyCommand(command: C): ExpectedAppender<C, S, E>
}

interface ExpectedAppender<C : Command, S : State, E : Event> {
    fun then(decideExpected: DecideResultExpected<E>): DecideFixture<C, S, E>
}

// https://youtrack.jetbrains.com/issue/KT-7770
fun <C : Command, S : State, E : Event> decideTestWith(): StateAppender<C, S, E> {
    return object : StateAppender<C, S, E> {
        override fun given(initialState: S): CommandAppender<C, S, E> {
            return object : CommandAppender<C, S, E> {
                override fun whenApplyCommand(command: C): ExpectedAppender<C, S, E> {
                    return object : ExpectedAppender<C, S, E> {
                        override fun then(decideExpected: DecideResultExpected<E>): DecideFixture<C, S, E> {
                            return DecideFixture(command, initialState, decideExpected)
                        }
                    }
                }
            }
        }
    }
}
