package com.florentdeborde.mayleo.dto.altair;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetryBatchRequestDto {
    private List<String> ids = List.of();
}
