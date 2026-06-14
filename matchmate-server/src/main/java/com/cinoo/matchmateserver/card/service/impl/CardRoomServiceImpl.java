package com.cinoo.matchmateserver.card.service.impl;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.constant.CardRoomEventType;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.card.mapper.CardRoomMapper;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.card.model.entity.CardRoom;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.card.model.request.AddFundRequest;
import com.cinoo.matchmateserver.card.model.request.AddTransferRequest;
import com.cinoo.matchmateserver.card.model.vo.CardRoomHistoryVO;
import com.cinoo.matchmateserver.card.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.card.service.CardRoomService;
import com.cinoo.matchmateserver.user.service.UserService;
import com.cinoo.matchmateserver.card.service.assembler.CardRoomViewAssembler;
import com.cinoo.matchmateserver.card.service.support.CardRoomAccessGuard;
import com.cinoo.matchmateserver.card.service.support.CardLedgerWriteProcessor;
import com.cinoo.matchmateserver.card.service.support.CardRoomEventPublisher;
import com.cinoo.matchmateserver.card.service.support.CardRoomLifecycleProcessor;
import com.cinoo.matchmateserver.card.service.support.CardRoomUndoCoordinator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardRoomServiceImpl implements CardRoomService {

    private static final int MAX_OVERVIEW_LIMIT = 20;

    private final CardRoomMapper cardRoomMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final CardRoomViewAssembler cardRoomViewAssembler;
    private final CardRoomAccessGuard cardRoomAccessGuard;
    private final CardLedgerWriteProcessor cardLedgerWriteProcessor;
    private final CardRoomEventPublisher cardRoomEventPublisher;
    private final CardRoomLifecycleProcessor cardRoomLifecycleProcessor;
    private final CardRoomUndoCoordinator cardRoomUndoCoordinator;

    @Override
    @Transactional
    public CardRoomVO createRoom(HttpServletRequest request) {
        User user = loginUser(request);
        return cardRoomLifecycleProcessor.createRoom(user);
    }

    @Override
    @Transactional
    public CardRoomVO joinRoom(String roomCode, HttpServletRequest request) {
        User user = loginUser(request);
        return cardRoomLifecycleProcessor.joinRoom(roomCode, user);
    }

    @Override
    public CardRoomVO getRoomDetail(Long roomId, HttpServletRequest request) {
        User user = loginUser(request);
        CardRoom room = cardRoomAccessGuard.requireRoom(roomId);
        cardRoomAccessGuard.requireAnyMember(roomId, user.getId());
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
        cardRoomLifecycleProcessor.leaveRoom(roomId, user);
    }

    @Override
    @Transactional
    public CardRoomVO addTransfer(Long roomId, AddTransferRequest request, HttpServletRequest httpRequest) {
        User user = loginUser(httpRequest);
        CardRoom room = cardRoomAccessGuard.requireActiveRoomForMember(roomId, user.getId());
        cardLedgerWriteProcessor.addTransfer(roomId, user, request);

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        cardRoomEventPublisher.pushAfterCommit(
                roomId,
                user.getId(),
                CardRoomEventType.ROUND_CREATED,
                vo.getRecentRounds().get(0));
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO addFund(Long roomId, AddFundRequest request, HttpServletRequest httpRequest) {
        User user = loginUser(httpRequest);
        CardRoom room = cardRoomAccessGuard.requireActiveRoomForMember(roomId, user.getId());
        cardLedgerWriteProcessor.addFund(roomId, user, request);

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, user.getId());
        cardRoomEventPublisher.pushAfterCommit(
                roomId,
                user.getId(),
                CardRoomEventType.FUND_CREATED,
                vo.getRecentFunds().get(0));
        return vo;
    }

    @Override
    @Transactional
    public CardRoomVO endRoom(Long roomId, HttpServletRequest request) {
        User user = loginUser(request);
        return cardRoomLifecycleProcessor.endRoom(roomId, user);
    }

    @Override
    @Transactional
    public CardRoomVO requestRoundUndo(Long roomId, Long roundId, HttpServletRequest request) {
        User user = loginUser(request);
        return cardRoomUndoCoordinator.requestRoundUndo(roomId, roundId, user.getId());
    }

    @Override
    @Transactional
    public CardRoomVO requestFundUndo(Long roomId, Long fundId, HttpServletRequest request) {
        User user = loginUser(request);
        return cardRoomUndoCoordinator.requestFundUndo(roomId, fundId, user.getId());
    }

    @Override
    @Transactional
    public CardRoomVO approveUndo(Long roomId, Long undoRequestId, HttpServletRequest request) {
        User user = loginUser(request);
        return cardRoomUndoCoordinator.approveUndo(roomId, undoRequestId, user.getId());
    }

    private User loginUser(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }

    private void validateOverviewLimit(int limit) {
        if (limit <= 0 || limit > MAX_OVERVIEW_LIMIT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "查询数量必须在 1 到 20 之间");
        }
    }
}
