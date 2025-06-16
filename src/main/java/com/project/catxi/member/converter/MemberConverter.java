package com.project.catxi.member.converter;

import com.project.catxi.member.dto.MatchHistoryRes;
import com.project.catxi.member.dto.MemberProfileRes;
import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import java.util.ArrayList;
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

  public static MatchHistoryRes toAllResDTO(MatchHistory history, String CurrentUserNickname) {

    List<String> total = new ArrayList<>();
    total.add(history.getUser().getNickname());
    total.addAll(history.getFellas());

    // 중복 제거 (현재 유저 제외)
    List<String> others = total.stream()
        .distinct()
        .filter(name -> !name.equals(CurrentUserNickname))  // 로그인한 사용자 제외
        .map(MemberConverter::maskName)
        .toList();

    return new MatchHistoryRes(
        history.getId(),
        history.getMatchedAt(),
        history.getStartPoint().name(),
        history.getEndPoint().name(),
        others
    );
  }

  private static List<String> maskFellas(List<String> fellas) {
    return fellas.stream()
        .map(MemberConverter::maskName)
        .toList();
  }

  private static String maskName(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.length() == 1) return name;
    return name.charAt(0) + "*".repeat(name.length() - 1);
  }

}
