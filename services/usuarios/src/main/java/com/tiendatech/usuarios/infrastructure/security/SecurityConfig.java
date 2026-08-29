package com.tiendatech.usuarios.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // HTML y estáticos
                        .requestMatchers("/", "/index.html", "/*.html", "/favicon.ico").permitAll()
                        .requestMatchers("/assets/**", "/uploads/**",
                                "/css/**", "/js/**", "/img/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        .requestMatchers("/health", "/metrics", "/api/login", "/auth/refresh",
                                "/auth/keepalive", "/auth/logout", "/api/otp/**",
                                "/api/usuarios/crear", "/api/usuarios/recuperar-password",
                                "/api/seguridad/cambiar-password-token").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/provincias/**", "/api/ciudades/**").permitAll()
                        .requestMatchers("/api/auditoria/**", "/api/usuarios/crear-usuario",
                                "/api/usuarios/crear-usuarioAdmin", "/api/usuarios/admin/**")
                                .hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/provincias/**", "/api/ciudades/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT,
                                "/api/provincias/**", "/api/ciudades/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                                "/api/provincias/**", "/api/ciudades/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/buscar", "/api/usuarios/buscar-min")
                                .hasAnyRole("ADMIN", "TRABAJADOR")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"JWT requerido\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Acceso denegado\"}"
                            );
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowCredentials(true);
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
