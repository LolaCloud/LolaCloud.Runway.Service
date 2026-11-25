package com.miranda.luna.runway;

import com.miranda.luna.runway.application.service.AccountService;
import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.model.EdgeTunnel;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.web.dto.AccountRequest;
import com.miranda.luna.runway.infrastructure.web.dto.AccountResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountImmediateSyncIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private CloudflareAccountRepositoryPort accountRepository;

    @Autowired
    private DnsRecordRepositoryPort recordRepository;

    @Autowired
    private EdgeTunnelRepositoryPort tunnelRepository;

    private UUID createdAccountId;

    @AfterEach
    void tearDown() {
        if (createdAccountId != null) {
            try {
                accountService.deleteAccount(createdAccountId);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("createAccount immediately synchronizes zones, DNS records, and tunnels into PostgreSQL")
    void testImmediateSyncOnAccountCreation() {
        String testEmail = "operator-sync-" + UUID.randomUUID().toString().substring(0, 8) + "@lola-cloud.dev";
        String testApiKey = "abcdef0123456789abcdef0123456789";

        AccountRequest request = new AccountRequest(
                "Immediate Sync Test Account",
                testEmail,
                testApiKey
        );

        AccountResponse response = accountService.createAccount(request);
        createdAccountId = response.id();

        assertNotNull(response.id());
        assertEquals("Immediate Sync Test Account", response.name());
        assertEquals(testEmail, response.email());
        assertTrue(response.zonesCount() >= 2, "Expected at least 2 zones synced");
        assertTrue(response.tunnelsCount() >= 2, "Expected at least 2 tunnels synced");

        // Verify DNS records in PostgreSQL database
        List<DnsRecord> syncedRecords = recordRepository.findByAccountId(createdAccountId);
        assertFalse(syncedRecords.isEmpty(), "DNS records must be immediately populated in PostgreSQL");

        // Verify that records of multiple types (A, CNAME, etc.) are present with NO filtering
        assertTrue(syncedRecords.stream().anyMatch(r -> "A".equalsIgnoreCase(r.getType())), "Expected A records");
        assertTrue(syncedRecords.stream().anyMatch(r -> "CNAME".equalsIgnoreCase(r.getType())), "Expected CNAME records");

        // Verify auto_sync is false on newly synced records
        assertTrue(syncedRecords.stream().noneMatch(DnsRecord::isAutoSync), "Synced records should default auto_sync = false");

        // Verify tunnels in PostgreSQL database
        List<EdgeTunnel> syncedTunnels = tunnelRepository.findByAccountId(createdAccountId);
        assertFalse(syncedTunnels.isEmpty(), "Tunnels must be immediately populated in PostgreSQL");
        assertTrue(syncedTunnels.stream().anyMatch(t -> t.getName().toLowerCase().contains("tunnel") || "Kali".equalsIgnoreCase(t.getName()) || "natan".equalsIgnoreCase(t.getName())), "Expected synced tunnel names");
    }
}
