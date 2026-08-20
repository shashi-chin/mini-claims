package au.com.shashichin.miniclaims.claim;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ClaimService {

    private final ConcurrentHashMap<String, StoredClaim> byIdempotencyKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Claim> byId = new ConcurrentHashMap<>();

    public CreateClaimResult create(String idempotencyKey, CreateClaimRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        CreateClaimRequest normalized = request.normalized();

        synchronized (this) {
            StoredClaim existing = byIdempotencyKey.get(idempotencyKey);
            if (existing != null) {
                if (!existing.request().equals(normalized)) {
                    throw new IdempotencyConflictException();
                }
                return new CreateClaimResult(existing.claim(), true);
            }

            Claim claim = new Claim(
                    UUID.randomUUID().toString(),
                    normalized.lossDate(),
                    normalized.description(),
                    normalized.status(),
                    normalized.reporterName());
            byIdempotencyKey.put(idempotencyKey, new StoredClaim(normalized, claim));
            byId.put(claim.claimId(), claim);
            return new CreateClaimResult(claim, false);
        }
    }

    public Claim get(String claimId) {
        Claim claim = byId.get(claimId);
        if (claim == null) {
            throw new ClaimNotFoundException(claimId);
        }
        return claim;
    }

    private record StoredClaim(CreateClaimRequest request, Claim claim) {}
}
