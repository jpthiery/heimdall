package com.github.jpthiery.heimdall.domain

class EventStoreForTest() : EventStore {

    private val cache: MutableMap<CacheEntry, List<Event>> = mutableMapOf()

    override fun <C : Command, S : State, E : Event> getEventForAggregate(aggregate: Aggregate<C, S, E>, id: StreamId): List<E> {
        val cacheEntry = CacheEntry.from(id)
        val res = cache[cacheEntry]
        return if (res == null) {
            listOf<E>()
        } else {
            res as List<E>
        }
    }

    override fun <I : StreamId, E : Event> appendEvents(streamId: I, events: List<E>) {
        val cacheEntry = CacheEntry.from(streamId)
        val storedEvents = cache[cacheEntry]?.toMutableList() ?: mutableListOf()
        storedEvents.addAll(events)
        cache[cacheEntry] = storedEvents.toList()
    }

    data class CacheEntry(val aggregateClass: Class<StreamId>, val id: StreamId) {
        companion object {
            fun from(id: StreamId): CacheEntry = CacheEntry(id.javaClass, id)
        }
    }


}