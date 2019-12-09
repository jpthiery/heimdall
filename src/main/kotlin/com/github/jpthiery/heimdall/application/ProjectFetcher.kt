package com.github.jpthiery.heimdall.application

import com.github.jpthiery.heimdall.domain.EventStore
import com.github.jpthiery.heimdall.domain.Project
import com.github.jpthiery.heimdall.domain.ProjectId
import com.github.jpthiery.heimdall.domain.ProjectState

interface ProjectFetcher {

    fun fetchById(projectId: ProjectId): ProjectState

}

class DefaultProjectFetcher(private val eventStore: EventStore): ProjectFetcher {

    override fun fetchById(projectId: ProjectId): ProjectState {
        val project = Project()
        val events = eventStore.getEventForAggregate(project, projectId)
        return project.replay(events).state
    }

}