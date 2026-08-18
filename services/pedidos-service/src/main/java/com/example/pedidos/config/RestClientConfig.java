package com.example.pedidos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory(
            @Value("${pedidos.http-client.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${pedidos.http-client.read-timeout-ms:5000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(ClientHttpRequestFactory clientHttpRequestFactory) {
        return RestClient.builder().requestFactory(clientHttpRequestFactory);
    }
}
