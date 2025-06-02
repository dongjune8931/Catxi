package com.project.catxi.chat.dto;

public record SseSendReq(
	String eventName,
	Object data
) { }
