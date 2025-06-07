package com.project.catxi.member.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.member.DTO.MatchHistoryRes;
import com.project.catxi.member.converter.MemberConverter;
import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MatchHistoryRepository;
import com.project.catxi.member.repository.MemberRepository;
import java.nio.file.AccessDeniedException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MatchHistoryService {

  private final MatchHistoryRepository matchHistoryRepository;
  private final MemberRepository memberRepository;
  private final MemberConverter memberConverter;

  public MatchHistoryService(MatchHistoryRepository matchHistoryRepository, MemberRepository memberRepository,MemberConverter memberConverter) {
    this.matchHistoryRepository = matchHistoryRepository;
    this.memberRepository = memberRepository;
    this.memberConverter = new MemberConverter();
  }

public MatchHistoryRes getHistoryById(Long historyId, String email) {
  Member user = memberRepository.findByEmail(email)
      .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

  MatchHistory history = matchHistoryRepository.findById(historyId)
      .orElseThrow(() -> new CatxiException(MemberErrorCode.MATCH_NOT_FOUND));

  return MemberConverter.toSingleResDTO(history);
}




  private List<String> maskFellas(List<String> fellas) {
    return fellas.stream()
        .map(this::maskName)
        .toList();
  }

  //이름 마스킹 (박**)
  private String maskName(String name) {
    if (name == null || name.isBlank()) return "";
    return name.length() >= 2
        ? name.substring(0, 1) + "*".repeat(name.length() - 1)
        : name;
  }

}
