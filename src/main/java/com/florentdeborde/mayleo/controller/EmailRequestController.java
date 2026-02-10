package com.florentdeborde.mayleo.controller;

import com.florentdeborde.mayleo.dto.request.EmailRequestDto;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.service.EmailRequestService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/email-request")
public class EmailRequestController {

    private final EmailRequestService service;

    public EmailRequestController(EmailRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createEmailRequest(
            @Parameter(hidden = true) @RequestAttribute("authenticatedClient") ApiClient client,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EmailRequestDto dto) {

        String emailRequestId = service.createEmailRequest(client, dto, idempotencyKey);

        return ResponseEntity.accepted().body(Collections.singletonMap("id", emailRequestId));
    }
}
