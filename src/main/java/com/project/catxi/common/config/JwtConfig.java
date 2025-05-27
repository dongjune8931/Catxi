package com.project.catxi.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "spring.jwt")
public class JwtConfig {

  private String header;
  private String secret;
  private Long accessTokenValidityInSeconds;

}
