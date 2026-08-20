package au.com.shashichin.miniclaims.claim;

import java.time.LocalDate;

public record Claim(
        String claimId,
        LocalDate lossDate,
        String description,
        ClaimStatus status,
        String reporterName) {}
