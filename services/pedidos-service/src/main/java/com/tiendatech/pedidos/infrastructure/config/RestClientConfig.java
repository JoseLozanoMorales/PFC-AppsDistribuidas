package com.tiendatech.pedidos.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

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

    // Este servicio construye su propio RestClient.Builder (para fijar los
    // timeouts de arriba), lo que reemplaza el bean autoconfigurado por Spring
    // Boot y con el la aplicacion automatica de RestClientCustomizer -- entre
    // ellos, el que propaga el contexto de trazado (traceparent) en cada
    // llamada saliente (FacturaClient, ProductoClient, UsuarioClient). Sin
    // aplicar aqui esos customizers a mano, las llamadas de pedidos-service
    // hacia los demas servicios quedarian fuera de la traza distribuida del
    // Paso 10 pese a que el resto del sistema si la propaga.
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(ClientHttpRequestFactory clientHttpRequestFactory,
                                                 List<RestClientCustomizer> customizers) {
        RestClient.Builder builder = RestClient.builder().requestFactory(clientHttpRequestFactory);
        customizers.forEach(customizer -> customizer.customize(builder));
        return builder;
    }
}
