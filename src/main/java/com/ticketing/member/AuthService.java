package com.ticketing.member;

import com.ticketing.common.DuplicateEmailException;
import com.ticketing.common.InvalidCredentialsException;
import com.ticketing.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public Long signup(String email, String rawPassword, String name) {
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        return memberRepository.save(new Member(email, passwordEncoder.encode(rawPassword), name)).getId();
    }

    @Transactional(readOnly = true)
    public String login(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .filter(m -> passwordEncoder.matches(rawPassword, m.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);
        return jwtProvider.create(member);
    }
}
