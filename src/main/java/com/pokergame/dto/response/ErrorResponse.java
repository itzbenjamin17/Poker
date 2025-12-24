package com.pokergame.dto.response;

public record ErrorResponse(int status, String error, String message) {}
