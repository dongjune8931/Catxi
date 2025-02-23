package com.example.catxi.taxiRoom.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

import com.example.catxi.kakaomap.KakaoMapService;
import com.example.catxi.taxiRoom.RoomStatus;
import com.example.catxi.taxiRoom.dto.TaxiRoomDto;
import com.example.catxi.taxiRoom.entity.TaxiRoom;
import com.example.catxi.taxiRoom.repository.TaxiRoomRepository;

@Service
@RequiredArgsConstructor
public class TaxiRoomService {

	private final TaxiRoomRepository taxiRoomRepository;
	private final KakaoMapService kakaoMapService;

	public List<TaxiRoomDto> getAllRooms() {
		return taxiRoomRepository.findAll().stream()
			.map(room -> new TaxiRoomDto(
				room.getId(),
				room.getDeparture(),
				room.getDestination(),
				room.getDepartureTime(),
				room.getStatus(),
				room.getMeetingAddress(),
				room.getLatitude(),
				room.getLongitude()))
			.collect(Collectors.toList());
	}

	public TaxiRoomDto createRoom(TaxiRoomDto dto) {
		KakaoMapService.Coordinates coordinates = kakaoMapService.getCoordinates(dto.meetingAddress());
		TaxiRoom taxiRoom = TaxiRoom.builder()
			.departure(dto.departure())
			.destination(dto.destination())
			.departureTime(dto.departureTime())
			.status(RoomStatus.OPEN)
			.meetingAddress(dto.meetingAddress())
			.latitude(coordinates != null ? coordinates.getLatitude() : null)
			.longitude(coordinates != null ? coordinates.getLongitude() : null)
			.build();
		taxiRoom = taxiRoomRepository.save(taxiRoom);
		return new TaxiRoomDto(
			taxiRoom.getId(),
			taxiRoom.getDeparture(),
			taxiRoom.getDestination(),
			taxiRoom.getDepartureTime(),
			taxiRoom.getStatus(),
			taxiRoom.getMeetingAddress(),
			taxiRoom.getLatitude(),
			taxiRoom.getLongitude()
		);
	}
}
