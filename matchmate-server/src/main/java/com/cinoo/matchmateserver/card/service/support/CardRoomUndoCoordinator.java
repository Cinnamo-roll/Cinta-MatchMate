package com.cinoo.matchmateserver.card.service.support;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.constant.CardRoomEventType;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.card.mapper.CardFundParticipantMapper;
import com.cinoo.matchmateserver.card.mapper.CardFundRecordMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoundMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoundScoreMapper;
import com.cinoo.matchmateserver.card.model.entity.CardFundParticipant;
import com.cinoo.matchmateserver.card.model.entity.CardFundRecord;
import com.cinoo.matchmateserver.card.model.entity.CardRoom;
import com.cinoo.matchmateserver.card.model.entity.CardRound;
import com.cinoo.matchmateserver.card.model.entity.CardRoundScore;
import com.cinoo.matchmateserver.card.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.card.service.assembler.CardRoomViewAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CardRoomUndoCoordinator {

    private final CardRoundMapper cardRoundMapper;
    private final CardRoundScoreMapper cardRoundScoreMapper;
    private final CardFundRecordMapper cardFundRecordMapper;
    private final CardFundParticipantMapper cardFundParticipantMapper;
    private final CardRoomViewAssembler cardRoomViewAssembler;
    private final CardRoomEventPublisher cardRoomEventPublisher;
    private final CardRoomUndoProcessor cardRoomUndoProcessor;
    private final CardRoomAccessGuard cardRoomAccessGuard;

    public CardRoomVO requestRoundUndo(Long roomId, Long roundId, Long userId) {
        CardRoom room = requireUndoableRoom(roomId, userId);
        CardRound round = cardRoundMapper.selectById(roundId);
        if (round == null || !roomId.equals(round.getRoomId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收支记录不存在");
        }
        if (!userId.equals(round.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有记这条账的人才能申请撤销");
        }

        List<CardRoundScore> scores = cardRoundScoreMapper.selectByRoundId(roundId);
        Set<Long> participantIds = CardLedgerParticipantUtils.roundScoreUserIds(scores);
        cardRoomUndoProcessor.requestUndo(
                room,
                CardConstant.UNDO_TARGET_ROUND,
                roundId,
                participantIds,
                userId);

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, userId);
        cardRoomEventPublisher.pushAfterCommit(roomId, userId, CardRoomEventType.ROUND_CREATED, vo);
        return vo;
    }

    public CardRoomVO requestFundUndo(Long roomId, Long fundId, Long userId) {
        CardRoom room = requireUndoableRoom(roomId, userId);
        CardFundRecord fund = cardFundRecordMapper.selectById(fundId);
        if (fund == null || !roomId.equals(fund.getRoomId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资金记录不存在");
        }
        if (!userId.equals(fund.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有记这条账的人才能申请撤销");
        }

        List<CardFundParticipant> participants = cardFundParticipantMapper.selectByFundId(fundId);
        Set<Long> participantIds = CardLedgerParticipantUtils.fundParticipantUserIds(fund, participants);
        cardRoomUndoProcessor.requestUndo(
                room,
                CardConstant.UNDO_TARGET_FUND,
                fundId,
                participantIds,
                userId);

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, userId);
        cardRoomEventPublisher.pushAfterCommit(roomId, userId, CardRoomEventType.FUND_CREATED, vo);
        return vo;
    }

    public CardRoomVO approveUndo(Long roomId, Long undoRequestId, Long userId) {
        CardRoom room = requireUndoableRoom(roomId, userId);
        cardRoomUndoProcessor.approveUndo(room, undoRequestId, userId);

        CardRoomVO vo = cardRoomViewAssembler.toRoomVO(room, userId);
        cardRoomEventPublisher.pushAfterCommit(roomId, userId, CardRoomEventType.ROUND_CREATED, vo);
        return vo;
    }

    private CardRoom requireUndoableRoom(Long roomId, Long userId) {
        return cardRoomAccessGuard.requireActiveRoomForMember(
                roomId,
                userId,
                "房间已结束，不能撤销历史记录");
    }
}
