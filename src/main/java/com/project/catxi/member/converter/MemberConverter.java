package com.project.catxi.member.converter;

import com.project.catxi.member.dto.MatchHistoryRes;
import com.project.catxi.member.dto.MemberProfileRes;
import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MemberConverter {

  public static MemberProfileRes toMemberProfileDTO(Member member) {
    return new MemberProfileRes(
        member.getMembername(),
        member.getStudentNo(),
        member.getMatchCount());
  }


  public static MatchHistoryRes toSingleResDTO(MatchHistory history) {
    return new MatchHistoryRes(
        history.getId(),
        history.getMatchedAt(),
        history.getStartPoint().name(),
        history.getEndPoint().name(),
        maskFellas(history.getFellas())
    );
  }

  private static List<String> maskFellas(List<String> fellas) {
    return fellas.stream()
        .map(MemberConverter::maskName)
        .toList();
  }

  private static String maskName(String name) {
    if (name == null || name.isBlank()) return "";
    return name.length() >= 2
        ? name.substring(0, 1) + "*".repeat(name.length() - 1)
        : name;
  }

}
