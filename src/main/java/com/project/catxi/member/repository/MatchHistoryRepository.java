package com.project.catxi.member.repository;

import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

  Optional<MatchHistory> findByIdAndUser(Long id, Member user);


}
