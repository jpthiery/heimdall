package com.github.jpthiery.heimdall.infra.http

import com.github.jpthiery.heimdall.domain.*
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
        val expectedId = createProjectIdFromName(projectName).id

        given()
                .body(projectName)

                .`when`()
                .post("/api/v1/project")

                .then()
                .statusCode(201)
                .body(`is`(expectedId))
                .header("Location", containsString("/api/v1/project/$expectedId"))
    }

    @Test
    fun `Create an already exist project should return a conflict http status code`() {

        val projectName = "Twice"
        val expectedId = createProject(projectName)

        given()
                .body(projectName)

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
                .body("id.id", `is`(projectId.id))
                .body("name", `is`(projectName))
    }

    private fun createProject(name: String): ProjectId {
        val projectId = createProjectIdFromName(name)
        val command = CreateProject(projectId, name)
        cqrsEngine.handleCommand(Project(), command)
        return projectId
    }

}