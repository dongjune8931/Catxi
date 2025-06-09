package com.project.catxi.member.repository;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.member.domain.Member;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository   extends JpaRepository<Member, Long> {

  Optional<Member> findByMembername(String membername);

  Optional<Member> findByNickname(String nickname);
  
  Optional<Member> findByEmail(String email);

  //학번 검증용
  boolean existsByStudentNo(Long studentNo);

  //삭제된 회원 조회 불가
  Optional<Member> findByEmailAndDeletedFalse(String email);
}