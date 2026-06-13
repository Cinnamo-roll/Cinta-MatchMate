package com.cinoo.matchmateserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinoo.matchmateserver.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.constant.CardConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.*;
import com.cinoo.matchmateserver.model.domain.*;
import com.cinoo.matchmateserver.model.request.AddFundRequest;
import com.cinoo.matchmateserver.model.request.AddRoundRequest;
import com.cinoo.matchmateserver.model.request.AddTransferRequest;
import com.cinoo.matchmateserver.model.vo.*;
import com.cinoo.matchmateserver.service.assembler.CardRoomViewAssembler;
import com.cinoo.matchmateserver.service.CardRoomService;
import com.cinoo.matchmateserver.service.DataRetentionService;
import com.cinoo.matchmateserver.service.UserService;
import com.cinoo.matchmateserver.service.support.CardLedgerParticipantUtils;
import com.cinoo.matchmateserver.websocket.CardWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardRoomServiceImpl implements CardRoomService {

    private final CardRoomMapper cardRoomMapper;
    private final CardRoomMemberMapper cardRoomMemberMapper;
    private final CardRoundMapper cardRoundMapper;
    private final CardRoundScoreMapper cardRoundScoreMapper;
    private final CardFundRecordMapper cardFundRecordMapper;
    private final CardFundParticipantMapper cardFundParticipantMapper;
    private final CardUndoRequestMapper cardUndoRequestMapper;
    private final CardUndoApprovalMapper cardUndoApprovalMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final DataRetentionService dataRetentionService;
    private final CacheInvalidationService cacheInvalidationService;
    private final CardRoomViewAssembler cardRoomViewAssembler;
    private final RedissonClient redissonClient;
    private final CardWebSocketHandler cardWebSocketHandler;

    private static final int MAX_OVERVIEW_LIMIT = 20;
    private static final Random RANDOM = new Random();

    // ── 辅助方法 ──

    private User loginUser(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }

    private CardRoom requireRoom(Long roomId) {
        CardRoom room = cardRoomMapper.selectById(roomId);
        if (room == null) throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        return room;
    }

    private CardRoomMember requireMember(Long roomId, Long userId) {
        CardRoomMember member = cardRoomMemberMapper.selectOne(
                new LambdaQueryWrapper<CardRoomMember>()
                        .eq(CardRoomMember::getRoomId, roomId)
                        .eq(CardRoomMember::getUserId, userId));
        if (member == null || member.getStatus() != CardConstant.MEMBER_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_MEMBER);
        }
        return member;
    }

    private CardRoomMember requireAnyMember(Long roomId, Long userId) {
        CardRoomMember member = cardRoomMemberMapper.selectOne(
                new LambdaQueryWrapper<CardRoomMember>()
                        .eq(CardRoomMember::getRoomId, roomId)
                        .eq(CardRoomMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ErrorCode.ROOM_NOT_MEMBER);
        }
        return member;
    }

    private void requireOwner(CardRoom room, Long userId) {
        if (!room.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ROOM_NOT_OWNER);
        }
    }

    private String generateRoomCode() {
        for (int i = 0; i < 20; i++) {
            int code = 100000 + RANDOM.nextInt(900000);
            String s = String.valueOf(code);
            if (cardRoomMapper.selectCount(
                    new LambdaQueryWrapper<CardRoom>()
                            .eq(CardRoom::getRoomCode, s)) == 0) {
                return s;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成房间号失败，请重试");
    }

    @Override
    @Transactional
    public CardRoomVO createRoom(HttpServletRequest request) {
        User user = loginUser(request);
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

    @Override
    @Transactional
    public CardRoomVO joinRoom(String roomCode, HttpServletRequest request) {
        User user = loginUser(request);

        CardRoom initialRoom = cardRoomMapper.selectOne(
                new LambdaQueryWrapper<CardRoom>()
                        .eq(CardRoom::getRoomCode, roomCode));
        if (initialRoom == null) throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        if (initialRoom.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        RLock lock = redissonClient.getLock(CardConstant.LOCK_JOIN + initialRoom.getId());
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作过于频繁，请稍后重试");
            }

            CardRoom room = requireRoom(initialRoom.getId());
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
                if (member.getStatus() == CardConstant.MEMBER_STATUS_ACTIVE) {
                    return cardRoomViewAssembler.toRoomVO(room, user.getId());
                }
                if (member.getStatus() == CardConstant.MEMBER_STATUS_LEFT) {
                    long activeCount = cardRoomMemberMapper.selectCount(
                            new LambdaQueryWrapper<CardRoomMember>()
                                    .eq(CardRoomMember::getRoomId, room.getId())
                                    .eq(CardRoomMember::getStatus, CardConstant.MEMBER_STATUS_ACTIVE));
                    if (activeCount >= room.getMaxMembers()) {
                        throw new BusinessException(ErrorCode.ROOM_FULL);
                    }
                    int updated = cardRoomMemberMapper.reactivate(member.getId());
                    if (updated != 1) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重新加入房间失败");
                    }
                    member.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
                    member.setJoinTime(new Date());
                    member.setLeaveTime(null);

                    CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
                    UserVO uvo = userService.toUserVO(user);
                    pushAfterCommit(room.getId(), user.getId(), CardWebSocketHandler.EVENT_MEMBER_JOINED, uvo);
                    return vo;
                }
                throw new BusinessException(
                        ErrorCode.ROOM_ALREADY_SETTLED,
                        "你已退出该房间，不能重复加入");
            }

            long activeCount = cardRoomMemberMapper.selectCount(
                    new LambdaQueryWrapper<CardRoomMember>()
                            .eq(CardRoomMember::getRoomId, room.getId())
                            .eq(CardRoomMember::getStatus, CardConstant.MEMBER_STATUS_ACTIVE));
            if (activeCount >= room.getMaxMembers()) {
                throw new BusinessException(ErrorCode.ROOM_FULL);
            }

            member = new CardRoomMember();
            member.setRoomId(room.getId());
            member.setUserId(user.getId());
            member.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
            cardRoomMemberMapper.insert(member);

            CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
            UserVO uvo = userService.toUserVO(user);
            pushAfterCommit(room.getId(), user.getId(), CardWebSocketHandler.EVENT_MEMBER_JOINED, uvo);
            return vo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            releaseLockAfterTransaction(lock);
        }
    }

    @Override
    public CardRoomVO getRoomDetail(Long roomId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireRoom(roomId);
        requireAnyMember(roomId, user.getId());
        return cardRoomViewAssembler.toRoomVO(room, user.getId());
    }

    @Override
    public CardRoomVO getActiveRoom(HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = cardRoomMapper.selectActiveRoomByUserId(user.getId());
        if (room == null) return null;
        return cardRoomViewAssembler.toRoomVO(room, user.getId());
    }

    @Override
    public List<CardRoomHistoryVO> getHistory(int limit, HttpServletRequest request) {
        User user = loginUser(request);
        validateOverviewLimit(limit);
        return cardRoomMapper.selectHistoryByUserId(
                user.getId(),
                Math.min(limit, CardConstant.HISTORY_RETENTION_COUNT));
    }

    @Override
    public List<UserVO> getRanking(int limit, HttpServletRequest request) {
        User user = loginUser(request);
        validateOverviewLimit(limit);
        return userMapper.selectCardRanking(user.getId(), limit).stream()
                .map(userService::toUserVO)
                .toList();
    }

    @Override
    @Transactional
    public void leaveRoom(Long roomId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireRoom(roomId);
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }
        if (room.getOwnerId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.ROOM_NOT_OWNER, "房主不能直接退出房间，请先结束房间");
        }

        CardRoomMember member = requireMember(roomId, user.getId());
        member.setStatus(CardConstant.MEMBER_STATUS_LEFT);
        member.setSettleScore(member.getTotalScore());
        int deltaWins = member.getTotalScore() > 0 ? 1 : 0;
        int deltaLosses = member.getTotalScore() < 0 ? 1 : 0;
        member.setWins(member.getWins() + deltaWins);
        member.setLosses(member.getLosses() + deltaLosses);
        member.setLeaveTime(new Date());
        cardRoomMemberMapper.updateById(member);

        userMapper.addStats(
                user.getId(),
                member.getTotalScore(),
                deltaWins,
                deltaLosses);
        cacheInvalidationService.userChanged(user.getId());

        UserVO uvo = userService.toUserVO(user);
        pushAfterCommit(roomId, user.getId(), CardWebSocketHandler.EVENT_MEMBER_LEFT, uvo);
    }

    @Override
    @Transactional
    public CardRoomVO addTransfer(Long roomId, AddTransferRequest req, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireRoom(roomId);
        requireMember(roomId, user.getId());
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        List<AddTransferRequest.TransferEntry> transfers = req.getTransfers();
        Set<Long> seenUserIds = new HashSet<>();
        Map<Long, Integer> amountsByUserId = new LinkedHashMap<>();
        int totalOut = 0;
        for (AddTransferRequest.TransferEntry t : transfers) {
            int amount = requirePositiveIntegerAmount(t.getAmount());
            if (!seenUserIds.add(t.getToUserId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能重复转账给同一人");
            }
            if (t.getToUserId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能转账给自己");
            }
            amountsByUserId.put(t.getToUserId(), amount);
            totalOut = Math.addExact(totalOut, amount);
        }

        List<Long> activeMemberIds = cardRoomMapper.selectActiveMemberIds(roomId);
        Set<Long> activeSet = new HashSet<>(activeMemberIds);
        if (!activeSet.containsAll(seenUserIds)) {
            throw new BusinessException(ErrorCode.ROUND_MEMBER_MISSING, "收款人必须为在房成员");
        }

        List<AddRoundRequest.ScoreEntry> scoreEntries = new ArrayList<>();
        AddRoundRequest.ScoreEntry selfEntry = new AddRoundRequest.ScoreEntry();
        selfEntry.setUserId(user.getId());
        selfEntry.setScore(-totalOut);
        scoreEntries.add(selfEntry);
        for (AddTransferRequest.TransferEntry t : transfers) {
            AddRoundRequest.ScoreEntry entry = new AddRoundRequest.ScoreEntry();
            entry.setUserId(t.getToUserId());
            entry.setScore(amountsByUserId.get(t.getToUserId()));
            scoreEntries.add(entry);
        }

        int sum = scoreEntries.stream().mapToInt(AddRoundRequest.ScoreEntry::getScore).sum();
        if (sum != 0) throw new BusinessException(ErrorCode.ROUND_SUM_NOT_ZERO);

        String lockKey = CardConstant.LOCK_ROUND + roomId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作过于频繁，请稍后重试");
            }

            int nextRoundNo = cardRoundMapper.selectMaxRoundNo(roomId) + 1;
            CardRound round = new CardRound();
            round.setRoomId(roomId);
            round.setRoundNo(nextRoundNo);
            round.setCreatorId(user.getId());
            round.setSettled(CardConstant.ROUND_UNSETTLED);
            cardRoundMapper.insert(round);

            List<CardRoundScore> scoreEntities = new ArrayList<>();
            for (AddRoundRequest.ScoreEntry se : scoreEntries) {
                CardRoundScore s = new CardRoundScore();
                s.setRoundId(round.getId());
                s.setUserId(se.getUserId());
                s.setScore(se.getScore());
                scoreEntities.add(s);
            }
            cardRoundScoreMapper.insertBatch(scoreEntities);

            for (CardRoundScore s : scoreEntities) {
                cardRoomMemberMapper.updateScoreIncrement(
                        requireMember(roomId, s.getUserId()).getId(), s.getScore());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            releaseLockAfterTransaction(lock);
        }

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        pushAfterCommit(
                roomId,
                user.getId(),
                CardWebSocketHandler.EVENT_ROUND_CREATED,
                vo.getRecentRounds().get(0));
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO addFund(Long roomId, AddFundRequest req, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireRoom(roomId);
        requireMember(roomId, user.getId());
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }
        int fundType = CardConstant.FUND_TYPE_ADD;
        if (req.getType() != null && req.getType() != CardConstant.FUND_TYPE_ADD) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "资金类型错误");
        }
        int amountYuan = requirePositiveIntegerAmount(req.getAmount());
        List<Long> participantIds = req.getParticipantIds();
        Set<Long> participantSet = new LinkedHashSet<>(participantIds);
        if (participantSet.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请至少选择一位平摊成员");
        }
        if (participantSet.contains(user.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "发起人不用选进平摊成员");
        }
        if (participantSet.size() != participantIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分摊人不能重复");
        }

        List<Long> activeMemberIds = cardRoomMapper.selectActiveMemberIds(roomId);
        if (!new HashSet<>(activeMemberIds).containsAll(participantSet)) {
            throw new BusinessException(ErrorCode.ROUND_MEMBER_MISSING, "分摊人包含非房间成员");
        }

        int amountFen = Math.multiplyExact(amountYuan, 100);

        String lockKey = CardConstant.LOCK_FUND + roomId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作过于频繁，请稍后重试");
            }

            CardFundRecord fund = new CardFundRecord();
            fund.setRoomId(roomId);
            fund.setType(fundType);
            fund.setAmount(amountFen);
            fund.setCreatorId(user.getId());
            cardFundRecordMapper.insert(fund);

            List<CardFundParticipant> fundParticipants = new ArrayList<>();
            for (Long pid : participantIds) {
                CardFundParticipant fp = new CardFundParticipant();
                fp.setFundId(fund.getId());
                fp.setUserId(pid);
                fundParticipants.add(fp);
            }
            cardFundParticipantMapper.insertBatch(fundParticipants);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            releaseLockAfterTransaction(lock);
        }

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        pushAfterCommit(
                roomId,
                user.getId(),
                CardWebSocketHandler.EVENT_FUND_CREATED,
                vo.getRecentFunds().get(0));
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO endRoom(Long roomId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireRoom(roomId);
        requireOwner(room, user.getId());
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }

        String lockKey = CardConstant.LOCK_END + roomId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.ROOM_ALREADY_SETTLED);
            }

            room = cardRoomMapper.selectById(roomId);
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
                userMapper.addStats(
                        member.getUserId(),
                        finalScore,
                        deltaWins,
                        deltaLosses);
                cacheInvalidationService.userChanged(member.getUserId());
            }

            if (!toSettle.isEmpty()) {
                cardRoomMemberMapper.batchSettle(toSettle);
            }

            room.setStatus(CardConstant.ROOM_STATUS_ENDED);
            room.setSettleTime(new Date());
            cardRoomMapper.updateById(room);

            CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
            pushAfterCommit(roomId, user.getId(), CardWebSocketHandler.EVENT_ROOM_CLOSED, vo);
            runAfterCommit(this::cleanupCardHistorySafely);
            return vo;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            releaseLockAfterTransaction(lock);
        }
    }

    @Override
    @Transactional
    public CardRoomVO requestRoundUndo(Long roomId, Long roundId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireUndoableRoom(roomId, user.getId());
        CardRound round = cardRoundMapper.selectById(roundId);
        if (round == null || !roomId.equals(round.getRoomId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收支记录不存在");
        }
        if (!user.getId().equals(round.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有记这条账的人才能申请撤销");
        }
        List<CardRoundScore> scores = cardRoundScoreMapper.selectByRoundId(roundId);
        Set<Long> participantIds = CardLedgerParticipantUtils.roundScoreUserIds(scores);
        createOrApproveUndo(roomId, CardConstant.UNDO_TARGET_ROUND, roundId, participantIds, user.getId());
        tryCompleteUndo(room, CardConstant.UNDO_TARGET_ROUND, roundId, participantIds);
        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        pushAfterCommit(roomId, user.getId(), CardWebSocketHandler.EVENT_ROUND_CREATED, vo);
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO requestFundUndo(Long roomId, Long fundId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireUndoableRoom(roomId, user.getId());
        CardFundRecord fund = cardFundRecordMapper.selectById(fundId);
        if (fund == null || !roomId.equals(fund.getRoomId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资金记录不存在");
        }
        if (!user.getId().equals(fund.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有记这条账的人才能申请撤销");
        }
        List<CardFundParticipant> participants = cardFundParticipantMapper.selectByFundId(fundId);
        Set<Long> participantIds = CardLedgerParticipantUtils.fundParticipantUserIds(fund, participants);
        createOrApproveUndo(roomId, CardConstant.UNDO_TARGET_FUND, fundId, participantIds, user.getId());
        tryCompleteUndo(room, CardConstant.UNDO_TARGET_FUND, fundId, participantIds);
        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        pushAfterCommit(roomId, user.getId(), CardWebSocketHandler.EVENT_FUND_CREATED, vo);
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO approveUndo(Long roomId, Long undoRequestId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireUndoableRoom(roomId, user.getId());
        CardUndoRequest undo = cardUndoRequestMapper.selectById(undoRequestId);
        if (undo == null || !roomId.equals(undo.getRoomId())
                || undo.getStatus() != CardConstant.UNDO_STATUS_PENDING) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "撤销申请不存在或已处理");
        }
        Set<Long> participantIds = undoParticipantIds(undo);
        if (!participantIds.contains(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有参与这条记录的人才能同意撤销");
        }
        approveUndoRequest(undo.getId(), user.getId());
        tryCompleteUndo(room, undo.getTargetType(), undo.getTargetId(), participantIds);
        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        pushAfterCommit(roomId, user.getId(), CardWebSocketHandler.EVENT_ROUND_CREATED, vo);
        return vo;
    }

    private CardRoom requireUndoableRoom(Long roomId, Long userId) {
        CardRoom room = requireRoom(roomId);
        requireMember(roomId, userId);
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED, "房间已结束，不能撤销历史记录");
        }
        return room;
    }

    private void createOrApproveUndo(
            Long roomId,
            Integer targetType,
            Long targetId,
            Set<Long> participantIds,
            Long userId) {
        if (!participantIds.contains(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有参与这条记录的人才能申请撤销");
        }
        CardUndoRequest undo = cardUndoRequestMapper.selectPending(roomId, targetType, targetId);
        if (undo == null) {
            undo = new CardUndoRequest();
            undo.setRoomId(roomId);
            undo.setTargetType(targetType);
            undo.setTargetId(targetId);
            undo.setRequesterId(userId);
            undo.setStatus(CardConstant.UNDO_STATUS_PENDING);
            cardUndoRequestMapper.insert(undo);
        }
        approveUndoRequest(undo.getId(), userId);
    }

    private void approveUndoRequest(Long requestId, Long userId) {
        CardUndoApproval approval = new CardUndoApproval();
        approval.setRequestId(requestId);
        approval.setUserId(userId);
        cardUndoApprovalMapper.insertIgnore(approval);
    }

    private Set<Long> undoParticipantIds(CardUndoRequest undo) {
        if (undo.getTargetType() == CardConstant.UNDO_TARGET_ROUND) {
            return CardLedgerParticipantUtils.roundScoreUserIds(
                    cardRoundScoreMapper.selectByRoundId(undo.getTargetId()));
        }
        if (undo.getTargetType() == CardConstant.UNDO_TARGET_FUND) {
            CardFundRecord fund = cardFundRecordMapper.selectById(undo.getTargetId());
            if (fund == null) return Set.of();
            return CardLedgerParticipantUtils.fundParticipantUserIds(
                    fund,
                    cardFundParticipantMapper.selectByFundId(undo.getTargetId()));
        }
        return Set.of();
    }

    private void tryCompleteUndo(
            CardRoom room,
            Integer targetType,
            Long targetId,
            Set<Long> participantIds) {
        CardUndoRequest undo = cardUndoRequestMapper.selectPending(room.getId(), targetType, targetId);
        if (undo == null || participantIds.isEmpty()) return;
        int approvedCount = cardUndoApprovalMapper.countByRequestId(undo.getId());
        if (approvedCount < participantIds.size()) return;

        if (targetType == CardConstant.UNDO_TARGET_ROUND) {
            undoRound(room.getId(), targetId);
        } else if (targetType == CardConstant.UNDO_TARGET_FUND) {
            undoFund(targetId);
        }
        cardUndoRequestMapper.markDone(undo.getId());
    }

    private void undoRound(Long roomId, Long roundId) {
        List<CardRoundScore> scores = cardRoundScoreMapper.selectByRoundId(roundId);
        for (CardRoundScore score : scores) {
            CardRoomMember member = requireMember(roomId, score.getUserId());
            cardRoomMemberMapper.updateScoreIncrement(member.getId(), -score.getScore());
        }
        cardRoundScoreMapper.deleteByRoundId(roundId);
        cardRoundMapper.deleteById(roundId);
    }

    private void undoFund(Long fundId) {
        cardFundParticipantMapper.deleteByFundId(fundId);
        cardFundRecordMapper.deleteById(fundId);
    }

    private void checkNotInActiveRoom(Long userId) {
        CardRoom existing = cardRoomMapper.selectActiveRoomByUserId(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_IN);
        }
    }

    private void validateOverviewLimit(int limit) {
        if (limit <= 0 || limit > MAX_OVERVIEW_LIMIT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "查询数量必须为 1 到 20");
        }
    }

    private int requirePositiveIntegerAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "金额不能为空");
        }
        try {
            int value = amount.intValueExact();
            if (value <= 0 || value > 999_999) {
                throw new BusinessException(
                        ErrorCode.PARAM_ERROR,
                        "金额只能输入1到999999的正整数");
            }
            return value;
        } catch (ArithmeticException e) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "金额只能输入1到999999的正整数");
        }
    }

    private void pushAfterCommit(Long roomId, Long excludeUserId, String type, Object data) {
        runAfterCommit(() -> safePush(roomId, excludeUserId, type, data));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private void releaseLockAfterTransaction(RLock lock) {
        if (!lock.isHeldByCurrentThread()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            lock.unlock();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
    }

    private void safePush(Long roomId, Long excludeUserId, String type, Object data) {
        try {
            cardWebSocketHandler.pushEvent(roomId, excludeUserId, type, data);
        } catch (Exception e) {
            log.error("WS push failed type={} roomId={}", type, roomId, e);
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
