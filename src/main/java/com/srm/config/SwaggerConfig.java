package com.srm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SRM 供应商管理系统 API")
                        .version("1.0.0")
                        .description("供应商全生命周期管理 & 采购订单协同管理")
                        .contact(new Contact().name("SRM Team")));
    }
}
