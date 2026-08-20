package au.com.shashichin.miniclaims.claim;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/claims")
@Tag(name = "claims", description = "Claims intake (Cloud API-shaped, not ClaimCenter)")
public class ClaimController {

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping
    @Operation(summary = "Create a claim", description = "Requires Idempotency-Key. Replays return the same claim.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Claim created"),
        @ApiResponse(responseCode = "200", description = "Idempotent replay of the same request"),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key or invalid body", content = @Content),
        @ApiResponse(
                responseCode = "409",
                description = "Idempotency-Key reused with a different body",
                content = @Content)
    })
    public ResponseEntity<Claim> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateClaimRequest request) {
        CreateClaimResult result = claims.create(idempotencyKey, request);
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.claim());
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "Get a claim by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Claim found"),
        @ApiResponse(responseCode = "404", description = "Unknown claimId", content = @Content)
    })
    public Claim get(@PathVariable String claimId) {
        return claims.get(claimId);
    }
}
