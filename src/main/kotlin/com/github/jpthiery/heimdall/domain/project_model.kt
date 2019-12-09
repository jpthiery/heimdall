package com.github.jpthiery.heimdall.domain

import com.github.jpthiery.heimdall.infra.RedisEventStore
import java.time.Clock

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

sealed class Document() {
    abstract val name: String
}

data class EmbeddedDocument(override val name: String, val content: String) : Document()
data class ExternalLinkDocument(override val name: String, val url: String) : Document()
data class InternalLinkDocument(override val name: String, val url: String) : Document()

data class ProjectBuiltVersion(val version: String)

data class ProjectId(val id: String) : StreamId

sealed class ProjectCommand : Command {
    abstract val id: ProjectId
    override fun id(): ProjectId = id
}

data class CreateProject(override val id: ProjectId, val name: String) : ProjectCommand()
data class AddDocumentToProject(override val id: ProjectId, val document: Document) : ProjectCommand()
data class AttachBuiltVersionOfProject(override val id: ProjectId, val builtId: BuildId) : ProjectCommand()

sealed class ProjectEvent(clock: Clock = Clock.systemUTC()) : Event {
    abstract val id: ProjectId
    val happenedDate: Long = clock.millis()
    override fun id(): ProjectId = id
    override fun happenedDate(): Long = happenedDate
}

data class ProjectCreated(override val id: ProjectId, val name: String) : ProjectEvent()
data class DocumentToProjectAdded(override val id: ProjectId, val document: Document) : ProjectEvent()
data class ProjectBuilt(override val id: ProjectId, val version: BuildId) : ProjectEvent()

sealed class ProjectState : State {
    abstract val id: ProjectId
    override fun id(): ProjectId = id
}

object ProjectNotExit : ProjectState() {
    override val id: ProjectId
        get() = ProjectId("Unknown")
}

data class ProjectDescribing(
        override val id: ProjectId,
        val name: String,
        val scmUrl: String = "",
        val documents: Set<Document> = setOf()
) : ProjectState()

data class ProjectAlive(
        override val id: ProjectId,
        val name: String,
        val scmUrl: String = "",
        val documents: Set<Document> = setOf(),
        val deliveries: Set<BuildId>
) : ProjectState()


//  Built of Project
data class BuildId(val id: ProjectBuiltVersion) : StreamId


