package org.example.mercenary.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mercenary High API Specification")
                        .version("v1.0")
                        .description("Redis Geo, 분산 락이 적용된 축구 용병 매칭 서비스 API 명세서")
                );
    }
}