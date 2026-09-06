package com.transnote.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 本地开发 CORS（T4 前端）：允许 Next.js dev（3000）与桌面端调用 /api/**。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins("http://localhost:3000", "http://127.0.0.1:3000", "tauri://localhost")
        .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
