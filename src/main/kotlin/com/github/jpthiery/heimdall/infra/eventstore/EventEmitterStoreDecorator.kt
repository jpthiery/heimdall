package com.github.jpthiery.heimdall.infra.eventstore

import com.github.jpthiery.heimdall.domain.*
import javax.inject.Inject

class EventEmitterStoreDecorator<E : Event>(
        @Inject private val delegateStore: EventStore,
        @Inject private val eventEmitter: EventEmitter
) : EventStore {

    override fun <C : Command, S : State, E : Event> getEventForAggregate(aggregate: Aggregate<C, S, E>, id: StreamId): List<E> {
        return delegateStore.getEventForAggregate(aggregate, id)
    }

    override fun <I : StreamId, C : Command, S : State, E : Event> appendEvents(aggregate: Aggregate<C, S, E>, id: I, events: List<E>, initialStateVersion: Int): AppendedEventResult {
        val res = delegateStore.appendEvents(aggregate, id, events, initialStateVersion)
        eventEmitter.emit(events)
        return res
    }

}