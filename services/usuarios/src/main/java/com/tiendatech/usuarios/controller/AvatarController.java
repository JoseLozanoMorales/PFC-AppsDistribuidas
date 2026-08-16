package com.tiendatech.usuarios.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/usuarios")
public class AvatarController {

    @Value("${app.upload-root}")
    private String uploadRoot;

    private final JdbcTemplate jdbc;

    public AvatarController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final long MAX_BYTES = DataSize.ofMegabytes(5).toBytes();
    private static final Set<String> ALLOWED_CT = Set.of("image/png", "image/jpeg", "image/webp");

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@PathVariable Integer id,
                                          @RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) return bad("Archivo vacio");
        if (file.getSize() > MAX_BYTES) return bad("Archivo supera el limite");

        String ct = Optional.ofNullable(file.getContentType()).orElse("");
        if (!ALLOWED_CT.contains(ct)) return bad("Formato no permitido (usa PNG/JPEG/WebP)");

        BufferedImage img = ImageIO.read(file.getInputStream());
        if (img == null) return bad("El archivo no es una imagen valida");

        Path baseDir = Paths.get(uploadRoot, "avatars", id.toString());
        Files.createDirectories(baseDir);

        Path out = baseDir.resolve("avatar.png");
        try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ImageIO.write(img, "png", os);
        }

        String publicUrl = "/uploads/avatars/" + id + "/avatar.png";
        int updated = jdbc.update(
                "UPDATE usuarios.usuario SET avatar_path = ? WHERE usuario_id = ?",
                publicUrl, id
        );
        if (updated == 0) return bad("Usuario no encontrado");

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "usuario_id", id,
                "url", publicUrl
        ));
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<?> removeAvatar(@PathVariable Integer id) {
        jdbc.update("UPDATE usuarios.usuario SET avatar_path = NULL WHERE usuario_id = ?", id);

        Path out = Paths.get(uploadRoot, "avatars", id.toString(), "avatar.png");
        try {
            Files.deleteIfExists(out);
        } catch (IOException ignored) {
        }

        return ResponseEntity.ok(Map.of("ok", true, "usuario_id", id));
    }

    private static ResponseEntity<Map<String, Object>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "error", msg));
    }
}
