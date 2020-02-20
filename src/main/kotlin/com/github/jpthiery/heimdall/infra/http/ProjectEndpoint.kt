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
import org.apache.commons.lang3.StringUtils.isNoneBlank
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.links.Link
import org.eclipse.microprofile.openapi.annotations.links.LinkParameter
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.util.*
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
            summary = "Create a new project",
            description = "",
            operationId = "createProject"
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
                    ],
                    links = [
                        Link(
                                name = "getProjectLink",
                                operationId = "getProject",
                                parameters = [
                                    LinkParameter(
                                            name = "projectId",
                                            expression = "\$response.body#/id"
                                    )
                                ]
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
            summary = "Allow to access to a given project state by her ProjectId",
            description = "",
            operationId = "getProject"
    )
    @APIResponses(
            APIResponse(
                    description = "Successfully access to the given project",
                    responseCode = "200",
                    content = [
                        Content(
                                schema = Schema(implementation = GetProjectResponseDto::class)
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
    ): Response = checkProjectIdIsValidForm(projectIdParam)
            .orElseGet {
                val projectId = ProjectId(projectIdParam)
                when (val projectState = projectFetcher.fetchById(projectId)) {
                    is ProjectNotExit -> Response.status(404).build()
                    else -> Response.ok(GetProjectResponseDto.fromProjectState(projectState)).build()
                }
            }


    @POST
    @Path("/{projectId}/document")
    @Operation(
            summary = "Allow to add a document to a given project",
            description = "",
            operationId = "addDocumentToProject"
    )
    @APIResponses(
            APIResponse(
                    description = "Successfully added document to the given project",
                    responseCode = "202"
            ),
            APIResponse(
                    description = "Project Id or Document are not valid",
                    responseCode = "400"
            ),
            APIResponse(
                    description = "Project not found",
                    responseCode = "404"
            )
    )
    @Tag(name = "Project")
    fun addDocument(
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
            projectIdParam: String,
            @Parameter(
                    required = true,
                    schema = Schema(
                            type = SchemaType.OBJECT,
                            implementation = DocumentRequestDto::class
                    )
            )
            documentRequestDto: DocumentRequestDto
    ): Response {
        val checkedProjectId = checkProjectIdIsValidForm(projectIdParam)
        val checkedDocumentDto = if (isBlank(documentRequestDto.name) ||
                (isBlank(documentRequestDto.content) && isBlank(documentRequestDto.url))) {
            Optional.of(Response.status(Response.Status.BAD_REQUEST).build())
        } else {
            Optional.empty()
        }

        //  Kotlin not support Optional.or :/
        return when {
            checkedProjectId.isPresent -> checkedProjectId.get()
            checkedDocumentDto.isPresent -> checkedDocumentDto.get()
            else -> {
                val document = when {
                    isNoneBlank(documentRequestDto.content) -> EmbeddedDocument(
                            documentRequestDto.name,
                            documentRequestDto.content
                    )
                    else -> ExternalLinkDocument(
                            documentRequestDto.name,
                            documentRequestDto.url
                    )
                }
                val projectId = ProjectId(projectIdParam)
                when (projectFetcher.fetchById(projectId)) {
                    is ProjectNotExit -> Response.status(Response.Status.NOT_FOUND).build()
                    else -> {
                        val command = AddDocumentToProject(
                                projectId,
                                document
                        )
                        when (val resultCommand = cqrsEngine.handleCommand(Project(), command)) {
                            is SuccessfullyHandleCommand<*, *> -> Response.accepted().build()
                            is FailedToHandleCommand<*> -> Response.status(Response.Status.BAD_REQUEST).entity(resultCommand.reason).build()
                            is NoopToHandleCommand<*> -> Response.status(Response.Status.NOT_MODIFIED).build()
                        }
                    }
                }
            }
        }

    }
}

fun checkProjectIdIsValidForm(projectIdParam: String): Optional<Response> =
        if (isBlank(projectIdParam) || !ProjectId.isValidProjectId(projectIdParam)) {
            Optional.of(Response.status(Response.Status.BAD_REQUEST).entity("projectId is not in expected format.").build())
        } else Optional.empty()


class CreateProjectRequestDto() {
    lateinit var name: String
}

data class CreateProjectResponseDto(val name: String, val id: String) {
    companion object {
        fun fromProjectDescribing(projectState: ProjectDescribing): CreateProjectResponseDto =
                CreateProjectResponseDto(projectState.name, projectState.id.id)
    }
}

data class GetProjectResponseDto(val name: String, val id: String) {
    companion object {
        fun fromProjectState(projectState: ProjectState): GetProjectResponseDto = when (projectState) {
            is ProjectDescribing -> GetProjectResponseDto(projectState.name, projectState.id.id)
            is ProjectAlive -> GetProjectResponseDto(projectState.name, projectState.id.id)
            else -> GetProjectResponseDto("", projectState.id.id)
        }
    }
}

class DocumentRequestDto() {
    lateinit var name: String
    lateinit var content: String
    lateinit var url: String
}
