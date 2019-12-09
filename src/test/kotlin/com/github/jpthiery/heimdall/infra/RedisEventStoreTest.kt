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

import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import com.github.jpthiery.heimdall.domain.*
import com.github.jpthiery.heimdall.infra.RedisEventStore.RedisEventStoreKeyForger
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import redis.clients.jedis.Client
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Transaction

internal class RedisEventStoreTest {

    private lateinit var mockJedis: Jedis
    private lateinit var jedisPool: JedisPool
    private lateinit var redisEventStoreMapper: RedisEventStore.RedisEventStoreMapper<ProjectEvent>
    private lateinit var redisEventStoreKeyForger: RedisEventStoreKeyForger
    private lateinit var redisEventStore: RedisEventStore
    private lateinit var projectRedisId: String

    @BeforeEach
    fun setup() {
        mockJedis = mock()
        jedisPool = TestJedisPool("localhost", mockJedis)
        redisEventStoreKeyForger = object : RedisEventStoreKeyForger {
            override fun forgeKey(aggregate: Aggregate<*, *, *>, id: StreamId): String = id.toString()
        }
        redisEventStoreMapper = ProjectEventSerializer()
        redisEventStore = RedisEventStore(jedisPool, redisEventStoreKeyForger, mapOf(Pair(ProjectEvent::class.java, redisEventStoreMapper)))
        projectRedisId = redisEventStoreKeyForger.forgeKey(Project(), defaultProjectId)
    }


    @Test
    fun `Requesting events from empty Stream should return empty list of events`() {
        whenInTransaction {
            whenever(mockJedis.exists(projectRedisId)).thenReturn(false)
        }

        val eventsInStream = redisEventStore.getEventForAggregate(Project(), defaultProjectId)

        assertk.assertThat(eventsInStream).isEmpty()
    }

    @Test
    fun `Requesting events from ProjectCreated should return creation event`() {
        whenInTransaction {
            whenever(mockJedis.exists(projectRedisId)).thenReturn(true)
            whenever(mockJedis.llen(projectRedisId)).thenReturn(1)
            whenever(mockJedis.lrange(projectRedisId, 0, 1)).thenReturn(
                    mutableListOf(projectCreatedJson)
            )
        }

        val eventInStream = redisEventStore.getEventForAggregate(Project(), defaultProjectId)

        assertk.assertThat(eventInStream).hasSize(1)
        val event = eventInStream[0]
        assertk.assertThat(event).isInstanceOf(ProjectCreated::class)
        verify(mockJedis).exists(projectRedisId)
        verify(mockJedis).llen(projectRedisId)
        verify(mockJedis).lrange(projectRedisId, 0, 1)
    }

    private fun whenInTransaction(action: (Transaction) -> Unit) {
        val client = mock<Client>()
        val transaction = Transaction(client)
        whenever(mockJedis.multi()).thenReturn(transaction)
        action(transaction)
    }

    class TestJedisPool(host: String?, private val jedis: Jedis) : JedisPool(host) {
        override fun getResource(): Jedis {
            return jedis
        }
    }

}