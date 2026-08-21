package com.havenbank.backend.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata (NFR-6.5). Declares a bearer-JWT security scheme so the generated Swagger UI has
 * an "Authorize" control. The spec is served at {@code /v3/api-docs} and the UI at
 * {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI havenBankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Haven Bank API")
                        .version("v1")
                        .description("Retail banking platform: accounts, money movement, administration."
                                + "\n\n## Authentication"
                                + "\n\nThis API is an OAuth 2.1 **resource server** - it validates JWTs, it does not"
                                + " issue them. Obtain an access token from the platform's own authorization server"
                                + " using the Authorization Code flow with PKCE, then send it as"
                                + " `Authorization: Bearer <token>` (use the **Authorize** button above)."
                                + "\n\nThe sign-in and OTP steps are browser pages served by the authorization server,"
                                + " not JSON APIs, so they do not appear here. The OAuth 2.1 / OIDC protocol endpoints"
                                + " (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/oauth2/revoke`,"
                                + " `/userinfo`) are provided by Spring Authorization Server and described by the OIDC"
                                + " discovery document at `/.well-known/openid-configuration`.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}