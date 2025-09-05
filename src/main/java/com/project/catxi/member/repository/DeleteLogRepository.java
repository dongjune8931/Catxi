package com.project.catxi.member.repository;

import com.project.catxi.member.domain.DeleteLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeleteLogRepository extends JpaRepository<DeleteLog, Long> {

}
