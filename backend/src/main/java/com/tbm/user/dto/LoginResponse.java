package com.tbm.user.dto;

public record LoginResponse(String token, UserProfile user) {
}
