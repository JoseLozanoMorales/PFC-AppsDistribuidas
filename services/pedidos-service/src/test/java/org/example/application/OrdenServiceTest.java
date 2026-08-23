package org.example.application;

import org.example.domain.BusinessMetricsPort;
import org.example.domain.FacturaPort;
import org.example.domain.IdempotenciaRepository;
import org.example.domain.Orden;
import org.example.domain.OrdenRepository;
import org.example.domain.SolicitudIdempotente;
import org.example.domain.CrdbRetryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de OrdenService.generarOrdenDesdeCarrito (checkout), la operacion mas
 * critica de la capa de aplicacion. Mockea OrdenRepository/FacturaPort/
 * IdempotenciaRepository -- no toca CockroachDB.
 *
 * NOTA DE ALCANCE: la creacion de la orden, el insert del detalle, el vaciado
 * del carrito y el calculo de subtotal/IVA/total ocurren TODOS dentro de
 * JdbcOrdenRepository.crear() (infrastructure.persistence), no aqui. Desde el
 * punto de vista de OrdenService esa llamada es una caja negra: estos tests
 * verifican que OrdenService la invoca con los argumentos correctos y que
 * propaga su resultado (o su fallo) sin alterarlo -- no pueden, ni deben,
 * verificar la aritmetica interna del repositorio.
 */
@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    @Mock
    private OrdenRepository ordenRepository;
    @Mock
    private FacturaPort facturaClient;
    @Mock
    private ObjectProvider<CrdbRetryPort> crdbRetryExecutor;
    @Mock
    private IdempotenciaRepository idempotenciaRepository;
    @Mock
    private BusinessMetricsPort businessMetrics;

    private static final Integer USUARIO_ID = 8;
    private static final Integer DIRECCION_ID = 3;
    private static final Integer METODOPAGO_ID = 2;

    private OrdenService servicioConIdempotencia(boolean habilitada) {
        Optional<IdempotenciaRepository> repo = habilitada ? Optional.of(idempotenciaRepository) : Optional.empty();
        return new OrdenService(ordenRepository, facturaClient, crdbRetryExecutor, repo, businessMetrics);
    }

    private static Orden ordenDe(Integer ordenId, BigDecimal subtotal, BigDecimal total) {
        return new Orden(ordenId, USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, subtotal, total, LocalDate.of(2026, 8, 18));
    }

    // Espeja OrdenService.calcularPayloadHash (metodo privado): mismo algoritmo
    // (SHA-256 de "direccionId|metodopagoId"), solo para poder construir en el
    // test un SolicitudIdempotente con el hash que el servicio va a comparar.
    private static String payloadHashEsperado(Integer direccionId, Integer metodopagoId) {
        String canonico = direccionId + "|" + metodopagoId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Flujo feliz
    // ------------------------------------------------------------------

    @Test
    void checkout_flujoFeliz_creaOrdenYFacturaSinIdempotencyKey() {
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        Orden creada = ordenDe(55, new BigDecimal("100.00"), new BigDecimal("115.00"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null)).thenReturn(creada);
        when(facturaClient.generarFactura(55)).thenReturn(900);

        Orden resultado = servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null);

        assertThat(resultado).isSameAs(creada);
        verify(ordenRepository).crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null);
        verify(facturaClient).generarFactura(55);
        verify(businessMetrics).registrarCheckoutCompletado();
        verify(businessMetrics, never()).registrarCheckoutFallido(any());
    }

    @Test
    void checkout_totalesSonLosQueDevuelveElRepositorio_sinRecalculoEnLaCapaDeAplicacion() {
        // El subtotal/IVA/total real los calcula JdbcOrdenRepository.crear() con
        // los precios de productos-service (ver infrastructure.persistence).
        // OrdenService es transparente: este test documenta que NO los toca ni
        // los recalcula, solo devuelve exactamente lo que recibio.
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        Orden creada = ordenDe(55, new BigDecimal("246.75"), new BigDecimal("283.76"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null)).thenReturn(creada);
        when(facturaClient.generarFactura(55)).thenReturn(900);

        Orden resultado = servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null);

        assertThat(resultado.getSubtotal()).isEqualByComparingTo("246.75");
        assertThat(resultado.getTotal()).isEqualByComparingTo("283.76");
    }

    // ------------------------------------------------------------------
    // Carrito vacio
    // ------------------------------------------------------------------

    @Test
    void checkout_carritoVacio_propagaLaFallaYNuncaFactura() {
        // La validacion "carrito vacio" vive dentro de JdbcOrdenRepository.crear()
        // (lanza IllegalStateException). Desde OrdenService, el contrato que
        // importa es: propagar el fallo tal cual, sin llamar a facturacion.
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null))
                .thenThrow(new IllegalStateException("El carrito 42 está vacío"));

        assertThatThrownBy(() -> servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vacío");

        verify(facturaClient, never()).generarFactura(any());
        verify(businessMetrics).registrarCheckoutFallido("carrito_vacio");
        verify(businessMetrics, never()).registrarCheckoutCompletado();
    }

    @Test
    void checkout_datosInvalidos_propagaLaFallaConMotivoValidacion() {
        // Usuario/direccion/metodo de pago invalidos tambien se validan dentro
        // de JdbcOrdenRepository.crear() (IllegalArgumentException). Motivo
        // distinto a "carrito_vacio" para no mezclar ambas causas en Grafana.
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null))
                .thenThrow(new IllegalArgumentException("La direccion 3 no pertenece al usuario 8"));

        assertThatThrownBy(() -> servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(facturaClient, never()).generarFactura(any());
        verify(businessMetrics).registrarCheckoutFallido("validacion");
    }

    // ------------------------------------------------------------------
    // Idempotencia
    // ------------------------------------------------------------------

    @Test
    void checkout_idempotencia_mismaClaveMismoPayload_devuelveOrdenExistenteSinRecrear() {
        String clave = "clave-abc-123";
        String hash = payloadHashEsperado(DIRECCION_ID, METODOPAGO_ID);
        Orden existente = ordenDe(30, new BigDecimal("50.00"), new BigDecimal("57.50"));

        when(idempotenciaRepository.buscarPorUsuarioYClave(USUARIO_ID, clave))
                .thenReturn(Optional.of(new SolicitudIdempotente(USUARIO_ID, clave, hash, 30, Instant.now())));
        when(ordenRepository.obtenerPorId(30)).thenReturn(existente);

        Orden resultado = servicioConIdempotencia(true)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, clave);

        assertThat(resultado).isSameAs(existente);
        verify(ordenRepository, never()).crear(any(), any(), any(), any(), any());
        verify(facturaClient, never()).generarFactura(any());
        // Replay idempotente: no es un checkout NUEVO, no debe sumar ni a
        // completado ni a fallido -- ver nota de diseño en el mensaje al usuario.
        verify(businessMetrics, never()).registrarCheckoutCompletado();
        verify(businessMetrics, never()).registrarCheckoutFallido(any());
    }

    @Test
    void checkout_idempotencia_mismaClaveDistintoPayload_lanza409() {
        String clave = "clave-abc-123";
        String hashDeOtraPeticion = "hash-que-no-coincide-con-el-payload-actual";

        when(idempotenciaRepository.buscarPorUsuarioYClave(USUARIO_ID, clave))
                .thenReturn(Optional.of(new SolicitudIdempotente(USUARIO_ID, clave, hashDeOtraPeticion, 30, Instant.now())));

        assertThatThrownBy(() -> servicioConIdempotencia(true)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, clave))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(ordenRepository, never()).crear(any(), any(), any(), any(), any());
        verify(facturaClient, never()).generarFactura(any());
        verify(businessMetrics).registrarCheckoutFallido("idempotencia_conflicto");
    }

    @Test
    void checkout_idempotencia_banderaDesactivada_ignoraHeaderYSeComportaComoSinClave() {
        // idempotenciaRepository = Optional.empty() simula
        // pedidos.idempotencia.enabled=false (ver application.properties): el
        // bean no se registra. Aunque el header SI viene, el servicio debe
        // ignorarlo por completo -- mismo comportamiento que sin header.
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        Orden creada = ordenDe(55, new BigDecimal("100.00"), new BigDecimal("115.00"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null)).thenReturn(creada);
        when(facturaClient.generarFactura(55)).thenReturn(900);

        Orden resultado = servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, "esta-clave-se-debe-ignorar");

        assertThat(resultado).isSameAs(creada);
        // claveParaPersistencia y payloadHash deben llegar en null al
        // repositorio: exactamente como si nunca hubiera existido el header.
        verify(ordenRepository).crear(eq(USUARIO_ID), eq(DIRECCION_ID), eq(METODOPAGO_ID), isNull(), isNull());
        verify(idempotenciaRepository, never()).buscarPorUsuarioYClave(any(), any());
        verify(businessMetrics).registrarCheckoutCompletado();
    }

    @Test
    void checkout_idempotencia_banderaActiva_claveNuevaAunNoVista_creaYRegistra() {
        // Idempotencia habilitada Y header presente, pero es la PRIMERA vez que
        // se ve esta clave (buscarPorUsuarioYClave no encuentra nada todavia):
        // debe seguir el flujo normal y pasar la clave + su hash al repositorio
        // para que los registre en la misma transaccion (ver JdbcOrdenRepository).
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        String clave = "clave-nueva-primera-vez";
        String hashEsperado = payloadHashEsperado(DIRECCION_ID, METODOPAGO_ID);
        when(idempotenciaRepository.buscarPorUsuarioYClave(USUARIO_ID, clave)).thenReturn(Optional.empty());
        Orden creada = ordenDe(56, new BigDecimal("100.00"), new BigDecimal("115.00"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, clave, hashEsperado)).thenReturn(creada);
        when(facturaClient.generarFactura(56)).thenReturn(901);

        Orden resultado = servicioConIdempotencia(true)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, clave);

        assertThat(resultado).isSameAs(creada);
        verify(ordenRepository).crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, clave, hashEsperado);
        verify(facturaClient).generarFactura(56);
        verify(businessMetrics).registrarCheckoutCompletado();
    }

    @Test
    void checkout_idempotencia_headerVacioOEnBlanco_seTrataIgualQueSinHeader() {
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        Orden creada = ordenDe(55, new BigDecimal("100.00"), new BigDecimal("115.00"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null)).thenReturn(creada);
        when(facturaClient.generarFactura(55)).thenReturn(900);

        servicioConIdempotencia(true)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, "   ");

        verify(ordenRepository).crear(eq(USUARIO_ID), eq(DIRECCION_ID), eq(METODOPAGO_ID), isNull(), isNull());
        verify(idempotenciaRepository, never()).buscarPorUsuarioYClave(any(), any());
        verify(businessMetrics).registrarCheckoutCompletado();
    }

    @Test
    void checkout_facturacionFalla_ordenYaCreadaPeroLanzaIllegalStateException() {
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(null);
        Orden creada = ordenDe(55, new BigDecimal("100.00"), new BigDecimal("115.00"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null)).thenReturn(creada);
        when(facturaClient.generarFactura(55)).thenThrow(new RuntimeException("ventas-service no responde"));

        assertThatThrownBy(() -> servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("55")
                .hasMessageContaining("facturación");

        verify(businessMetrics).registrarCheckoutFallido("facturacion");
        verify(businessMetrics, never()).registrarCheckoutCompletado();
    }

    // ------------------------------------------------------------------
    // Retry via CrdbRetryPort (cuando el bean SI esta disponible)
    // ------------------------------------------------------------------

    @Test
    void checkout_conRetryDisponible_ejecutaCrearATravesDelRetryPort() {
        CrdbRetryPort retryPort = new CrdbRetryPort() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> operation) {
                return operation.get();
            }
        };
        when(crdbRetryExecutor.getIfAvailable()).thenReturn(retryPort);
        Orden creada = ordenDe(55, new BigDecimal("100.00"), new BigDecimal("115.00"));
        when(ordenRepository.crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null)).thenReturn(creada);
        when(facturaClient.generarFactura(55)).thenReturn(900);

        Orden resultado = servicioConIdempotencia(false)
                .generarOrdenDesdeCarrito(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null);

        assertThat(resultado).isSameAs(creada);
        verify(ordenRepository).crear(USUARIO_ID, DIRECCION_ID, METODOPAGO_ID, null, null);
        verify(businessMetrics).registrarCheckoutCompletado();
    }
}
