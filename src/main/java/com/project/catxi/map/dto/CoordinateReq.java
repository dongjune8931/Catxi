package com.project.catxi.map.dto;

public record CoordinateReq(
	Long roomId,
	String email,
	Double latitude,
	Double longitude
) { }
