package com.mkotb.bitmojigram.bitmoji;

import lombok.Getter;

@Getter
public class LoginResponse {
    private String accessToken;
    private String tokenType;

    protected LoginResponse() {
    }
}
