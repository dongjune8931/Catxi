package com.project.catxi.member.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.member.dto.MatchHistoryRes;
import com.project.catxi.member.converter.MemberConverter;
import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MatchHistoryRepository;
import com.project.catxi.member.repository.MemberRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MatchHistoryService {

  private final MatchHistoryRepository matchHistoryRepository;
  private final MemberRepository memberRepository;

  public MatchHistoryService(MatchHistoryRepository matchHistoryRepository, MemberRepository memberRepository,MemberConverter memberConverter) {
    this.matchHistoryRepository = matchHistoryRepository;
    this.memberRepository = memberRepository;
  }

  //단 건 조회용
  public MatchHistoryRes getHistoryById(Long historyId, String email) {
    Member user = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

     MatchHistory history = matchHistoryRepository.findById(historyId)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MATCH_NOT_FOUND));

    if (!history.getUser().equals(user)) {
      throw new AccessDeniedException("본인의 이력만 조회할 수 있습니다.");
    }

    return MemberConverter.toSingleResDTO(history);
  }

  //최근 내역 조회용
  public List<MatchHistoryRes> getRecentHistoryTop2(String email) {
    Member user = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    return matchHistoryRepository.findTop2ByUserOrderByCreatedAtDesc(user).stream()
        .map(MemberConverter::toSingleResDTO)
        .toList();
  }

  public Slice<MatchHistoryRes> getScrollHistory(String email, Pageable pageable) {
    Member user = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    Slice<MatchHistory> histories = matchHistoryRepository.findAllByUserOrderByCreatedAtDesc(user, pageable);

    return histories.map(MemberConverter::toSingleResDTO);
  }

}
