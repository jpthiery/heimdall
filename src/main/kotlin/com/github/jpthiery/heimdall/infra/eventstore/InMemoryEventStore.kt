package com.github.jpthiery.heimdall.infra.eventstore

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

import com.github.jpthiery.heimdall.domain.*
import javax.annotation.Priority
import javax.enterprise.inject.Alternative

@Alternative
@Priority(1)
class InMemoryEventStore : EventStore {

    private val cache: MutableMap<CacheEntry, List<Event>> = mutableMapOf()

    override fun <C : Command, S : State, E : Event> getEventForAggregate(aggregate: Aggregate<C, S, E>, id: StreamId): List<E> {
        val cacheEntry = CacheEntry.from(id)
        val res = cache[cacheEntry]
        return if (res == null) {
            listOf()
        } else {
            res as List<E>
        }
    }

    override fun <I : StreamId, C : Command, S : State, E : Event> appendEvents(aggregate: Aggregate<C, S, E>, streamId: I, events: List<E>, initialStateVersion: Int): AppendedEventResult {
        val cacheEntry = CacheEntry.from(streamId)
        val storedEvents = cache[cacheEntry]?.toMutableList() ?: mutableListOf()
        storedEvents.addAll(events)
        cache[cacheEntry] = storedEvents.toList()
        val resVersion = initialStateVersion + events.size
        return SuccessfulAppendedEventResult(resVersion)
    }

    data class CacheEntry(val aggregateClass: Class<StreamId>, val id: StreamId) {
        companion object {
            fun from(id: StreamId): CacheEntry = CacheEntry(id.javaClass, id)
        }
    }

}