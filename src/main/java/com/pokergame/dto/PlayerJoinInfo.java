package com.pokergame.dto;

public record PlayerJoinInfo(
        String name,
        boolean isHost,
        String joinedAt
) {}