package com.pokergame.dto.response;

/**
 * Generic API response wrapper for REST endpoints.
 * Provides consistent response structure across all API calls.
 *
 * @param <T> the type of data being returned
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data) {
    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * Creates a successful response without data.
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /**
     * Creates an error response with message.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
