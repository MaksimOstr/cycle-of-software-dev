package com.cycleofsoftwaredev.shared.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title, description and the security scheme of the generated OpenAPI 3 document (NFR "API documentation"). */
@Configuration
class OpenApiConfiguration {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI shopSphereApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere API")
                        .version("v1")
                        .description("REST API of the ShopSphere online store. Sign in with POST /api/v1/auth/login, "
                                + "press Authorize and paste the accessToken: it is sent as \"Authorization: Bearer\"."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
