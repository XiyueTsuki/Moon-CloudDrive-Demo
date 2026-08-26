package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;

    private String password;

    private String email;

    private String code;
}