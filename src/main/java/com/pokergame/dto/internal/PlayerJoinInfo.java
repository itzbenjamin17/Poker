package com.pokergame.dto.internal;

public record PlayerJoinInfo(
        String name,
        boolean isHost,
        String joinedAt
) {}