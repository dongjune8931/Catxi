package com.project.catxi.common.config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.project.catxi.chat.dto.SseSendReq;
import com.project.catxi.chat.service.RedisPubSubService;
import com.project.catxi.chat.service.SseSubscriber;

@Configuration
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	private String host;

	@Value("${spring.data.redis.port}")
	private int port;

	@Value("${spring.data.redis.password}")
	private String password;

	//연결 기본 객체
	@Bean
	@Qualifier("chatRedisConnectionFactory")
	public RedisConnectionFactory chatPubSubFactory() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
		configuration.setHostName(host);
		configuration.setPort(port);
		//redis pub/sub 에서는 특정 데이터베이스에 의존적이지 않음.
		//configuration.setDatabase(0);
		configuration.setPassword(RedisPassword.of(password));
		return new LettuceConnectionFactory(configuration);
	}

	//publish 객체
	@Bean
	@Qualifier("chatPubSub")
	// 일반적으로 RedisTemplate< key 데이터 타입 , value 데이터타입> 을 사용
	public StringRedisTemplate stringRedisTemplate( @Qualifier("chatRedisConnectionFactory")RedisConnectionFactory redisConnectionFactory) {
		return new StringRedisTemplate(redisConnectionFactory);
	}

	@Bean
	@Qualifier("ssePubSub")
	public RedisTemplate<String, Object> redisTemplate(
		@Qualifier("chatRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(redisConnectionFactory);

		redisTemplate.setKeySerializer(new StringRedisSerializer());
		Jackson2JsonRedisSerializer<SseSendReq> serializer = new Jackson2JsonRedisSerializer<>(SseSendReq.class);
		redisTemplate.setValueSerializer(serializer);

		return redisTemplate;
	}

	//subscribe 객체
	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
		@Qualifier("chatRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory,
		MessageListenerAdapter messageListenerAdapter,
		MessageListenerAdapter sseListenerAdapter
	) {
		RedisMessageListenerContainer container=new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener(messageListenerAdapter, new PatternTopic("chat"));
		container.addMessageListener(sseListenerAdapter, new PatternTopic("sse:*"));
		return container;
	}


	//redis에서 수신된 메시지를 처리하는 객체 생성
	@Bean
	public MessageListenerAdapter messageListenerAdapter(RedisPubSubService redisPubSubService){
		//RedisPubSubService의 특정 메서드가 수신된 메시지를 처리할 수 있도록 지정
		return new MessageListenerAdapter(redisPubSubService,"onMessage");
	}

	@Bean
	public MessageListenerAdapter sseListenerAdapter(SseSubscriber sseSubscriber) {
		//RedisPubSubService의 특정 메서드가 수신된 메시지를 처리할 수 있도록 지정
		return new MessageListenerAdapter(sseSubscriber, "onMessage");
	}
}
