package com.project.catxi.chat.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.domain.QChatRoom;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.QMember;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EnumPath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryCustomImpl implements ChatRoomRepositoryCustom {
	private final JPAQueryFactory jpaQueryFactory;

	@Override
	public Page<ChatRoom> findByLocationAndDirection(Location location, String point, Pageable pageable) {
		QChatRoom chatRoom = QChatRoom.chatRoom;

		// 1. 페이징 조회
		List<ChatRoom> chatRooms = jpaQueryFactory
			.selectFrom(chatRoom)
			.where(
				chatRoom.status.eq(RoomStatus.WAITING),
				filterByLocationAndPoint(location, point)
			)
			.orderBy(getOrderSpecifier(pageable))
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();

		// 2. 전체 데이터 개수 조회
		JPAQuery<Long> total = jpaQueryFactory
			.select(chatRoom.count())
			.from(chatRoom)
			.where(
				chatRoom.status.eq(RoomStatus.WAITING),
				filterByLocationAndPoint(location, point)
			);

		return PageableExecutionUtils.getPage(
			chatRooms,
			pageable,
			total::fetchOne
		);
	}

	private OrderSpecifier<?> getOrderSpecifier(Pageable pageable) {
		Sort.Order order = pageable.getSort().iterator().next();
		PathBuilder<ChatRoom> path = new PathBuilder<>(ChatRoom.class, "chatRoom");
		String property = order.getProperty();

		return new OrderSpecifier<>(
			order.isAscending() ? Order.ASC : Order.DESC,
			path.getComparable(property, Comparable.class)
		);
	}

	private BooleanExpression filterByLocationAndPoint(Location location, String point) {
		QChatRoom chatRoom = QChatRoom.chatRoom;
		if (point.equals("TO_SCHOOL")) {
			return filterByLocation(chatRoom.startPoint, location);
		} else if (point.equals("FROM_SCHOOL")) {
			return filterByLocation(chatRoom.endPoint, location);
		} else {
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		}
	}

	private BooleanExpression filterByLocation(EnumPath<Location> path, Location location){
		Set<Location> locations = Set.of(Location.GURO_ST, Location.BUCHEON_ST, Location.SINDORIM_ST);
		if(locations.contains(location))
			return path.in(locations);
		else
			return path.eq(location);
	}


}
