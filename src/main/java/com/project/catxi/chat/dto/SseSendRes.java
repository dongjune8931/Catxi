package com.project.catxi.chat.dto;

import java.time.LocalDateTime;

public record SseSendRes(
	String senderName,
	Object data,
	LocalDateTime timestamp
) { }
