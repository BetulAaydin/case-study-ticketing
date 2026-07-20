package com.turkcell.mayacore.ticketing.config;

import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(
            @Value("${ticketing.gateway.shared-secret:ticketing-local-gateway-secret}") String gatewaySecret) {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketing Service API")
                        .version("1.0.0")
                        .description("""
                                Prefer calling via Gateway http://localhost:8080/api/...
                                For direct Swagger calls set headers:
                                X-Gateway-Secret, X-User-Id, X-Session-Id (optional).
                                """))
                .components(new Components()
                        .addSecuritySchemes("gatewaySecret", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(GatewayHeaders.GATEWAY_SECRET))
                        .addSecuritySchemes("userId", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(GatewayHeaders.USER_ID))
                        .addParameters("gatewaySecretParam", new Parameter()
                                .in("header")
                                .name(GatewayHeaders.GATEWAY_SECRET)
                                .required(true)
                                .description("Default local secret: " + gatewaySecret)
                                .example(gatewaySecret))
                        .addParameters("userIdParam", new Parameter()
                                .in("header")
                                .name(GatewayHeaders.USER_ID)
                                .required(true)
                                .example("2")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("gatewaySecret")
                        .addList("userId"));
    }
}
