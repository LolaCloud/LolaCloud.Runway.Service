package com.miranda.luna.runway.infrastructure.web.controller;

import com.miranda.luna.runway.application.service.DnsService;
import com.miranda.luna.runway.infrastructure.web.dto.AutoSyncRequest;
import com.miranda.luna.runway.infrastructure.web.dto.DnsRecordRequest;
import com.miranda.luna.runway.infrastructure.web.dto.DnsRecordResponse;
import com.miranda.luna.runway.infrastructure.web.dto.DnsZoneResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runway")
public class DnsController {

    private final DnsService dnsService;

    public DnsController(DnsService dnsService) {
        this.dnsService = dnsService;
    }

    @GetMapping("/zones")
    public ResponseEntity<List<DnsZoneResponse>> listZones() {
        return ResponseEntity.ok(dnsService.listZones());
    }

    @GetMapping("/zones/{zoneId}")
    public ResponseEntity<DnsZoneResponse> getZone(@PathVariable String zoneId) {
        return dnsService.listZones().stream()
                .filter(z -> z.id().equalsIgnoreCase(zoneId) || z.name().equalsIgnoreCase(zoneId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/records")
    public ResponseEntity<List<DnsRecordResponse>> listRecords(
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) Boolean autoSync
    ) {
        return ResponseEntity.ok(dnsService.listRecords(zoneId, autoSync));
    }

    @GetMapping("/zones/{zoneId}/records")
    public ResponseEntity<List<DnsRecordResponse>> listRecordsByZone(
            @PathVariable String zoneId,
            @RequestParam(required = false) Boolean autoSync
    ) {
        return ResponseEntity.ok(dnsService.listRecords(zoneId, autoSync));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<DnsRecordResponse> getRecord(@PathVariable UUID id) {
        return ResponseEntity.ok(dnsService.getRecordById(id));
    }

    @PostMapping("/records")
    public ResponseEntity<DnsRecordResponse> createRecord(@RequestBody DnsRecordRequest request) {
        DnsRecordResponse response = dnsService.createRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<DnsRecordResponse> updateRecord(
            @PathVariable UUID id,
            @RequestBody DnsRecordRequest request
    ) {
        DnsRecordResponse response = dnsService.updateRecord(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> deleteRecord(@PathVariable UUID id) {
        dnsService.deleteRecord(id);
        return ResponseEntity.ok(Map.of("success", true, "deletedId", id));
    }

    @PatchMapping("/records/{id}/auto-sync")
    public ResponseEntity<DnsRecordResponse> toggleAutoSync(
            @PathVariable UUID id,
            @RequestBody AutoSyncRequest request
    ) {
        DnsRecordResponse response = dnsService.toggleAutoSync(id, request.autoSync());
        return ResponseEntity.ok(response);
    }
}
