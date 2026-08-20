package au.com.shashichin.miniclaims.error;

public record ApiError(int status, String error, String message) {}
