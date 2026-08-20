package au.com.shashichin.miniclaims.claim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ClaimStatus {
    DRAFT,
    OPEN;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ClaimStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return ClaimStatus.valueOf(value.trim().toUpperCase());
    }
}
