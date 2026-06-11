package com.cinta.matchmateserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    public OpenAPI matchMateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MatchMate API")
                        .description("MatchMate 接口文档")
                        .version("1.0.0"));
    }
}
