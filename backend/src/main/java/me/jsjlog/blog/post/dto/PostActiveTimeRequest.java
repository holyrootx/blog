package me.jsjlog.blog.post.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostActiveTimeRequest(
		@NotBlank @Size(max = 36) String token,
		@NotNull @Min(0) @Max(7_200_000) Long activeTimeMillis
) {
}
