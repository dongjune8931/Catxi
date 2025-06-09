package com.project.catxi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SseSendReq(
	@JsonProperty("eventName") String eventName,
	@JsonProperty("data") String data,
	@JsonProperty("roomId") String roomId,
	@JsonProperty("senderName") String senderName,
	@JsonProperty("direction") String direction
) { }
