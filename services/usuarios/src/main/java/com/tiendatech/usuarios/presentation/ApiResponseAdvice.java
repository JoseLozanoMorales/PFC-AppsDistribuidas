package com.tiendatech.usuarios.presentation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;
import java.util.Map;

/** Contrato transversal obligatorio de respuestas JSON del Paso 4. */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    public record ApiResponse(int status, Object data, String message, Instant timestamp) {}

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (debeOmitir(body, contentType, request)) {
            return body;
        }
        int status = status(response);
        return new ApiResponse(status, body, mensaje(body, status), Instant.now());
    }

    private static boolean debeOmitir(Object body, MediaType contentType, ServerHttpRequest request) {
        return rutaExcluida(path(request)) || cuerpoExcluido(body) || contenidoBinario(contentType);
    }

    private static String path(ServerHttpRequest request) {
        return request instanceof ServletServerHttpRequest servlet ? servlet.getServletRequest().getRequestURI() : "";
    }

    private static boolean rutaExcluida(String path) {
        return path.startsWith("/actuator") || path.startsWith("/internal/")
                || path.equals("/health") || path.equals("/metrics");
    }

    private static boolean cuerpoExcluido(Object body) {
        return body instanceof ApiResponse || body instanceof Resource || body instanceof byte[];
    }

    private static boolean contenidoBinario(MediaType contentType) {
        return contentType != null
                && (MediaType.APPLICATION_OCTET_STREAM.includes(contentType) || "image".equals(contentType.getType()));
    }

    private static int status(ServerHttpResponse response) {
        return response instanceof ServletServerHttpResponse servlet ? servlet.getServletResponse().getStatus() : 200;
    }

    private static String mensaje(Object body, int status) {
        if (body instanceof Map<?, ?> map && map.get("message") != null) {
            return String.valueOf(map.get("message"));
        }
        return status < 400 ? "OK" : "Error";
    }
}