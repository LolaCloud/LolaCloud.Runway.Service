package com.miranda.luna.runway.infrastructure.web.controller;

import com.miranda.luna.runway.application.service.TunnelService;
import com.miranda.luna.runway.infrastructure.web.dto.AddRouteRequest;
import com.miranda.luna.runway.infrastructure.web.dto.ApiResponse;
import com.miranda.luna.runway.infrastructure.web.dto.ExposePlatformRequest;
import com.miranda.luna.runway.infrastructure.web.dto.ExposePlatformResponse;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelRequest;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelResponse;
import com.miranda.luna.runway.infrastructure.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/runway/tunnels")
public class TunnelController {

    private final TunnelService tunnelService;

    public TunnelController(TunnelService tunnelService) {
        this.tunnelService = tunnelService;
    }

    @GetMapping
    public ResponseEntity<List<TunnelResponse>> listTunnels() {
        return ResponseEntity.ok(tunnelService.listTunnels());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TunnelResponse> getTunnel(@PathVariable String id) {
        return ResponseEntity.ok(tunnelService.getTunnelById(id));
    }

    @GetMapping("/{id}/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTunnelToken(@PathVariable String id, HttpServletRequest request) {
        String token = tunnelService.getTunnelToken(id);
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.of(Map.of("token", token), requestId));
    }

    @GetMapping(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String id) {
        return tunnelService.streamTunnelLogs(id);
    }

    @PostMapping
    public ResponseEntity<TunnelResponse> createTunnel(@RequestBody TunnelRequest request) {
        TunnelResponse response = tunnelService.createTunnel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/routes")
    public ResponseEntity<TunnelResponse> addRoute(
            @PathVariable String id,
            @RequestBody AddRouteRequest routeRequest
    ) {
        TunnelResponse response = tunnelService.addRoute(id, routeRequest);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/routes/{index}")
    public ResponseEntity<TunnelResponse> deleteRouteByIndex(
            @PathVariable String id,
            @PathVariable String index
    ) {
        try {
            int idx = Integer.parseInt(index);
            TunnelResponse response = tunnelService.deleteRoute(id, idx);
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            TunnelResponse response = tunnelService.deleteRouteByHostname(id, index);
            return ResponseEntity.ok(response);
        }
    }

    @DeleteMapping("/{id}/routes")
    public ResponseEntity<TunnelResponse> deleteRoute(
            @PathVariable String id,
            @RequestParam(required = false) String hostname,
            @RequestParam(required = false) Integer index
    ) {
        if (index != null) {
            return ResponseEntity.ok(tunnelService.deleteRoute(id, index));
        }
        return ResponseEntity.ok(tunnelService.deleteRouteByHostname(id, hostname));
    }

    @PostMapping("/sync")
    public ResponseEntity<List<TunnelResponse>> syncTunnels() {
        return ResponseEntity.ok(tunnelService.syncTunnels());
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<TunnelResponse> syncTunnel(@PathVariable String id) {
        return ResponseEntity.ok(tunnelService.syncTunnel(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTunnel(@PathVariable String id) {
        tunnelService.deleteTunnel(id);
        return ResponseEntity.ok(Map.of("success", true, "deletedId", id));
    }

    @PostMapping("/expose-platform")
    public ResponseEntity<ApiResponse<ExposePlatformResponse>> exposePlatform(
            @RequestBody(required = false) ExposePlatformRequest request,
            HttpServletRequest servletRequest
    ) {
        ExposePlatformResponse response = tunnelService.exposePlatform(request);
        String requestId = (String) servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.of(response, requestId));
    }

    @GetMapping("/expose-platform/status")
    public ResponseEntity<ApiResponse<ExposePlatformResponse>> getPlatformExposeStatus(
            HttpServletRequest servletRequest
    ) {
        ExposePlatformResponse response = tunnelService.getPlatformExposeStatus();
        String requestId = (String) servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.of(response, requestId));
    }
}
