package com.miranda.luna.runway.infrastructure.web.controller;

import com.miranda.luna.runway.application.service.AccountService;
import com.miranda.luna.runway.infrastructure.web.dto.AccountRequest;
import com.miranda.luna.runway.infrastructure.web.dto.AccountResponse;
import com.miranda.luna.runway.infrastructure.web.dto.ValidateAccountRequest;
import com.miranda.luna.runway.infrastructure.web.dto.ValidateAccountResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runway/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidateAccountResponse> validateCredentials(@RequestBody ValidateAccountRequest request) {
        ValidateAccountResponse response = accountService.validateCredentials(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountService.listAccounts());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable UUID id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok(Map.of("success", true, "deletedId", id));
    }
}
