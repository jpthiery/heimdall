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

interface StreamId
interface Command {
    fun id(): StreamId
}

interface State {
    fun id(): StreamId
}

interface Event {
    fun id(): StreamId
    fun happenedDate(): Long
}

object Unknown : StreamId
object NotExist : State {
    override fun id(): StreamId = Unknown

}

data class StateVersioned<S : State>(val state: S, val version: Int)

interface Aggregate<C : Command, S : State, E : Event> {

    fun decide(command: C, state: S): Either<String, List<E>>

    fun apply(state: S, event: E): S

    fun notExistState(): S

    fun replay(events: List<E>): StateVersioned<S> {
        var currentState = notExistState()
        var version = 0
        for (event in events) {
            currentState = apply(currentState, event)
            version++
        }
        return StateVersioned(currentState, version)
    }

    fun getEventType(): Class<E>

}

interface EventStore {

    fun <C : Command, S : State, E : Event> getEventForAggregate(aggregate: Aggregate<C, S, E>, id: StreamId): List<E>

    fun <I : StreamId, C : Command, S : State, E : Event> appendEvents(aggregate: Aggregate<C, S, E>, id: I, events: List<E>, initialStateVersion: Int): AppendedEventResult

}

sealed class AppendedEventResult
data class SuccessfulAppendedEventResult(val newStateVersion: Int) : AppendedEventResult()
data class FailedAppendedEventResult(val reason: String) : AppendedEventResult()

sealed class HandleCommandResult() {

    abstract val command: Command

}

data class SuccessfullyHandleCommand<C : Command, E : Event>(override val command: C, val eventEmitted: List<E>, val newStateVersion: Int) : HandleCommandResult()
data class FailedToHandleCommand<C : Command>(override val command: C, val reason: String) : HandleCommandResult()
data class NoopToHandleCommand<C : Command>(override val command: C) : HandleCommandResult()

class CqrsEngine(private val eventStore: EventStore) {

    fun <C : Command, E : Event, S : State> handleCommand(aggregate: Aggregate<C, S, E>, command: C): HandleCommandResult {

        val previousEvents = eventStore.getEventForAggregate(aggregate, command.id())
        val initialVersionedState = aggregate.replay(previousEvents)

        return aggregate.decide(command, initialVersionedState.state)
                .fold(
                        { reason -> FailedToHandleCommand(command, reason) },
                        { emittedEvents ->
                            if (emittedEvents.isEmpty()) {
                                NoopToHandleCommand(command)
                            } else {
                                when (val appendedEventResult = eventStore.appendEvents(aggregate, command.id(), emittedEvents, initialVersionedState.version)) {
                                    is SuccessfulAppendedEventResult -> SuccessfullyHandleCommand(command, emittedEvents, initialVersionedState.version + emittedEvents.size)
                                    is FailedAppendedEventResult -> FailedToHandleCommand(command, appendedEventResult.reason)
                                }
                            }
                        }
                )
    }

}

