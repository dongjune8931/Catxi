package com.example.catxi.taxiRoom.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.catxi.taxiRoom.entity.TaxiRoom;

@Repository
public interface TaxiRoomRepository extends JpaRepository<TaxiRoom, Long> {
}