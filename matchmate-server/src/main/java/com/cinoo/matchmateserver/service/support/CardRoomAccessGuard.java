package com.cinoo.matchmateserver.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.constant.CardConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.CardRoomMapper;
import com.cinoo.matchmateserver.mapper.CardRoomMemberMapper;
import com.cinoo.matchmateserver.model.domain.CardRoom;
import com.cinoo.matchmateserver.model.domain.CardRoomMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardRoomAccessGuard {

    private final CardRoomMapper cardRoomMapper;
    private final CardRoomMemberMapper cardRoomMemberMapper;

    public CardRoom requireRoom(Long roomId) {
        CardRoom room = cardRoomMapper.selectById(roomId);
        if (room == null) throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        return room;
    }

    public CardRoom requireActiveRoomForMember(Long roomId, Long userId) {
        return requireActiveRoomForMember(roomId, userId, null);
    }

    public CardRoom requireActiveRoomForMember(Long roomId, Long userId, String endedMessage) {
        CardRoom room = requireRoom(roomId);
        requireActiveMember(roomId, userId);
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            if (endedMessage == null) {
                throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
            }
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED, endedMessage);
        }
        return room;
    }

    public CardRoomMember requireActiveMember(Long roomId, Long userId) {
        CardRoomMember member = selectMember(roomId, userId);
        if (member == null || member.getStatus() != CardConstant.MEMBER_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_MEMBER);
        }
        return member;
    }

    public CardRoomMember requireAnyMember(Long roomId, Long userId) {
        CardRoomMember member = selectMember(roomId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.ROOM_NOT_MEMBER);
        }
        return member;
    }

    public void requireOwner(CardRoom room, Long userId) {
        if (!room.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ROOM_NOT_OWNER);
        }
    }

    private CardRoomMember selectMember(Long roomId, Long userId) {
        return cardRoomMemberMapper.selectOne(
                new LambdaQueryWrapper<CardRoomMember>()
                        .eq(CardRoomMember::getRoomId, roomId)
                        .eq(CardRoomMember::getUserId, userId));
    }
}
