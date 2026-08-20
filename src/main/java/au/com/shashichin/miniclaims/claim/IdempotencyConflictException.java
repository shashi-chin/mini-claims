package au.com.shashichin.miniclaims.claim;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency-Key was reused with a different request body");
    }
}
