package com.project.catxi.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.member.domain.Member;

public interface MemberRepository   extends JpaRepository<Member, Long> {
  Boolean existsByMembername(String name);

  Optional<Member> findByMembername(String membername);

  Boolean existsByStudentNo(Long studentNo);
}