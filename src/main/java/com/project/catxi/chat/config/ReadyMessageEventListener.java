package com.project.catxi.chat.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.dto.ReadyMessageEvent;
import com.project.catxi.chat.service.RedisPubSubService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReadyMessageEventListener {

	private final RedisPubSubService redisPubSubService;
	private final ObjectMapper objectMapper;


	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReadyMessageEvent(ReadyMessageEvent event) throws JsonProcessingException {
			String json = objectMapper.writeValueAsString(event.readyMessageRes());
			redisPubSubService.publish(event.channel(), json);
	}
}