package com.tiendatech.usuarios.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload-root}")
    private String uploadRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadRoot).toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
