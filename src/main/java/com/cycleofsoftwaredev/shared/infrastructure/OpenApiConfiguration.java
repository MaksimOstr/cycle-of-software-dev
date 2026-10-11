package com.cycleofsoftwaredev.shared.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description of the generated OpenAPI 3 document (NFR "API documentation"). */
@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI shopSphereApi() {
        return new OpenAPI().info(new Info()
                .title("ShopSphere API")
                .version("v1")
                .description("REST API of the ShopSphere online store. The caller is identified by the "
                        + "X-User-Id header; the id is returned by POST /api/v1/auth/login."));
    }
}
