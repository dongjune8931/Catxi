package com.project.catxi.map.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MapError;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.map.dto.CoordinateReq;
import com.project.catxi.map.dto.CoordinateRes;

@Service
public class MapService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private static final long GEO_TTL_SECONDS = 300;

	public MapService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			ChatParticipantRepository chatParticipantRepository, ChatRoomRepository chatRoomRepository) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.chatParticipantRepository = chatParticipantRepository;
		this.chatRoomRepository = chatRoomRepository;
	}

	public List<CoordinateRes> getCoordinates(Long roomId) {
		/*
		지도 좌표 조회 메서드
		해당 방에 참여중인 모든 유저의 좌표를 조회
		레디스에서 map:{roomId}:* 패턴으로 조회
		 */
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		List<String> keys = chatParticipantRepository.findParticipantEmailsByChatRoom(chatRoom);

		return keys.stream()
			.map(key -> {
				String json = redisTemplate.opsForValue().get("map:" + roomId + ":" + key);
				try {
					Map<String, Double> coordMap = objectMapper.readValue(json, Map.class);
					String userEmail = key.substring(key.lastIndexOf(':') + 1);
					Double latitude = coordMap.get("latitude");
					Double longitude = coordMap.get("longitude");
					Double distance = coordMap.get("distance");
					return new CoordinateRes(roomId, userEmail, latitude, longitude, distance);
				} catch (Exception e) {
					throw new CatxiException(MapError.COORDINATE_PARSE_FAILED);
				}
			})
			.toList();
	}

	public double handleSaveCoordinateAndDistance(CoordinateReq coordinateReq) {
		/*
	    좌표 저장 및 거리 계산 메서드
		출발지점은 map:{roomId}:departure 키로 저장되어 있어야 함
		출발지점이 없으면 예외 발생
		 */
		double distance = calculateFromDeparture(coordinateReq);
		saveCoordinate(coordinateReq, distance);
		return distance;
	}

	private void saveCoordinate(CoordinateReq coordinateReq, double distance) {
		String key = "map:" + coordinateReq.roomId() + ":" + coordinateReq.email();

		try{
			String json = objectMapper.writeValueAsString(Map.of(
				"latitude", coordinateReq.latitude(),
				"longitude", coordinateReq.longitude(),
				"distance", distance
			));
			redisTemplate.opsForValue().set(key, json, GEO_TTL_SECONDS, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new CatxiException(MapError.COORDINATE_SAVE_FAILED);
		}
	}

	private double calculateFromDeparture(CoordinateReq coordinateReq) {
		String key = "map:" + coordinateReq.roomId() + ":departure";
		String json = redisTemplate.opsForValue().get(key);
		if (json == null) {
			throw new CatxiException(MapError.DEPARTURE_NOT_FOUND);
		}

		try {
			Map<String, Double> departureMap = objectMapper.readValue(json, Map.class);
			double departureLat = departureMap.get("latitude");
			double departureLon = departureMap.get("longitude");
			return calculateDistance(departureLat, departureLon, coordinateReq.latitude(), coordinateReq.longitude());
		} catch (Exception e) {
			throw new CatxiException(MapError.COORDINATE_PARSE_FAILED);
		}

	}

	private double calculateDistance (double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371000; // 미터 단위
		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

}
