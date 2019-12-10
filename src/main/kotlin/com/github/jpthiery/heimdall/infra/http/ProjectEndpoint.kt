package com.github.jpthiery.heimdall.infra.http

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

import com.github.jpthiery.heimdall.application.ProjectFetcher
import com.github.jpthiery.heimdall.domain.*
import org.apache.commons.lang3.StringUtils.isBlank
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.*


@Path(apiBase + "project")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class ProjectEndpoint(
        @Inject private val cqrsEngine: CqrsEngine,
        @Inject private val projectFetcher: ProjectFetcher
) {

    private val log: Logger = Logger.getLogger(javaClass)

    @POST
    @Operation(
            description = "Create a new project"
    )
    @APIResponses(
            APIResponse(
                    description = "Successfully created project",
                    responseCode = "201",
                    headers = [
                        Header(
                                name = "Location",
                                description = "Content url to access to the newly created project",
                                required = true
                        )
                    ]
            ),
            APIResponse(
                    description = "Project creation request not in valid format",
                    responseCode = "400"
            ),
            APIResponse(
                    description = "Project creation request no op",
                    responseCode = "422"
            )
    )
    @Tag(name = "Project")
    fun createProject(requestDto: CreateProjectRequestDto, @Context uriInfo: UriInfo): Response {
        log.debug("Create project with name '${requestDto.name}'")
        val name = requestDto.name
        if (isBlank(name)) {
            return Response.status(400).build()
        }

        val projectId = ProjectId.createProjectIdFromName(name)
        val command = CreateProject(projectId, name)

        val uriBuilder: UriBuilder = uriInfo.absolutePathBuilder
        uriBuilder.path(projectId.id)
        return when (val result = cqrsEngine.handleCommand(Project(), command)) {
            is SuccessfullyHandleCommand<*, *> -> {
                val project = Project()
                val currentProjectState = project.replay(result.eventEmitted as List<ProjectEvent>)
                when (currentProjectState.state) {
                    is ProjectDescribing -> {
                        Response
                                .created(uriBuilder.build())
                                .entity(CreateProjectResponseDto.fromProjectDescribing(currentProjectState.state))
                                .build()
                    }
                    else -> Response.status(400).entity("Not expected behaviour").build()
                }
            }
            is FailedToHandleCommand<*> -> {
                Response
                        .status(400)
                        .entity(result.reason)
                        .location(uriBuilder.build())
                        .build()
            }
            is NoopToHandleCommand<*> -> Response.status(422).build()
        }
    }

    @GET
    @Path("/{projectId}")
    @Operation(
            summary = "Allow to access to a given project state by her ProjectId"
    )
    @APIResponses(
            APIResponse(
                    description = "Successfully access to the given project",
                    responseCode = "200",
                    content = [
                        Content(
                                schema = Schema(oneOf = [
                                    ProjectDescribing::class,
                                    ProjectAlive::class
                                ])
                        )
                    ]
            ),
            APIResponse(
                    description = "Project Id not valid",
                    responseCode = "400"
            ),
            APIResponse(
                    description = "Project not found",
                    responseCode = "404"
            )
    )
    @Tag(name = "Project")
    fun getProject(
            @PathParam("projectId")
            @Parameter(
                    `in` = ParameterIn.PATH,
                    name = "projectId",
                    required = true,
                    schema = Schema(
                            type = SchemaType.STRING,
                            pattern = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    )
            )
            projectIdParam: String
    ): Response {
        if (isBlank(projectIdParam)) {
            return Response.status(400).entity("projectId must be defined").build()
        }
        val projectId = ProjectId(projectIdParam)
        return when (val projectState = projectFetcher.fetchById(projectId)) {
            is ProjectNotExit -> Response.status(404).build()
            else -> Response.ok(projectState).build()
        }
    }


}

class CreateProjectRequestDto() {
    lateinit var name: String
}

data class CreateProjectResponseDto(val name: String, val id: String) {
    companion object {
        fun fromProjectDescribing(projectState: ProjectDescribing): CreateProjectResponseDto =
                CreateProjectResponseDto(projectState.name, projectState.id.id)
    }
}
