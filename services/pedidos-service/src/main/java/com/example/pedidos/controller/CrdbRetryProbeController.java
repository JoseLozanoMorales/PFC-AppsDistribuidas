package com.example.pedidos.controller;

import com.example.pedidos.service.CrdbRetryProbeService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("crdb")
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
