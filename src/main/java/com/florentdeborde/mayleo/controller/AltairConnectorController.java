package com.florentdeborde.mayleo.controller;

import com.florentdeborde.mayleo.dto.altair.EmailRequestAltairDto;
import com.florentdeborde.mayleo.dto.altair.RetryBatchRequestDto;
import com.florentdeborde.mayleo.service.AltairEmailRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class AltairConnectorController {

    private static final Logger log = LoggerFactory.getLogger(AltairConnectorController.class);

    private final AltairEmailRequestService service;

    public AltairConnectorController(AltairEmailRequestService service) {
        this.service = service;
    }

    @GetMapping("/.well-known/altair.json")
    public ResponseEntity<Map<String, Object>> manifest() {
        log.info("[Altair] manifest requested");
        return ResponseEntity.ok(buildManifest());
    }

    @GetMapping("/altair/email-requests")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateRange) {

        Page<EmailRequestAltairDto> result = service.list(page, size, status, dateRange);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("number", result.getNumber());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/altair/email-requests/{id}")
    public ResponseEntity<EmailRequestAltairDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping("/altair/email-requests/retry-batch")
    public ResponseEntity<Void> retryBatch(@RequestBody RetryBatchRequestDto body) {
        service.retryBatch(body.getIds());
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> buildManifest() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", "email-requests");
        resource.put("endpoint", "/altair/email-requests");
        resource.put("labels", Map.of("en", "Email Requests", "fr", "Demandes d'email", "es", "Solicitudes de email"));
        resource.put("fields", buildFields());
        resource.put("actions", buildActions());
        resource.put("filters", buildFilters());
        return Map.of("resources", List.of(resource));
    }

    private List<Map<String, Object>> buildFields() {
        return List.of(
            field("id",            "text",   false, "ID",        "ID",               "ID"),
            field("toEmail",       "text",   true,  "Recipient", "Destinataire",     "Destinatario"),
            field("subject",       "text",   true,  "Subject",   "Objet",            "Asunto"),
            field("langCode",      "text",   true,  "Language",  "Langue",           "Idioma"),
            field("status",        "text",   true,  "Status",    "Statut",           "Estado"),
            field("retryCount",    "number", true,  "Retries",   "Tentatives",       "Intentos"),
            field("apiClientName", "text",   true,  "Client",    "Client",           "Cliente"),
            field("createdAt",     "date",   true,  "Created",   "Créé le",          "Creado el"),
            field("processedAt",   "date",   false, "Processed", "Traité le",        "Procesado el"),
            field("errorMessage",  "text",   false, "Error",     "Erreur",           "Error")
        );
    }

    private Map<String, Object> field(String name, String type, boolean visible, String en, String fr, String es) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("type", type);
        if (!visible) f.put("visible", false);
        f.put("labels", Map.of("en", en, "fr", fr, "es", es));
        return f;
    }

    private List<Map<String, Object>> buildActions() {
        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("id", "retry");
        retry.put("scope", "multiple");
        retry.put("endpoint", "/altair/email-requests/retry-batch");
        retry.put("labels", Map.of("en", "Retry", "fr", "Réessayer", "es", "Reintentar"));
        retry.put("condition", List.of(
            Map.of("column", "status", "condition", "EQUALS", "value", "FAILED")
        ));
        return List.of(retry);
    }

    private List<Map<String, Object>> buildFilters() {
        return List.of(
            filter("status-pending", "Pending",    "En attente",      "Pendiente",   "status",    "PENDING"),
            filter("status-sending", "Sending",    "En cours d'envoi","Enviando",    "status",    "SENDING"),
            filter("status-sent",    "Sent",        "Envoyés",         "Enviados",    "status",    "SENT"),
            filter("status-failed",  "Failed",      "Échoués",         "Fallidos",    "status",    "FAILED"),
            filter("date-today",     "Today",       "Aujourd'hui",     "Hoy",         "dateRange", "TODAY"),
            filter("date-week",      "This week",   "Cette semaine",   "Esta semana", "dateRange", "WEEK"),
            filter("date-month",     "This month",  "Ce mois",         "Este mes",    "dateRange", "MONTH")
        );
    }

    private Map<String, Object> filter(String id, String en, String fr, String es, String column, String value) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("id", id);
        f.put("name", en);
        f.put("labels", Map.of("en", en, "fr", fr, "es", es));
        f.put("filterJson", List.of(Map.of("column", column, "condition", "EQUALS", "value", value)));
        f.put("editable", false);
        return f;
    }
}
