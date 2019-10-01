package com.github.jpthiery.heimdall.domain

interface StreamId
interface Command {
    abstract val streamId: StreamId
    fun id(): StreamId {
        return streamId
    }
}

interface State {
    abstract val streamId: StreamId
    fun id(): StreamId {
        return streamId
    }
}

interface Event {
    abstract val streamId: StreamId
    fun id(): StreamId {
        return streamId
    }
}

object Unknown : StreamId
object NotExist : State {
    override val streamId: StreamId
        get() = Unknown
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

    fun <C : Command, S : State, E : Event> appendEvents(aggregate: Aggregate<C, S, E>, events: List<E>)

}


sealed class HandleCommandResult() {

    abstract val command: Command

}


data class SuccessfullyHandleCommand<C : Command, E : Event>(override val command: C, val eventEmitted: List<E>) : HandleCommandResult()
data class FailedToHandleCommand<C : Command>(override val command: C, val reason: String) : HandleCommandResult()
data class NoopToHandleCommand<C : Command>(override val command: C) : HandleCommandResult()

class CqrsEngine(private val eventStore: EventStore) {

    fun <C : Command, E : Event, S : State> handleCommand(aggregate: Aggregate<C, S, E>, command: C): HandleCommandResult {

        val previousEvents = eventStore.getEventForAggregate(aggregate, command.streamId)
        var versionedState = aggregate.replay(previousEvents)

        return aggregate.decide(command, versionedState.state)
                .fold(
                        { reason -> FailedToHandleCommand(command, reason) },
                        { emittedEvents ->
                            if (emittedEvents.isEmpty()) {
                                NoopToHandleCommand(command)
                            } else {
                                SuccessfullyHandleCommand(command, emittedEvents)
                            }
                        }
                )

    }

}