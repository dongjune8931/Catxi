package com.project.catxi.report.repository;

import com.project.catxi.report.domain.Report;
import com.project.catxi.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("SELECT r FROM Report r WHERE r.roomId = :roomId AND r.reporter.id = :reporterId AND r.reportedMember.id = :reportedMemberId")
    Optional<Report> findDuplicateReport(@Param("roomId") Long roomId, @Param("reporterId") Long reporterId, @Param("reportedMemberId") Long reportedMemberId);

    @Modifying
    @Transactional
    void deleteAllByReporter(Member reporter);
    
    @Modifying
    @Transactional
    void deleteAllByReportedMember(Member reportedMember);
}
