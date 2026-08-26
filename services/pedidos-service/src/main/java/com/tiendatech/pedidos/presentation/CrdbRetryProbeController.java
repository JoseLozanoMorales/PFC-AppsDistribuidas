package com.tiendatech.pedidos.presentation;

import com.tiendatech.pedidos.application.CrdbRetryProbeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/crdb")
public class CrdbRetryProbeController {

    private final CrdbRetryProbeService probeService;

    public CrdbRetryProbeController(CrdbRetryProbeService probeService) {
        this.probeService = probeService;
    }

    @PostMapping("/retry-probe")
    public Map<String, Object> provocarColision() {
        return probeService.provocarColision();
    }
}
