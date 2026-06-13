package com.cinoo.matchmateserver.card.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinoo.matchmateserver.infrastructure.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.constant.CardRoomEventType;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.card.mapper.CardRoomMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoomMemberMapper;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.card.model.entity.CardRoom;
import com.cinoo.matchmateserver.card.model.entity.CardRoomMember;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.card.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.retention.DataRetentionService;
import com.cinoo.matchmateserver.user.service.UserService;
import com.cinoo.matchmateserver.card.service.assembler.CardRoomViewAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardRoomLifecycleProcessor {

    private static final Random RANDOM = new Random();

    private final CardRoomMapper cardRoomMapper;
    private final CardRoomMemberMapper cardRoomMemberMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final DataRetentionService dataRetentionService;
    private final CacheInvalidationService cacheInvalidationService;
    private final CardRoomViewAssembler cardRoomViewAssembler;
    private final CardRedisLockExecutor cardRedisLockExecutor;
    private final CardRoomEventPublisher cardRoomEventPublisher;
    private final CardRoomAccessGuard cardRoomAccessGuard;

    public CardRoomVO createRoom(User user) {
        checkNotInActiveRoom(user.getId());

        CardRoom room = new CardRoom();
        room.setRoomCode(generateRoomCode());
        room.setOwnerId(user.getId());
        room.setStatus(CardConstant.ROOM_STATUS_ACTIVE);
        room.setMaxMembers(CardConstant.DEFAULT_MAX_MEMBERS);
        cardRoomMapper.insert(room);

        CardRoomMember member = new CardRoomMember();
        member.setRoomId(room.getId());
        member.setUserId(user.getId());
        member.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
        cardRoomMemberMapper.insert(member);

        return cardRoomViewAssembler.toRoomVO(room, user.getId());
    }

    public CardRoomVO joinRoom(String roomCode, User user) {
        CardRoom initialRoom = cardRoomMapper.selectOne(
                new LambdaQueryWrapper<CardRoom>()
                        .eq(CardRoom::getRoomCode, roomCode));
        if (initialRoom == null) throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        if (initialRoom.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        return cardRedisLockExecutor.call(
                CardConstant.LOCK_JOIN + initialRoom.getId(),
                3,
                () -> joinRoomLocked(initialRoom.getId(), user));
    }

    public void leaveRoom(Long roomId, User user) {
        CardRoom room = cardRoomAccessGuard.requireRoom(roomId);
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }
        if (room.getOwnerId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.ROOM_NOT_OWNER, "房主不能直接退出房间，请先结束房间");
        }

        CardRoomMember member = cardRoomAccessGuard.requireActiveMember(roomId, user.getId());
        member.setStatus(CardConstant.MEMBER_STATUS_LEFT);
        member.setSettleScore(member.getTotalScore());
        int deltaWins = member.getTotalScore() > 0 ? 1 : 0;
        int deltaLosses = member.getTotalScore() < 0 ? 1 : 0;
        member.setWins(member.getWins() + deltaWins);
        member.setLosses(member.getLosses() + deltaLosses);
        member.setLeaveTime(new Date());
        cardRoomMemberMapper.updateById(member);

        userMapper.addStats(user.getId(), member.getTotalScore(), deltaWins, deltaLosses);
        cacheInvalidationService.userChanged(user.getId());

        UserVO userVO = userService.toUserVO(user);
        cardRoomEventPublisher.pushAfterCommit(
                roomId,
                user.getId(),
                CardRoomEventType.MEMBER_LEFT,
                userVO);
    }

    public CardRoomVO endRoom(Long roomId, User user) {
        CardRoom room = cardRoomAccessGuard.requireRoom(roomId);
        cardRoomAccessGuard.requireOwner(room, user.getId());
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        return cardRedisLockExecutor.call(
                CardConstant.LOCK_END + roomId,
                5,
                ErrorCode.ROOM_ALREADY_SETTLED,
                null,
                () -> endRoomLocked(roomId, user));
    }

    private CardRoomVO joinRoomLocked(Long roomId, User user) {
        CardRoom room = cardRoomAccessGuard.requireRoom(roomId);
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        CardRoom existing = cardRoomMapper.selectActiveRoomByUserId(user.getId());
        if (existing != null && !existing.getId().equals(room.getId())) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_IN);
        }

        CardRoomMember member = cardRoomMemberMapper.selectOne(
                new LambdaQueryWrapper<CardRoomMember>()
                        .eq(CardRoomMember::getRoomId, room.getId())
                        .eq(CardRoomMember::getUserId, user.getId()));
        if (member != null) {
            return handleExistingMember(room, member, user);
        }

        assertRoomHasCapacity(room);
        CardRoomMember newMember = new CardRoomMember();
        newMember.setRoomId(room.getId());
        newMember.setUserId(user.getId());
        newMember.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
        cardRoomMemberMapper.insert(newMember);

        return publishJoinedAndBuildVO(room, user);
    }

    private CardRoomVO handleExistingMember(CardRoom room, CardRoomMember member, User user) {
        if (member.getStatus() == CardConstant.MEMBER_STATUS_ACTIVE) {
            return cardRoomViewAssembler.toRoomVO(room, user.getId());
        }
        if (member.getStatus() == CardConstant.MEMBER_STATUS_LEFT) {
            assertRoomHasCapacity(room);
            int updated = cardRoomMemberMapper.reactivate(member.getId());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重新加入房间失败");
            }
            member.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
            member.setJoinTime(new Date());
            member.setLeaveTime(null);
            return publishJoinedAndBuildVO(room, user);
        }
        throw new BusinessException(ErrorCode.ROOM_ALREADY_SETTLED, "你已结算该房间，不能重新加入");
    }

    private CardRoomVO endRoomLocked(Long roomId, User user) {
        CardRoom room = cardRoomAccessGuard.requireRoom(roomId);
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        List<CardRoomMember> activeMembers = cardRoomMemberMapper.selectActiveByRoomId(roomId);
        List<CardRoomMember> toSettle = new ArrayList<>();

        for (CardRoomMember member : activeMembers) {
            int finalScore = member.getTotalScore();
            member.setSettleScore(finalScore);
            member.setStatus(CardConstant.MEMBER_STATUS_SETTLED);
            member.setLeaveTime(new Date());

            if (finalScore > 0) member.setWins(member.getWins() + 1);
            else if (finalScore < 0) member.setLosses(member.getLosses() + 1);

            toSettle.add(member);

            int deltaWins = finalScore > 0 ? 1 : 0;
            int deltaLosses = finalScore < 0 ? 1 : 0;
            userMapper.addStats(member.getUserId(), finalScore, deltaWins, deltaLosses);
            cacheInvalidationService.userChanged(member.getUserId());
        }

        if (!toSettle.isEmpty()) {
            cardRoomMemberMapper.batchSettle(toSettle);
        }

        room.setStatus(CardConstant.ROOM_STATUS_ENDED);
        room.setSettleTime(new Date());
        cardRoomMapper.updateById(room);

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        cardRoomEventPublisher.pushAfterCommit(
                roomId,
                user.getId(),
                CardRoomEventType.ROOM_CLOSED,
                vo);
        cardRoomEventPublisher.runAfterCommit(this::cleanupCardHistorySafely);
        return vo;
    }

    private CardRoomVO publishJoinedAndBuildVO(CardRoom room, User user) {
        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        UserVO userVO = userService.toUserVO(user);
        cardRoomEventPublisher.pushAfterCommit(
                room.getId(),
                user.getId(),
                CardRoomEventType.MEMBER_JOINED,
                userVO);
        return vo;
    }

    private void assertRoomHasCapacity(CardRoom room) {
        long activeCount = cardRoomMemberMapper.selectCount(
                new LambdaQueryWrapper<CardRoomMember>()
                        .eq(CardRoomMember::getRoomId, room.getId())
                        .eq(CardRoomMember::getStatus, CardConstant.MEMBER_STATUS_ACTIVE));
        if (activeCount >= room.getMaxMembers()) {
            throw new BusinessException(ErrorCode.ROOM_FULL);
        }
    }

    private String generateRoomCode() {
        for (int i = 0; i < 20; i++) {
            String code = String.valueOf(100000 + RANDOM.nextInt(900000));
            if (cardRoomMapper.selectCount(
                    new LambdaQueryWrapper<CardRoom>()
                            .eq(CardRoom::getRoomCode, code)) == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成房间号失败，请重试");
    }

    private void checkNotInActiveRoom(Long userId) {
        CardRoom existing = cardRoomMapper.selectActiveRoomByUserId(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_IN);
        }
    }

    private void cleanupCardHistorySafely() {
        try {
            dataRetentionService.cleanupExpiredCardRooms();
        } catch (RuntimeException e) {
            log.error("Card history retention cleanup failed", e);
        }
    }
}
