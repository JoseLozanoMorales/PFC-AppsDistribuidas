package org.example.infrastructure.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Reenvía el JWT de la solicitud entrante en llamadas HTTP internas.
 * Las identidades X-User-* siguen siendo responsabilidad del gateway; los
 * servicios de destino validan nuevamente el token y no confían en cabeceras
 * de identidad fabricadas por otro servicio.
 */
@Component
public class InboundAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String authorization = currentAuthorization();
        if (authorization != null && !authorization.isBlank()) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return execution.execute(request, body);
    }

    String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest inbound = attributes.getRequest();
        return inbound.getHeader(HttpHeaders.AUTHORIZATION);
    }
}
