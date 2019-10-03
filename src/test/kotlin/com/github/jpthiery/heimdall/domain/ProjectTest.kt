package com.github.jpthiery.heimdall.domain

import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

internal class ProjectTest {

    private lateinit var eventStore: EventStore

    private lateinit var cqrsEngine: CqrsEngine

    private lateinit var aggregate: Project

    @BeforeEach
    fun setup() {
        eventStore = EventStoreForTest()
        cqrsEngine = CqrsEngine(eventStore)
        aggregate = Project()
    }

    @Test
    fun `Create a project will return a ProjectCreated event`() {

        val command = CreateProject(defaultProjectId, "Heimdall")

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        assertk.assertThat(commandResult).isInstanceOf(SuccessfullyHandleCommand::class)
        val successResult = commandResult as SuccessfullyHandleCommand<ProjectCommand, ProjectEvent>
        assertk.assertThat(successResult.eventEmitted).hasSize(1)
    }

    @Test
    fun `Attach a document to a not existing project may be rejected`() {

        val command = AddDocumentToProject(defaultProjectId, defaultDocument)

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        assertk.assertThat(commandResult).isInstanceOf(FailedToHandleCommand::class)
    }

    @Test
    fun `Attach a built version to a not existing project may be rejected`() {

        val command = AttachBuiltVersionOfProject(defaultProjectId, defaultBuiltId)

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        assertk.assertThat(commandResult).isInstanceOf(FailedToHandleCommand::class)
    }

    @Test
    fun `Attach a document to a project with a description should return a DocumentToProjectAdded event`() {

        eventStore.appendEvents(defaultProjectId, listOf(ProjectCreated(defaultProjectId, defaultProjectId.id)))
        val command = AddDocumentToProject(defaultProjectId, defaultDocument)

        val commandResult = cqrsEngine.handleCommand(aggregate, command)


        expectedCommandSuccessContainEventAndIsTypeOf(commandResult, DocumentToProjectAdded::class)
    }


    @Test
    fun `Create a project to an project describing may be rejected`() {

        eventStore.appendEvents(defaultProjectId, listOf(ProjectCreated(defaultProjectId, defaultProjectId.id)))
        val command = CreateProject(defaultProjectId, "Heimdall")

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        assertk.assertThat(commandResult).isInstanceOf(FailedToHandleCommand::class)
    }


    @Test
    fun `Create a project to a lived project may be rejected`() {

        eventStore.appendEvents(
                defaultProjectId,
                listOf(
                        ProjectCreated(defaultProjectId, defaultProjectId.id),
                        ProjectBuilt(defaultProjectId, defaultBuiltId)
                )
        )

        val command = CreateProject(defaultProjectId, "Heimdall")

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        assertk.assertThat(commandResult).isInstanceOf(FailedToHandleCommand::class)
    }


    @Test
    fun `Attach a build version to a project describing should return a ProjectBuilt event`() {

        eventStore.appendEvents(
                defaultProjectId,
                listOf(
                        ProjectCreated(defaultProjectId, defaultProjectId.id)
                )
        )

        val command = AttachBuiltVersionOfProject(defaultProjectId, defaultBuiltId)

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        expectedCommandSuccessContainEventAndIsTypeOf(commandResult, ProjectBuilt::class)
    }

    @Test
    fun `Attach 2 builds version to a project describing should return a ProjectBuilt event`() {

        eventStore.appendEvents(
                defaultProjectId,
                listOf(
                        ProjectCreated(defaultProjectId, defaultProjectId.id)
                )
        )

        var command = AttachBuiltVersionOfProject(defaultProjectId, defaultBuiltId)

        cqrsEngine.handleCommand(aggregate, command)
        command = AttachBuiltVersionOfProject(defaultProjectId, BuildId(ProjectBuiltVersion("2.0.0")))

        val commandResult = cqrsEngine.handleCommand(aggregate, command)

        expectedCommandSuccessContainEventAndIsTypeOf(commandResult, ProjectBuilt::class)
        val events = eventStore.getEventForAggregate(aggregate, defaultProjectId)
        assertk.assertThat(events).hasSize(3)

        val currentState = aggregate.replay(events)
        assertk.assertThat(currentState.state).isInstanceOf(ProjectAlive::class)
        val projectAlive = currentState.state as ProjectAlive
        assertk.assertThat(projectAlive.deliveries).hasSize(2)

    }

    private fun containAtLeastOnEventType(eventType: KClass<out ProjectEvent>, events: List<ProjectEvent>): Boolean = events.any {
        it::class == eventType
    }.or(false)

    private fun expectedCommandSuccessContainEventAndIsTypeOf(commandResult: HandleCommandResult, eventType: KClass<out ProjectEvent>?) {
        expectedCommandResultContainEventAndIsTypeOf(
                commandResult,
                SuccessfullyHandleCommand::class,
                eventType
        )
    }

    private fun expectedCommandFailed(commandResult: HandleCommandResult, eventType: KClass<out ProjectEvent>?) {
        expectedCommandResultContainEventAndIsTypeOf(
                commandResult,
                FailedToHandleCommand::class,
                null
        )
    }

    private fun expectedCommandNoop(commandResult: HandleCommandResult, eventType: KClass<out ProjectEvent>?) {
        expectedCommandResultContainEventAndIsTypeOf(
                commandResult,
                NoopToHandleCommand::class,
                null
        )
    }

    private fun expectedCommandResultContainEventAndIsTypeOf(commandResult: HandleCommandResult, commandResultType: KClass<out HandleCommandResult>, eventType: KClass<out ProjectEvent>?) {
        assertk.assertThat(commandResult).isInstanceOf(commandResultType)
        if (eventType != null) {
            if (commandResult is SuccessfullyHandleCommand<*, *>) {
                val successResult = commandResult as SuccessfullyHandleCommand<ProjectCommand, ProjectEvent>
                assertk.assertThat(successResult.eventEmitted).hasSize(1)
                containAtLeastOnEventType(eventType, successResult.eventEmitted)
            }
        }
    }

}