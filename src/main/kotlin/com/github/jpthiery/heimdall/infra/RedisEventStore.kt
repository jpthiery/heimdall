package com.github.jpthiery.heimdall.infra

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
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.util.*

class RedisEventStore(
        private val pool: JedisPool,
        private val eventStoreKeyForger: RedisEventStoreKeyForger,
        private val mappers: Map<Class<out Event>, RedisEventStoreMapper<out Event>>
) : EventStore {

    override fun <C : Command, S : State, E : Event> getEventForAggregate(aggregate: Aggregate<C, S, E>, id: StreamId): List<E> {
        return getMapper(aggregate.getEventType())
                .map { mapper ->
                    val key = eventStoreKeyForger.forgeKey(aggregate, id)
                    tryInTransaction { jedis ->
                        if (jedis.exists(key)) {
                            val redisEntries = jedis.lrange(key, 0, jedis.llen(key))

                            @Suppress("UNCHECKED_CAST")
                            redisEntries
                                    .map { eventStr ->
                                        mapper.deserialize(eventStr)
                                    }
                                    .filter { aggregate.getEventType().isAssignableFrom(it::class.java) }
                                    .toList() as List<E>
                        } else {
                            emptyList()
                        }
                    }
                }
                .orElse(emptyList())
    }

    override fun <I : StreamId, C : Command, S : State, E : Event> appendEvents(aggregate: Aggregate<C, S, E>, id: I, events: List<E>, initialStateVersion: Int): AppendedEventResult {

        return getMapper(aggregate.getEventType()).map { mapper ->
            val key = eventStoreKeyForger.forgeKey(aggregate, id)
            tryInTransaction { jedis ->
                val redisEntries = events
                        .map { event ->
                            mapper.serialize(event)
                        }
                        .toList()
                jedis.lpush(key, *redisEntries.toTypedArray())
                val eventsStored = getEventForAggregate(aggregate, id)
                SuccessfulAppendedEventResult(aggregate.replay(eventsStored).version) as AppendedEventResult
            }
        }.orElse(FailedAppendedEventResult("Unable to find and event mapper for aggregate ${aggregate::class.java.simpleName}"))

    }

    data class RedisConfiguration(
            val host: String,
            val port: Int
    )

    private fun <T> tryInTransaction(action: (Jedis) -> T): T = pool.resource.use { jedis ->
        val transaction = jedis.multi()
        val res = action(jedis)
        transaction.exec()
        res
    }


    private fun getMapper(eventType: Class<out Event>): Optional<RedisEventStoreMapper<out Event>> {
        if (mappers.containsKey(eventType)) {
            return Optional.ofNullable(mappers[eventType])
        }
        return Optional.empty()
    }

    interface RedisEventStoreKeyForger {
        fun forgeKey(aggregate: Aggregate<*, *, *>, id: StreamId): String
    }

    interface RedisEventStoreMapper<E : Event> {
        fun serialize(event: Event): String
        fun deserialize(data: String): E
    }
}