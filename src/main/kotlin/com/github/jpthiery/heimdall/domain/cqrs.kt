package com.github.jpthiery.heimdall.domain

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

}

interface EventStore {

    fun <C : Command, S : State, E : Event> getEventForAggregate(aggregate: Aggregate<C, S, E>, id: StreamId): List<E>

    fun <I: StreamId, E : Event> appendEvents(streamId: I, events: List<E>)

}

sealed class HandleCommandResult() {

    abstract val command: Command

}

data class SuccessfullyHandleCommand<C : Command, E : Event>(override val command: C, val eventEmitted: List<E>) : HandleCommandResult()
data class FailedToHandleCommand<C : Command>(override val command: C, val reason: String) : HandleCommandResult()
data class NoopToHandleCommand<C : Command>(override val command: C) : HandleCommandResult()

class CqrsEngine(private val eventStore: EventStore) {

    fun <C : Command, E : Event, S : State> handleCommand(aggregate: Aggregate<C, S, E>, command: C): HandleCommandResult {

        val previousEvents = eventStore.getEventForAggregate(aggregate, command.id())
        var versionedState = aggregate.replay(previousEvents)

        return aggregate.decide(command, versionedState.state)
                .fold(
                        { reason -> FailedToHandleCommand(command, reason) },
                        { emittedEvents ->
                            if (emittedEvents.isEmpty()) {
                                NoopToHandleCommand(command)
                            } else {
                                eventStore.appendEvents(command.id(), emittedEvents)
                                SuccessfullyHandleCommand(command, emittedEvents)
                            }
                        }
                )

    }

}