package com.project.catxi.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.member.domain.Member;

public interface MemberRepository   extends JpaRepository<Member, Long> {
  Boolean existsByMembername(String name);

  Member findByMembername(String name);

  Boolean existsByStudentNo(Long studentNo);
}