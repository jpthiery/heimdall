package com.github.jpthiery.heimdall.application

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

import com.github.jpthiery.heimdall.domain.CqrsEngine
import com.github.jpthiery.heimdall.domain.EventStore
import com.github.jpthiery.heimdall.infra.InMemoryEventStore
import io.quarkus.arc.DefaultBean
import javax.enterprise.context.Dependent
import javax.enterprise.inject.Produces
import javax.inject.Singleton

@Dependent
class ServiceConfiguration {

    @Produces
    @Singleton
    fun eventStore(): EventStore = InMemoryEventStore()

    @DefaultBean
    @Produces
    @Singleton
    fun cqrsEngine(eventStore: EventStore): CqrsEngine = CqrsEngine(eventStore)

    @DefaultBean
    @Produces
    @Singleton
    fun projectFetcher(eventStore: EventStore): ProjectFetcher = DefaultProjectFetcher(eventStore)

}

