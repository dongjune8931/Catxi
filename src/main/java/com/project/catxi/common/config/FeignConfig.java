package com.project.catxi.common.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.project.catxi.common.auth.kakao")
public class FeignConfig {

}
