package com.project.catxi.map.dto;

public record CoordinateRes (
	Long roomId,
	String email,
	Double latitude,
	Double longitude,
	Double distance
){
}
