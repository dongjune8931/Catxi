package com.project.catxi.member.converter;

import com.project.catxi.member.DTO.MemberProfileDTO;
import com.project.catxi.member.domain.Member;

public class MemberConverter {
  public static MemberProfileDTO toMemberProfileDTO(Member member) {
    return new MemberProfileDTO(member.getMembername(),
        member.getStudentNo(),
        member.getMatchCount());
  }
}
