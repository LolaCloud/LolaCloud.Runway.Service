package com.miranda.luna.runway.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ValidateAccountRequest(
        String email,
        @JsonAlias({"globalApiKey", "global_api_key", "apiKey"})
        String apiKey
) {}
