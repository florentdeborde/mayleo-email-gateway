package com.florentdeborde.mayleo.security.converter;

import com.florentdeborde.mayleo.service.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;

@Converter
public class HmacEncryptionConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;
    private final String hmacKey;

    public HmacEncryptionConverter(EncryptionService encryptionService,
                                   @Value("${app.security.key-hmac}") String hmacKey) {
        this.encryptionService = encryptionService;
        this.hmacKey = hmacKey;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute, hmacKey);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData, hmacKey);
    }
}