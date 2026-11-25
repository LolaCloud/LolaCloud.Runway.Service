package com.miranda.luna.runway.infrastructure.web.controller;

import com.miranda.luna.runway.application.service.EdgeStatusService;
import com.miranda.luna.runway.infrastructure.web.dto.AutoIpFixResponse;
import com.miranda.luna.runway.infrastructure.web.dto.CloudflaredStatusResponse;
import com.miranda.luna.runway.infrastructure.web.dto.EdgeIpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runway")
public class EdgeController {

    private final EdgeStatusService edgeStatusService;

    public EdgeController(EdgeStatusService edgeStatusService) {
        this.edgeStatusService = edgeStatusService;
    }

    @GetMapping("/cloudflared/status")
    public ResponseEntity<CloudflaredStatusResponse> getCloudflaredStatus() {
        return ResponseEntity.ok(edgeStatusService.getCloudflaredStatus());
    }

    @GetMapping("/edge-ip")
    public ResponseEntity<EdgeIpResponse> getEdgeIpStatus() {
        return ResponseEntity.ok(edgeStatusService.getEdgeIpStatus());
    }

    @PostMapping("/edge-ip/fix")
    public ResponseEntity<AutoIpFixResponse> fixEdgeIp() {
        return ResponseEntity.ok(edgeStatusService.fixEdgeIp());
    }
}
