package com.project.catxi.map.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.common.api.error.MapError;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.map.dto.CoordinateReq;
import com.project.catxi.map.dto.CoordinateRes;

@Service
public class MapService {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private static final long GEO_TTL_SECONDS = 300; // 5 minutes

	public MapService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	public List<CoordinateRes> getCoordinates(Long roomId) {
		/*
		지도 좌표 조회 메서드
		해당 방에 참여중인 모든 유저의 좌표를 조회
		레디스에서 map:{roomId}:* 패턴으로 조회
		 */

		String pattern = "map:" + roomId + ":*";
		Set<String> keys = redisTemplate.keys(pattern);

		if (keys == null || keys.isEmpty()) {
			throw new CatxiException(MapError.COORDINATE_NOT_FOUND);
		}
		try {
			return keys.stream()
				.map(key -> {
					String json = redisTemplate.opsForValue().get(key);
					try {
						Map<String, Double> coordMap = objectMapper.readValue(json, Map.class);
						String userEmail = key.substring(key.lastIndexOf(':') + 1);
						Double latitude = coordMap.get("latitude");
						Double longitude = coordMap.get("longitude");
						return new CoordinateRes(userEmail, latitude, longitude);
					} catch (Exception e) {
						throw new CatxiException(MapError.COORDINATE_PARSE_FAILED);
					}
				})
				.toList();
		} catch (Exception e) {
			throw new CatxiException(MapError.COORDINATE_PARSE_FAILED);
		}
	}

}
