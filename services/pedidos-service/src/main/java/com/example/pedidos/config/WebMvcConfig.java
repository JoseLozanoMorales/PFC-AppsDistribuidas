package com.example.pedidos.config;

import com.example.pedidos.security.AuthUsuarioArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthUsuarioArgumentResolver authUsuarioArgumentResolver;

    public WebMvcConfig(AuthUsuarioArgumentResolver authUsuarioArgumentResolver) {
        this.authUsuarioArgumentResolver = authUsuarioArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUsuarioArgumentResolver);
    }
}
