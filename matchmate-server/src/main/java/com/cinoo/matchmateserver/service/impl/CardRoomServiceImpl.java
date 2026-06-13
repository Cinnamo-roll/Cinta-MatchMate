package com.cinoo.matchmateserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinoo.matchmateserver.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.constant.CardConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.*;
import com.cinoo.matchmateserver.model.domain.*;
import com.cinoo.matchmateserver.model.request.AddExpenseRequest;
import com.cinoo.matchmateserver.model.request.AddFundRequest;
import com.cinoo.matchmateserver.model.request.AddRoundRequest;
import com.cinoo.matchmateserver.model.request.AddTransferRequest;
import com.cinoo.matchmateserver.model.vo.*;
import com.cinoo.matchmateserver.service.CardRoomService;
import com.cinoo.matchmateserver.service.DataRetentionService;
import com.cinoo.matchmateserver.service.UserService;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardRoomServiceImpl implements CardRoomService {

    private final CardRoomMapper cardRoomMapper;
    private final CardRoomMemberMapper cardRoomMemberMapper;
    private final CardRoundMapper cardRoundMapper;
    private final CardRoundScoreMapper cardRoundScoreMapper;
    private final CardExpenseMapper cardExpenseMapper;
    private final CardExpenseParticipantMapper cardExpenseParticipantMapper;
    private final CardFundRecordMapper cardFundRecordMapper;
    private final CardFundParticipantMapper cardFundParticipantMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final DataRetentionService dataRetentionService;
    private final CacheInvalidationService cacheInvalidationService;
    private final RedissonClient redissonClient;
    private final CardWebSocketHandler cardWebSocketHandler;

    private static final int RECENT_LIMIT = 20;
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

    private CardRoomVO toRoomVO(CardRoom room, Long currentUserId) {
        List<CardRoomMember> members = cardRoomMemberMapper.selectByRoomId(room.getId());
        List<CardRound> rounds = cardRoundMapper.selectByRoomId(room.getId(), RECENT_LIMIT);
        List<CardExpense> expenses = cardExpenseMapper.selectByRoomId(room.getId(), RECENT_LIMIT);
        // fund tables may not exist yet; degrade gracefully
        List<CardFundRecord> funds;
        List<CardFundParticipant> fundParticipants;
        try {
            funds = cardFundRecordMapper.selectByRoomId(room.getId(), RECENT_LIMIT);
            fundParticipants = funds.isEmpty()
                    ? List.of()
                    : cardFundParticipantMapper.selectByFundIds(
                            funds.stream().map(CardFundRecord::getId).toList());
        } catch (Exception e) {
            log.warn("Fund tables not available for room {}: {}", room.getId(), e.getMessage());
            funds = List.of();
            fundParticipants = List.of();
        }

        List<CardRoundScore> scores = rounds.isEmpty()
                ? List.of()
                : cardRoundScoreMapper.selectByRoundIds(
                        rounds.stream().map(CardRound::getId).toList());
        List<CardExpenseParticipant> participants = expenses.isEmpty()
                ? List.of()
                : cardExpenseParticipantMapper.selectByExpenseIds(
                        expenses.stream().map(CardExpense::getId).toList());

        Set<Long> userIds = new HashSet<>();
        userIds.add(room.getOwnerId());
        members.forEach(member -> userIds.add(member.getUserId()));
        scores.forEach(score -> userIds.add(score.getUserId()));
        expenses.forEach(expense -> userIds.add(expense.getPayerId()));
        participants.forEach(participant -> userIds.add(participant.getUserId()));
        funds.forEach(fund -> userIds.add(fund.getCreatorId()));
        fundParticipants.forEach(fp -> userIds.add(fp.getUserId()));
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, List<CardRoundScore>> scoresByRoundId = scores.stream()
                .collect(Collectors.groupingBy(CardRoundScore::getRoundId));
        Map<Long, List<CardExpenseParticipant>> participantsByExpenseId = participants.stream()
                .collect(Collectors.groupingBy(CardExpenseParticipant::getExpenseId));
        Map<Long, List<CardFundParticipant>> fundParticipantsByFundId = fundParticipants.stream()
                .collect(Collectors.groupingBy(CardFundParticipant::getFundId));

        CardRoomVO vo = new CardRoomVO();
        vo.setRoomId(room.getId());
        vo.setRoomCode(room.getRoomCode());
        vo.setOwnerId(room.getOwnerId());
        vo.setStatus(room.getStatus());
        vo.setMaxMembers(room.getMaxMembers());
        vo.setTeaAmount(room.getTeaAmount());
        vo.setMealAmount(room.getMealAmount());
        vo.setSettleTime(room.getSettleTime());
        vo.setCreateTime(room.getCreateTime());

        User owner = usersById.get(room.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getUsername() : null);

        List<CardRoomMemberVO> memberVOs = new ArrayList<>();
        for (CardRoomMember m : members) {
            User u = usersById.get(m.getUserId());
            CardRoomMemberVO mv = new CardRoomMemberVO();
            mv.setUserId(m.getUserId());
            mv.setUsername(u != null ? u.getUsername() : null);
            mv.setAvatarUrl(u != null ? u.getAvatarUrl() : null);
            mv.setTotalScore(m.getTotalScore());
            mv.setStatus(m.getStatus());
            mv.setWins(m.getWins());
            mv.setLosses(m.getLosses());
            mv.setJoinTime(m.getJoinTime());
            memberVOs.add(mv);
        }
        vo.setMembers(memberVOs);

        vo.setRecentRounds(rounds.stream()
                .map(round -> toRoundVO(
                        round,
                        scoresByRoundId.getOrDefault(round.getId(), List.of()),
                        usersById))
                .toList());
        vo.setRecentExpenses(expenses.stream()
                .map(expense -> toExpenseVO(
                        expense,
                        participantsByExpenseId.getOrDefault(expense.getId(), List.of()),
                        usersById))
                .toList());
        vo.setRecentFunds(funds.stream()
                .map(fund -> toFundRecordVO(
                        fund,
                        fundParticipantsByFundId.getOrDefault(fund.getId(), List.of()),
                        usersById))
                .toList());

        // 计算当前用户在该房间的平摊资金余额
        int balance = 0;
        for (CardFundRecord fund : funds) {
            List<CardFundParticipant> fps = fundParticipantsByFundId.getOrDefault(fund.getId(), List.of());
            int shareCount = fps.size();
            if (shareCount == 0) continue;
            if (fund.getCreatorId().equals(currentUserId)) {
                balance += fund.getType() == CardConstant.FUND_TYPE_ADD
                        ? fund.getAmount()
                        : -fund.getAmount();
            }
            int sharePerPerson = fund.getAmount() / shareCount;
            int remainder = fund.getAmount() % shareCount;
            int idx = 0;
            for (CardFundParticipant fp : fps) {
                int charge = sharePerPerson + (idx < remainder ? 1 : 0);
                if (fp.getUserId().equals(currentUserId)) {
                    balance += fund.getType() == CardConstant.FUND_TYPE_ADD
                            ? -charge
                            : charge;
                }
                idx++;
            }
        }
        vo.setFundBalance(balance);

        return vo;
    }

    private CardRoundVO toRoundVO(
            CardRound round,
            List<CardRoundScore> scores,
            Map<Long, User> usersById) {
        CardRoundVO vo = new CardRoundVO();
        vo.setRoundId(round.getId());
        vo.setRoundNo(round.getRoundNo());
        vo.setCreatorId(round.getCreatorId());
        vo.setCreateTime(round.getCreateTime());

        List<CardRoundVO.ScoreEntry> entries = new ArrayList<>();
        for (CardRoundScore s : scores) {
            User u = usersById.get(s.getUserId());
            CardRoundVO.ScoreEntry e = new CardRoundVO.ScoreEntry();
            e.setUserId(s.getUserId());
            e.setUsername(u != null ? u.getUsername() : null);
            e.setScore(s.getScore());
            entries.add(e);
        }
        vo.setScores(entries);
        return vo;
    }

    private CardExpenseVO toExpenseVO(
            CardExpense expense,
            List<CardExpenseParticipant> participants,
            Map<Long, User> usersById) {
        CardExpenseVO vo = new CardExpenseVO();
        vo.setExpenseId(expense.getId());
        vo.setType(expense.getType());
        vo.setAmount(expense.getAmount());
        vo.setPayerId(expense.getPayerId());
        vo.setCreateTime(expense.getCreateTime());

        User payer = usersById.get(expense.getPayerId());
        vo.setPayerName(payer != null ? payer.getUsername() : null);

        List<CardExpenseVO.Participant> ptps = new ArrayList<>();
        for (CardExpenseParticipant p : participants) {
            User u = usersById.get(p.getUserId());
            CardExpenseVO.Participant pt = new CardExpenseVO.Participant();
            pt.setUserId(p.getUserId());
            pt.setUsername(u != null ? u.getUsername() : null);
            ptps.add(pt);
        }
        vo.setParticipants(ptps);
        return vo;
    }

    private CardFundRecordVO toFundRecordVO(
            CardFundRecord fund,
            List<CardFundParticipant> participants,
            Map<Long, User> usersById) {
        CardFundRecordVO vo = new CardFundRecordVO();
        vo.setFundId(fund.getId());
        vo.setType(fund.getType());
        vo.setAmount(fund.getAmount());
        vo.setCreatorId(fund.getCreatorId());
        vo.setCreateTime(fund.getCreateTime());

        User creator = usersById.get(fund.getCreatorId());
        vo.setCreatorName(creator != null ? creator.getUsername() : null);

        List<CardFundRecordVO.Participant> ptps = new ArrayList<>();
        for (CardFundParticipant p : participants) {
            User u = usersById.get(p.getUserId());
            CardFundRecordVO.Participant pt = new CardFundRecordVO.Participant();
            pt.setUserId(p.getUserId());
            pt.setUsername(u != null ? u.getUsername() : null);
            ptps.add(pt);
        }
        vo.setParticipants(ptps);
        return vo;
    }

    // ── 业务方法 ──

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

        return toRoomVO(room, user.getId());
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
                    return toRoomVO(room, user.getId());
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

            CardRoomVO vo = toRoomVO(room, user.getId());
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
        return toRoomVO(room, user.getId());
    }

    @Override
    public CardRoomVO getActiveRoom(HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = cardRoomMapper.selectActiveRoomByUserId(user.getId());
        if (room == null) return null;
        return toRoomVO(room, user.getId());
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

        CardRoomVO vo = toRoomVO(room, user.getId());
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
        if (req.getType() != CardConstant.FUND_TYPE_ADD &&
                req.getType() != CardConstant.FUND_TYPE_DEDUCT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "资金类型错误");
        }
        int amountYuan = requirePositiveIntegerAmount(req.getAmount());
        List<Long> participantIds = req.getParticipantIds();
        Set<Long> participantSet = new LinkedHashSet<>(participantIds);
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
            fund.setType(req.getType());
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

        CardRoomVO vo = toRoomVO(room, user.getId());
        pushAfterCommit(
                roomId,
                user.getId(),
                CardWebSocketHandler.EVENT_FUND_CREATED,
                vo.getRecentFunds().get(0));
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO addExpense(Long roomId, AddExpenseRequest req, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = requireRoom(roomId);
        requireOwner(room, user.getId());
        if (room.getStatus() != CardConstant.ROOM_STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_ENDED);
        }
        if (req.getType() != CardConstant.EXPENSE_TYPE_TEA &&
                req.getType() != CardConstant.EXPENSE_TYPE_MEAL) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "费用类型错误");
        }

        List<Long> participantIds = req.getParticipantIds();
        Set<Long> participantSet = new LinkedHashSet<>(participantIds);
        if (participantSet.size() != participantIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分摊人不能重复");
        }

        List<Long> activeMemberIds = cardRoomMapper.selectActiveMemberIds(roomId);
        if (!new HashSet<>(activeMemberIds).containsAll(participantSet)) {
            throw new BusinessException(ErrorCode.ROUND_MEMBER_MISSING, "分摊人包含非房间成员");
        }

        String lockKey = CardConstant.LOCK_EXPENSE + roomId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作过于频繁，请稍后重试");
            }

            CardExpense expense = new CardExpense();
            expense.setRoomId(roomId);
            expense.setType(req.getType());
            expense.setAmount(req.getAmount());
            expense.setPayerId(user.getId());
            cardExpenseMapper.insert(expense);

            List<CardExpenseParticipant> participants = new ArrayList<>();
            for (Long pid : participantIds) {
                CardExpenseParticipant p = new CardExpenseParticipant();
                p.setExpenseId(expense.getId());
                p.setUserId(pid);
                participants.add(p);
            }
            cardExpenseParticipantMapper.insertBatch(participants);

            int participantCount = participantIds.size();
            int sharePerPerson = req.getAmount() / participantCount;
            int remainder = req.getAmount() % participantCount;

            for (int i = 0; i < participantIds.size(); i++) {
                Long pid = participantIds.get(i);
                int charge = sharePerPerson + (i < remainder ? 1 : 0);
                int delta = -charge;
                if (pid.equals(user.getId())) {
                    delta += req.getAmount();
                }
                cardRoomMemberMapper.updateScoreIncrement(
                        requireMember(roomId, pid).getId(), delta);
            }
            if (!participantSet.contains(user.getId())) {
                cardRoomMemberMapper.updateScoreIncrement(
                        requireMember(roomId, user.getId()).getId(), req.getAmount());
            }

            if (req.getType() == CardConstant.EXPENSE_TYPE_TEA) {
                room.setTeaAmount(room.getTeaAmount() + req.getAmount());
            } else {
                room.setMealAmount(room.getMealAmount() + req.getAmount());
            }
            cardRoomMapper.updateById(room);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            releaseLockAfterTransaction(lock);
        }

        CardRoomVO vo = toRoomVO(room, user.getId());
        pushAfterCommit(
                roomId,
                user.getId(),
                CardWebSocketHandler.EVENT_EXPENSE_CREATED,
                vo.getRecentExpenses().get(0));
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

            CardRoomVO vo = toRoomVO(room, user.getId());
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
