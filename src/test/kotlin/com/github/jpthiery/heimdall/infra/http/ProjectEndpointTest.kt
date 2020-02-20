package com.github.jpthiery.heimdall.infra.http

import com.github.jpthiery.heimdall.domain.CqrsEngine
import com.github.jpthiery.heimdall.domain.CreateProject
import com.github.jpthiery.heimdall.domain.Project
import com.github.jpthiery.heimdall.domain.ProjectId
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import javax.inject.Inject

@QuarkusTest
@Tag("integration")
internal class ProjectEndpointTest {

    @Inject
    lateinit var cqrsEngine: CqrsEngine

    @Test
    fun `Create a new project should return her id`() {
        val projectName = "TheOne"
        val expectedId = ProjectId.createProjectIdFromName(projectName).id

        given()
                .body("{\"name\": \"$projectName\" }")

                .`when`()
                .contentType("application/json")
                .post("/api/v1/project")

                .then()
                .statusCode(201)
                .body("id", `is`(expectedId))
                .body("name", `is`(projectName))
                .header("Location", containsString("/api/v1/project/$expectedId"))
    }

    @Test
    fun `Create an already exist project should return a conflict http status code`() {

        val projectName = "Twice"
        val expectedId = createProject(projectName)

        given()
                .body("{\"name\": \"$projectName\" }")

                .`when`()
                .contentType("application/json")
                .`when`()
                .post("/api/v1/project")

                .then()
                .statusCode(400)
                .header("Location", containsString("/api/v1/project/${expectedId.id}"))

    }

    @Test
    fun `Get an exiting project should return her details`() {

        val projectName = "TopGun"
        val projectId = createProject(projectName)

        given()
                .pathParam("projectId", projectId.id)
                .`when`()
                .get("/api/v1/project/{projectId}")
                .then()
                .statusCode(200)
                .body("id", `is`(projectId.id))
                .body("name", `is`(projectName))
    }

    @Test
    fun `Add a document to not existing project may return a bad request`() {
        val projectName = "TopGun"

        given()
                .pathParam("projectId", ProjectId.createProjectIdFromName(projectName).id)
                .`when`()
                .contentType("application/json")
                .with().body("{\"name\": \"topSecretDocument\", \"content\": \"Coucou\"}")
                .post("/api/v1/project/{projectId}/document")
                .then()
                .statusCode(404)
    }

    private fun createProject(name: String): ProjectId {
        val projectId = ProjectId.createProjectIdFromName(name)
        val command = CreateProject(projectId, name)
        cqrsEngine.handleCommand(Project(), command)
        return projectId
    }

}