package com.project.catxi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import io.swagger.v3.oas.models.servers.Server;



@Configuration
public class SwaggerConfig {
	@Bean
	public OpenAPI openAPI() {

		Info info = new Info()
			.title("CATXI API Document")
			.version("1.0")
			.description(
			"CATXI에요");
		//Swagger UI 설정 및 보안 추가
		return new OpenAPI()
			.addServersItem(new Server().url("http://localhost:8080"))
			.components(new Components())
			.info(info);
	}
}
