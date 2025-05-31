package com.project.catxi.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.member.domain.Member;

public interface MemberRepository   extends JpaRepository<Member, Long> {

  Optional<Member> findByMembername(String membername);

  Optional<Member> findByNickname(String nickname);

  Optional<Member> findByEmail(String email);
}