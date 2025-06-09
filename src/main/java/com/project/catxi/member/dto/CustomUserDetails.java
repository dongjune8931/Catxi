package com.project.catxi.member.dto;

import com.project.catxi.member.domain.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

  private final Member member;

  public CustomUserDetails(Member member) {
    this.member = member;
  }

  // Role값 확인
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {

    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Override
  public String getPassword() {
    return member.getPassword();
  }

  @Override
  public String getUsername() {return member.getEmail();}



  // 계정 블락 여부
  /*@Override
  public boolean isAccountNonExpired() {
    return true;
  }
   */
}
