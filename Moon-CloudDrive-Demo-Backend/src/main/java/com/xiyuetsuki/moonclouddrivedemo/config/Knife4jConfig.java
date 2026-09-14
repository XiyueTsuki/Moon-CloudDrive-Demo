package com.xiyuetsuki.moonclouddrivedemo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Moon-CloudDrive-Demo API 文档")
                        .version("1.0.0")
                        .description("月云盘 - 文件管理、分享、用户系统的 RESTful API 接口文档"));
    }
}