package com.arbitragepro.api;

import lombok.Data;

@Data
public class AuthResponse {
    private int user_id;
    private String token;
    private String email;
}
