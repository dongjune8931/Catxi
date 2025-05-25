package com.project.catxi.member.DTO;

import com.project.catxi.member.domain.Member;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

  private final Member member;

  public CustomUserDetails(Member member) {
    this.member = member;
  }

  // Role값 확인
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {

    Collection<GrantedAuthority> collection = new ArrayList<>();

    collection.add(new GrantedAuthority() {
      @Override
      public String getAuthority() {
        return member.getMembername();
      }
    });
    return collection;
  }

  @Override
  public String getPassword() {
    return member.getPassword();
  }

  @Override
  public String getMembername() {
    return member.getMembername();
  }

  // 계정 블락 여부
  /*@Override
  public boolean isAccountNonExpired() {
    return true;
  }
   */
}
