package com.project.catxi.common.config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.project.catxi.chat.service.RedisPubSubService;

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

	@Bean
	@Qualifier("chatPubSub")
	public StringRedisTemplate chatPubSubTemplate(
		@Qualifier("chatRedisConnectionFactory") RedisConnectionFactory cf) {
		StringRedisTemplate tpl = new StringRedisTemplate(cf);
		// StringRedisTemplate 기본도 StringRedisSerializer(UTF-8)이지만 명시해도 ok
		// var s = new StringRedisSerializer(StandardCharsets.UTF_8);
		// tpl.setKeySerializer(s); tpl.setValueSerializer(s);
		// tpl.setHashKeySerializer(s); tpl.setHashValueSerializer(s);
		return tpl;
	}

	@Bean
	public ThreadPoolTaskScheduler redisPubSubScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("redis-pubsub-");
		scheduler.initialize();
		return scheduler;
	}

	//publish 객체
	/*@Bean
	@Qualifier("chatPubSub")
	// 일반적으로 RedisTemplate< key 데이터 타입 , value 데이터타입> 을 사용
	public StringRedisTemplate stringRedisTemplate( @Qualifier("chatRedisConnectionFactory")RedisConnectionFactory redisConnectionFactory) {
		return new StringRedisTemplate(redisConnectionFactory);
	}*/

	//subscribe 객체
	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
		@Qualifier("chatRedisConnectionFactory") RedisConnectionFactory cf,
		RedisPubSubService listener,
		ThreadPoolTaskScheduler redisPubSubScheduler
	) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(cf);
		container.setTaskExecutor(redisPubSubScheduler);

		// 명시적 구독: 채널 + 패턴
		container.addMessageListener(listener, new ChannelTopic("chat"));
		container.addMessageListener(listener, new ChannelTopic("map"));
		container.addMessageListener(listener, new PatternTopic("ready:*"));
		container.addMessageListener(listener, new PatternTopic("participants:*"));
		container.addMessageListener(listener, new PatternTopic("kick:*"));
		return container;
	}

}
