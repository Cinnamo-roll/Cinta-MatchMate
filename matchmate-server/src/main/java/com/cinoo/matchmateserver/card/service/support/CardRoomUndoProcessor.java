package com.cinoo.matchmateserver.card.service.support;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.card.mapper.CardFundParticipantMapper;
import com.cinoo.matchmateserver.card.mapper.CardFundRecordMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoomMemberMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoundMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoundScoreMapper;
import com.cinoo.matchmateserver.card.mapper.CardUndoApprovalMapper;
import com.cinoo.matchmateserver.card.mapper.CardUndoRequestMapper;
import com.cinoo.matchmateserver.card.model.entity.CardFundRecord;
import com.cinoo.matchmateserver.card.model.entity.CardRoom;
import com.cinoo.matchmateserver.card.model.entity.CardRoomMember;
import com.cinoo.matchmateserver.card.model.entity.CardRoundScore;
import com.cinoo.matchmateserver.card.model.entity.CardUndoApproval;
import com.cinoo.matchmateserver.card.model.entity.CardUndoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CardRoomUndoProcessor {

    private final CardRoomMemberMapper cardRoomMemberMapper;
    private final CardRoundMapper cardRoundMapper;
    private final CardRoundScoreMapper cardRoundScoreMapper;
    private final CardFundRecordMapper cardFundRecordMapper;
    private final CardFundParticipantMapper cardFundParticipantMapper;
    private final CardUndoRequestMapper cardUndoRequestMapper;
    private final CardUndoApprovalMapper cardUndoApprovalMapper;
    private final CardRoomAccessGuard cardRoomAccessGuard;

    public void requestUndo(
            CardRoom room,
            Integer targetType,
            Long targetId,
            Set<Long> participantIds,
            Long userId) {
        if (!participantIds.contains(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有参与这条记录的人才能申请撤销");
        }
        CardUndoRequest undo = cardUndoRequestMapper.selectPending(room.getId(), targetType, targetId);
        if (undo == null) {
            undo = new CardUndoRequest();
            undo.setRoomId(room.getId());
            undo.setTargetType(targetType);
            undo.setTargetId(targetId);
            undo.setRequesterId(userId);
            undo.setStatus(CardConstant.UNDO_STATUS_PENDING);
            cardUndoRequestMapper.insert(undo);
        }
        approveUndoRequest(undo.getId(), userId);
        tryCompleteUndo(room, targetType, targetId, participantIds);
    }

    public void approveUndo(CardRoom room, Long undoRequestId, Long userId) {
        CardUndoRequest undo = cardUndoRequestMapper.selectById(undoRequestId);
        if (undo == null || !room.getId().equals(undo.getRoomId())
                || undo.getStatus() != CardConstant.UNDO_STATUS_PENDING) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "撤销申请不存在或已处理");
        }
        Set<Long> participantIds = undoParticipantIds(undo);
        if (!participantIds.contains(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只有参与这条记录的人才能同意撤销");
        }
        approveUndoRequest(undo.getId(), userId);
        tryCompleteUndo(room, undo.getTargetType(), undo.getTargetId(), participantIds);
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
            CardRoomMember member = cardRoomAccessGuard.requireActiveMember(roomId, score.getUserId());
            cardRoomMemberMapper.updateScoreIncrement(member.getId(), -score.getScore());
        }
        cardRoundScoreMapper.deleteByRoundId(roundId);
        cardRoundMapper.deleteById(roundId);
    }

    private void undoFund(Long fundId) {
        cardFundParticipantMapper.deleteByFundId(fundId);
        cardFundRecordMapper.deleteById(fundId);
    }
}
