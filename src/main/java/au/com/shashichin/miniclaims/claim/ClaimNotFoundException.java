package au.com.shashichin.miniclaims.claim;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(String claimId) {
        super("Claim not found: " + claimId);
    }
}
