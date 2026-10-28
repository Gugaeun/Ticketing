package com.ticketing.member;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 관리자 계정이 없으면 설정값(admin.email / admin.password)으로 하나 만든다. */
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}") private String email;
    @Value("${admin.password}") private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (!memberRepository.existsByEmail(email)) {
            memberRepository.save(new Member(email, passwordEncoder.encode(password), "admin", Role.ADMIN));
        }
    }
}
