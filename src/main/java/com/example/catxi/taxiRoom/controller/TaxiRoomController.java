package com.example.catxi.taxiRoom.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.catxi.taxiRoom.dto.TaxiRoomDto;
import com.example.catxi.taxiRoom.service.TaxiRoomService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/taxi")
@RequiredArgsConstructor
public class TaxiRoomController {

	private final TaxiRoomService taxiRoomService;

	@GetMapping("/rooms")
	public List<TaxiRoomDto> getAllRooms() {
		return taxiRoomService.getAllRooms();
	}

	@PostMapping("/rooms")
	public TaxiRoomDto createRoom(@RequestBody TaxiRoomDto dto) {
		return taxiRoomService.createRoom(dto);
	}
}
