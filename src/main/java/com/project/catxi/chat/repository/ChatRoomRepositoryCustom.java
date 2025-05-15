package com.project.catxi.chat.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.common.domain.Location;

public interface ChatRoomRepositoryCustom {
	Page<ChatRoom> findByLocationAndDirection(Location location, String point, Pageable pageable);
}
