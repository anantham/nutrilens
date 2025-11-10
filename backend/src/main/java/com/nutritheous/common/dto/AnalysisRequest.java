package com.nutritheous.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {

    @JsonProperty("image_url")
    @NotBlank(message = "Image URL is required")
    @Pattern(regexp = "^(https?://|data:image/).*", message = "Image URL must be a valid HTTP(S) URL or data URI")
    private String imageUrl;
}
