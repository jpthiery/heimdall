package com.github.jpthiery.heimdall.domain


class Project : Aggregate<ProjectCommand, ProjectState, ProjectEvent> {

    override fun decide(command: ProjectCommand, state: ProjectState): Either<String, List<ProjectEvent>> = when (state) {
        ProjectNotExit -> decideOnProjectNotExit(command)
        is ProjectDescribing -> decideOnProjectDescribing(state, command)
        is ProjectAlive -> decideOnProjectAlive(state, command)
    }

    override fun apply(state: ProjectState, event: ProjectEvent): ProjectState = when (state) {
        ProjectNotExit -> applyOnProjectNotExist(event)
        is ProjectDescribing -> applyOnProjectDescribing(state, event)
        is ProjectAlive -> applyOnProjectAlive(state, event)
    }

    override fun notExistState(): ProjectState = ProjectNotExit

    private fun decideOnProjectNotExit(command: ProjectCommand): Either<String, List<ProjectEvent>> = when (command) {
        is CreateProject -> Either.success(listOf(
                ProjectCreated(command.id, command.name)
        ))
        else -> Either.fail("Project ${command.id} not exist yet")
    }

    private fun decideOnProjectDescribing(state: ProjectDescribing, command: ProjectCommand): Either<String, List<ProjectEvent>> = when (command) {
        is AddDocumentToProject -> {
            if (state.documents.contains(command.document)) {
                Either.success(listOf())
            } else {
                Either.success(listOf(
                        DocumentToProjectAdded(
                                state.id,
                                command.document
                        )
                )
                )
            }
        }
        is AttachBuiltVersionOfProject -> Either.success(
                listOf(
                        ProjectBuilt(state.id, command.builtId)
                )
        )
        else -> Either.fail("Command ${command::class} is not supported for project describing")
    }

    private fun decideOnProjectAlive(state: ProjectAlive, command: ProjectCommand): Either<String, List<ProjectEvent>> = when (command) {
        is AttachBuiltVersionOfProject -> {
            if (state.deliveries.contains(command.builtId)) {
                Either.success(listOf())
            } else {
                Either.success(
                        listOf(
                                ProjectBuilt(state.id, command.builtId)
                        )
                )
            }
        }
        else -> Either.fail("Not yet implemented for state ProjectAlive")
    }

    private fun applyOnProjectNotExist(event: ProjectEvent): ProjectState = when (event) {
        is ProjectCreated -> ProjectDescribing(event.id, event.name)
        else -> ProjectNotExit
    }

    private fun applyOnProjectDescribing(state: ProjectDescribing, event: ProjectEvent): ProjectState = when (event) {
        is DocumentToProjectAdded -> {
            val newEvents = state.documents.toMutableList()
            newEvents.add(event.document)
            ProjectDescribing(
                    state.id,
                    state.name,
                    state.scmUrl,
                    newEvents.toList()
            )
        }
        is ProjectBuilt -> ProjectAlive(
                state.id,
                state.name,
                state.scmUrl,
                state.documents.toList(),
                listOf(event.version)
        )
        else -> state
    }

    private fun applyOnProjectAlive(state: ProjectAlive, event: ProjectEvent): ProjectState = when (event) {
        is ProjectBuilt -> {
            val newBuilts = state.deliveries.toMutableList()
            newBuilts.add(event.version)
            ProjectAlive(
                    state.id,
                    state.name,
                    state.scmUrl,
                    state.documents.toList(),
                    newBuilts.toList()
            )
        }
        else -> state
    }

}