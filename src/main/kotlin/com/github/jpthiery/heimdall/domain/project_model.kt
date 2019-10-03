package com.github.jpthiery.heimdall.domain

import java.time.Clock

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


sealed class ProjectEvent(private val clock: Clock = Clock.systemUTC()) : Event {
    abstract val id: ProjectId
    override fun id(): ProjectId = id
    override fun happenedDate(): Long = clock.millis()
}

data class ProjectCreated(override val id: ProjectId, val name: String) : ProjectEvent()
data class DocumentToProjectAdded(override val id: ProjectId, val document: Document) : ProjectEvent()
data class ProjectBuilt(override val id: ProjectId, val version: BuildId) : ProjectEvent()
data class DocumentToProjectBuiltAdded(override val id: ProjectId, val version: ProjectBuiltVersion, val document: Document) : ProjectEvent()

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
        val documents: List<Document> = listOf()
) : ProjectState()

data class ProjectAlive(
        override val id: ProjectId,
        val name: String,
        val scmUrl: String = "",
        val documents: List<Document> = listOf(),
        val deliveries: List<BuildId>
) : ProjectState()


//  Built of Project
data class BuildId(val id: ProjectBuiltVersion) : StreamId

