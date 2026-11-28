package com.veggofresh.archive.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermanentDeleteRequestDto {
    /** Optional reason recorded on the archive header. */
    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
