package com.example.catxi.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.catxi.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
	Member findByUsername(String username);
}
