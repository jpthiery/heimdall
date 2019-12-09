package com.github.jpthiery.heimdall.infra

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jpthiery.heimdall.domain.Event
import com.github.jpthiery.heimdall.domain.ProjectEvent
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

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

class ProjectEventSerializer : RedisEventStore.RedisEventStoreMapper<ProjectEvent> {

    companion object {
        fun createMapper(): ObjectMapper {
            val kotlinModule = KotlinModule()
            kotlinModule.addSerializer(ProjectEvent::class.java, ProjectEventJacksonSerializer())
            kotlinModule.addDeserializer(ProjectEvent::class.java, ProjectEventJacksonDeserializer())
            return ObjectMapper().registerModule(kotlinModule)
        }
    }

    override fun serialize(event: Event): String {
        val mapper = createMapper()
        return mapper.writeValueAsString(event)
    }

    override fun deserialize(data: String): ProjectEvent {
        val mapper = createMapper()
        return mapper.readValue(data, ProjectEvent::class.java)
    }

    private class ProjectEventJacksonSerializer : StdSerializer<ProjectEvent>(ProjectEvent::class.java) {
        override fun serialize(projectEvent: ProjectEvent?, jgen: JsonGenerator?, sp: SerializerProvider?) {
            if (projectEvent == null || jgen == null) return;

            jgen.writeStartObject()
            val properties = projectEvent::class::memberProperties.get()
            for (property in properties) {
                val propertyValue = property as KProperty1<Any, *>
                jgen.writeObjectField(property.name, propertyValue.get(projectEvent))

            }
            jgen.writeObjectField("eventType", projectEvent::class.java.simpleName)
            jgen.writeEndObject()
        }
    }

    private class ProjectEventJacksonDeserializer : StdDeserializer<ProjectEvent>(ProjectEvent::class.java) {
        override fun deserialize(jsonPaser: JsonParser?, dc: DeserializationContext?): ProjectEvent? {
            if (jsonPaser == null || dc == null) return null
            val jsonNode = jsonPaser.codec.readTree<JsonNode>(jsonPaser)
            val eventType = jsonNode.get("eventType").textValue() ?: return null
            //val eventClass = Class.forName("${ProjectEvent::class.java.packageName}.${eventType}")
            val eventClass = Class.forName("com.github.jpthiery.heimdall.domain.${eventType}")
            val kotlinModule = KotlinModule()
            val mapper = ObjectMapper().registerModule(kotlinModule)
            val rewriteJsonNode = JsonNodeFactory.instance.objectNode()
            jsonNode.fields()
                    .asSequence()
                    .filter {
                        it.key != "eventType"
                    }
                    .forEach {
                        rewriteJsonNode.set(it.key, it.value)
                    }
            return mapper.readValue(rewriteJsonNode.toString(), eventClass) as ProjectEvent
        }

    }

}