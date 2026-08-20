package au.com.shashichin.miniclaims.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateClaimRequest(
        @NotNull(message = "lossDate is required") LocalDate lossDate,
        @NotBlank(message = "description is required") String description,
        ClaimStatus status,
        @NotBlank(message = "reporterName is required") String reporterName) {

    CreateClaimRequest normalized() {
        return new CreateClaimRequest(
                lossDate,
                description == null ? null : description.trim(),
                status == null ? ClaimStatus.DRAFT : status,
                reporterName == null ? null : reporterName.trim());
    }
}
