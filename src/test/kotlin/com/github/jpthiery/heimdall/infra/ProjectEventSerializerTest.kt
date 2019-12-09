package com.github.jpthiery.heimdall.infra

import com.github.jpthiery.heimdall.domain.ProjectCreated
import com.github.jpthiery.heimdall.domain.defaultProjectId
import org.junit.jupiter.api.Test

internal class ProjectEventSerializerTest {

    @Test
    fun `Serialized a ProjectCreated Event`() {
        val converter = ProjectEventSerializer()
        val message = converter.serialize(ProjectCreated(defaultProjectId, defaultProjectId.id))
        println(message)
        val reverted = converter.deserialize(message)
        println(reverted)
        println(reverted.happenedDate)
    }

}