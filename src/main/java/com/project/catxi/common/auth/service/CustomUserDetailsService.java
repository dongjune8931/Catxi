package com.project.catxi.common.auth.service;

import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
  private final MemberRepository memberRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

    Member member = memberRepository.findByEmail(username).orElse(null);

    if (member != null) {
      return new CustomUserDetails(member);
    } throw new UsernameNotFoundException("회원이 존재하지 않습니다" + username);
  }

}
