package com.project.catxi.chat.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.project.catxi.chat.service.RedisPubSubService;

@Configuration
public class RedisPubSubConfig {

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
		RedisConnectionFactory connectionFactory,
		RedisPubSubService listener,
		ThreadPoolTaskScheduler redisPubSubScheduler
	) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setTaskExecutor(redisPubSubScheduler); // 수신 스레드풀

		// 채널 구독: 정확히 "chat" 채널
		container.addMessageListener(listener, new ChannelTopic("chat"));
		container.addMessageListener(listener, new PatternTopic("map"));

		// 패턴 구독: ready:*, participants:*, kick:* 모두 수신
		container.addMessageListener(listener, new PatternTopic("ready:*"));
		container.addMessageListener(listener, new PatternTopic("participants:*"));
		container.addMessageListener(listener, new PatternTopic("kick:*"));

		return container;
	}

	// 2) 리스너 스레드풀(권장)
	@Bean
	public ThreadPoolTaskScheduler redisPubSubScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("redis-pubsub-");
		scheduler.initialize();
		return scheduler;
	}

	@Bean
	@Qualifier("chatPubSub")
	public StringRedisTemplate chatPubSubTemplate(RedisConnectionFactory cf) {
		StringRedisTemplate tpl = new StringRedisTemplate(cf);
		var s = new org.springframework.data.redis.serializer.StringRedisSerializer(StandardCharsets.UTF_8);
		tpl.setKeySerializer(s);
		tpl.setValueSerializer(s);
		tpl.setHashKeySerializer(s);
		tpl.setHashValueSerializer(s);
		return tpl;
	}
}
