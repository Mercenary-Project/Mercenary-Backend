package org.example.mercenary.global.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthenticatedMember {

    private final Long memberId;
    private final String role;
}
