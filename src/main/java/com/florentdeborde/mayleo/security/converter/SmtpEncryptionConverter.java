package com.florentdeborde.mayleo.security.converter;

import com.florentdeborde.mayleo.service.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;

@Converter
public class SmtpEncryptionConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;
    private final String smtpKey;

    public SmtpEncryptionConverter(EncryptionService encryptionService,
                                   @Value("${app.security.key-smtp}") String smtpKey) {
        this.encryptionService = encryptionService;
        this.smtpKey = smtpKey;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute, smtpKey);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData, smtpKey);
    }
}