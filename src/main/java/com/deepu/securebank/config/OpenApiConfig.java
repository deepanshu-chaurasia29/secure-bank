package com.deepu.securebank.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Settings for the Swagger page (springdoc).
 * Open it at /swagger-ui.html. The "Authorize" button there accepts a JWT token,
 * so you can try the protected APIs from the browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI secureBankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SecureBank API")
                        .version("v1")
                        .description("A demo banking backend (Spring Boot, JdbcTemplate, MySQL, JWT). "
                                + "All money is demo money. Log in with /api/v1/auth/login, copy the token, "
                                + "click Authorize, and paste the token."))
                // every URL needs the token, except the ones marked with security = {} (register, login)
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
