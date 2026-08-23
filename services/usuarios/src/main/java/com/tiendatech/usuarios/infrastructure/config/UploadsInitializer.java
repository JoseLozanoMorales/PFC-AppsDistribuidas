package com.tiendatech.usuarios.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadsInitializer {

    @Bean
    CommandLineRunner ensureUploadsRoot(@Value("${app.upload-root}") String root) {
        return args -> {
            Path p = Paths.get(root);
            Files.createDirectories(p);
            System.out.println("[uploads] Root: " + p.toAbsolutePath());
        };
    }
}
