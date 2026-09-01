package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.domain.port.out.EmailPort;
import com.tiendatech.usuarios.domain.port.out.OtpStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final EmailPort emailPort;
    private final OtpStorePort otpStore;

    @Value("${otp.ttl-minutes:10}")
    private int otpTtlMin;

    @Value("${otp.length:6}")
    private int otpLen;

    @Value("${otp.resend.cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${otp.rate.max-per-15min:3}")
    private int maxPerWindow;

    @Value("${otp.mail-fallback.enabled:${OTP_MAIL_FALLBACK_ENABLED:false}}")
    private boolean mailFallbackEnabled;

    //NUEVO
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private String buildPasswordChangeUrl() {
        // evita doble slash y apunta a tu página
        String base = (frontendBaseUrl == null) ? "" : frontendBaseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length()-1);
        return base + "/ActualizarContrasenia.html";
    }

    private static final SecureRandom RAND = new SecureRandom();

    private static String key(String email, String txId) { return email + ":" + txId; }
    private static String rateKey(String email) { return "rate:" + email.toLowerCase(); }

    private boolean canSendForEmail(String email) {
        String k = rateKey(email);
        Integer current = otpStore.findAttemptCount(k);
        if (current == null) current = 0;
        if (current >= maxPerWindow) return false;
        otpStore.saveAttemptCount(k, current + 1);
        return true;
    }

    public Map<String, Object> enviar(String email, String txIdReuso) {
        if (!canSendForEmail(email)) {
            throw new OtpTooManyRequestsException("Demasiados envíos para este correo. Intenta más tarde.");
        }

        String txId = resolverTxId(txIdReuso);
        String code = generarCodigoOtp();

        String hash = BCrypt.hashpw(code, BCrypt.gensalt());
        String cacheKey = key(email, txId);
        otpStore.saveOtpHash(cacheKey, hash);

        String body = """
                Tu código de verificación es: %s
                Expira en %d minutos.
                Si no solicitaste este código, ignora este correo.
                """.formatted(code, otpTtlMin);

        boolean mailSent = enviarOtpOFallback(email, txId, code, cacheKey, body);
        return construirRespuestaEnvio(txId, email, mailSent, code);
    }

    private static String resolverTxId(String txIdReuso) {
        return (txIdReuso != null && !txIdReuso.isBlank()) ? txIdReuso : UUID.randomUUID().toString();
    }

    private boolean enviarOtpOFallback(String email, String txId, String code, String cacheKey, String body) {
        try {
            emailPort.send(email, "Código de verificación - TiendaTech", body);
            log.info("OTP enviado a {} txId={}", email, txId);
            return true;
        } catch (Exception ex) {
            manejarFalloEnvioOtp(email, txId, code, cacheKey, ex);
            return false;
        }
    }

    // no dejes un OTP huérfano si el correo falló
    private void manejarFalloEnvioOtp(String email, String txId, String code, String cacheKey, Exception ex) {
        if (!mailFallbackEnabled) {
            otpStore.removeOtp(cacheKey);
            log.error("Error enviando OTP a {}: {}", email, ex.getMessage(), ex);
            throw new RuntimeException("MAIL_SEND_FAILED: " + ex.getMessage(), ex);
        }
        log.warn("SMTP no disponible. Fallback local activo para OTP. correo={} txId={} otp={}",
                email, txId, code, ex);
    }

    private Map<String, Object> construirRespuestaEnvio(String txId, String email, boolean mailSent, String code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("txId", txId);
        response.put("correo", email);
        response.put("expiresInMin", otpTtlMin);
        response.put("resendCooldownSec", resendCooldownSeconds);
        response.put("now", Instant.now().toString());
        response.put("mailSent", mailSent);
        if (!mailSent && mailFallbackEnabled) {
            response.put("devCode", code);
            response.put("message", "SMTP no disponible; usa devCode solo para pruebas locales.");
        }
        return response;
    }

    private String generarCodigoOtp() {
        int bound = (int) Math.pow(10, otpLen);
        return String.format("%0" + otpLen + "d", RAND.nextInt(bound));
    }

    public boolean validar(String email, String code, String txId) {
        String cacheKey = key(email, txId);
        String hash = otpStore.findOtpHash(cacheKey);
        if (hash == null) return false; // expirado / inexistente
        boolean ok = BCrypt.checkpw(code, hash);
        if (ok) {
            otpStore.removeOtp(cacheKey);
        }
        return ok;
    }

    public static class OtpTooManyRequestsException extends RuntimeException {
        public OtpTooManyRequestsException(String msg) { super(msg); }
    }


    /* ==================== NUEVO: envío de credenciales ==================== */

    // Conjunto "normal": mayúsculas, minúsculas, dígitos. Sin caracteres raros.
    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray(); // sin I/O
    private static final char[] LOWER = "abcdefghijkmnopqrstuvwxyz".toCharArray(); // sin l
    private static final char[] DIGIT = "23456789".toCharArray(); // sin 0/1
    private static final char[] ALL;

    static {
        String all = new String(UPPER) + new String(LOWER) + new String(DIGIT);
        ALL = all.toCharArray();
    }

    /* Genera una contraseña legible (por defecto 12). Incluye al menos 1 mayúscula, 1 minúscula y 1 dígito. */
    public String generarPasswordLegible(int len) {
        int L = Math.max(8, Math.min(len <= 0 ? 12 : len, 32)); // 8..32
        List<Character> buf = new ArrayList<>(L);

        // Garantizar clases
        buf.add(UPPER[RAND.nextInt(UPPER.length)]);
        buf.add(LOWER[RAND.nextInt(LOWER.length)]);
        buf.add(DIGIT[RAND.nextInt(DIGIT.length)]);

        while (buf.size() < L) {
            buf.add(ALL[RAND.nextInt(ALL.length)]);
        }

        // Mezclar
        Collections.shuffle(buf, RAND);

        StringBuilder sb = new StringBuilder(L);
        for (char c : buf) sb.append(c);
        return sb.toString();
    }

    /* Envía un correo con las credenciales (usuario + password en claro). Lanza excepción si falla. */
    public void enviarCredenciales(String correo, String usuario, String passwordPlano) {

        // 1) Genera token y URL con token
        String token = emitirTokenCambioPassword(correo);
        String urlCambio = buildPasswordChangeUrl() + "?t=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        String body = """
        ¡Hola!

        Se ha creado tu cuenta en TiendaTech.

        Usuario: %s
        Contraseña temporal: %s

        Por seguridad, debes cambiar tu contraseña:
        %s

        Si no solicitaste esta cuenta, ignora este correo.

        Saludos,
        TiendaTech
        """.formatted(
                (usuario == null || usuario.isBlank()) ? "(asignado por sistema)" : usuario,
                passwordPlano,
                urlCambio
        );

        emailPort.send(correo, "Tus credenciales de acceso - TiendaTech", body);
    }

    /*Genera una contraseña legible y la envía por correo. */
    public String generarYEnviarCredenciales(String correo, String usuario, Integer length) {
        int L = (length == null ? 12 : length.intValue());
        String pwd = generarPasswordLegible(L);
        enviarCredenciales(correo, usuario, pwd); // si esto falla, que reviente
        return pwd;
    }

    //nuevooooo


    public void enviarPasswordTemporalRecuperacion(String correo, String usuario, String passwordPlano) {
        String token = emitirTokenCambioPassword(correo);
        String urlCambio = buildPasswordChangeUrl() + "?t=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        String body = """
            ¡Hola %s!

            Generamos una contraseña temporal para que puedas ingresar:

            %s

            Luego, cámbiala aquí:
            %s

            Si no solicitaste este cambio, ignora este correo o contáctanos.

            -- TiendaTech
            """.formatted(
                (usuario == null || usuario.isBlank()) ? "usuario" : usuario,
                passwordPlano,
                urlCambio
        );
        emailPort.send(correo, "Recuperación de contraseña - TiendaTech", body);
    }

    // emitir/consumir
    public String emitirTokenCambioPassword(String correo) {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        otpStore.savePasswordChangeToken(token, correo.toLowerCase());
        return token;
    }

    public String peekTokenCambioPassword(String token) {
        return otpStore.findPasswordChangeEmail(token); // NO invalida
    }
    public void invalidateTokenCambioPassword(String token) {
        otpStore.removePasswordChangeToken(token);
    }
}
