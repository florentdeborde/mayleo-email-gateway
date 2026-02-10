package com.florentdeborde.mayleo.dto.request;

import com.florentdeborde.mayleo.model.ImageSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailRequestDto {
    @Pattern(
            regexp = "^[a-z]{2}(-[A-Z]{2})?$",
            message = "langCode must be a 2-letter ISO code (e.g. fr, en)"
    )
    private String langCode;

    private String subject;
    private String message;

    @NotBlank(message = "toEmail is required")
    @Email(message = "toEmail must be a valid email address")
    private String toEmail;

    @NotNull(message = "imageSource is required")
    private ImageSource imageSource;

    @NotBlank(message = "imagePath is required")
    private String imagePath;
}
