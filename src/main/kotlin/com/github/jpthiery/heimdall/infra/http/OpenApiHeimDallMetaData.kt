package com.github.jpthiery.heimdall.infra.http

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition
import org.eclipse.microprofile.openapi.annotations.info.Info
import org.eclipse.microprofile.openapi.annotations.info.License
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@OpenAPIDefinition(
        info = Info(
                title = "Heimdall API",
                version = "1.0.0",
                license = License(
                        name = "Apache Licence v2",
                        url = "http://www.apache.org/licenses/LICENSE-2.0"
                )
        )
)
@ApplicationPath("/")
class OpenApiHeimDallMetaData : Application() {
}