package com.project.catxi.member.DTO;

import java.time.LocalDateTime;
import java.util.List;

public record MatchHistoryRes(
    Long historyId,
    LocalDateTime matchedAt,
    String startPoint,
    String endPoint,
    List<String> maskedFellas
){}
