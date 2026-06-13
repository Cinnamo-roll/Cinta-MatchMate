package com.cinoo.matchmateserver.service.support;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.constant.CardConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.CardFundParticipantMapper;
import com.cinoo.matchmateserver.mapper.CardFundRecordMapper;
import com.cinoo.matchmateserver.mapper.CardRoomMapper;
import com.cinoo.matchmateserver.mapper.CardRoomMemberMapper;
import com.cinoo.matchmateserver.mapper.CardRoundMapper;
import com.cinoo.matchmateserver.mapper.CardRoundScoreMapper;
import com.cinoo.matchmateserver.model.domain.CardFundParticipant;
import com.cinoo.matchmateserver.model.domain.CardFundRecord;
import com.cinoo.matchmateserver.model.domain.CardRoomMember;
import com.cinoo.matchmateserver.model.domain.CardRound;
import com.cinoo.matchmateserver.model.domain.CardRoundScore;
import com.cinoo.matchmateserver.model.domain.User;
import com.cinoo.matchmateserver.model.request.AddFundRequest;
import com.cinoo.matchmateserver.model.request.AddTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CardLedgerWriteProcessor {

    private final CardRoomMapper cardRoomMapper;
    private final CardRoomMemberMapper cardRoomMemberMapper;
    private final CardRoundMapper cardRoundMapper;
    private final CardRoundScoreMapper cardRoundScoreMapper;
    private final CardFundRecordMapper cardFundRecordMapper;
    private final CardFundParticipantMapper cardFundParticipantMapper;
    private final CardRedisLockExecutor cardRedisLockExecutor;
    private final CardRoomAccessGuard cardRoomAccessGuard;

    public void addTransfer(Long roomId, User user, AddTransferRequest request) {
        Map<Long, Integer> scoresByUserId = buildTransferScores(roomId, user, request);
        cardRedisLockExecutor.run(
                CardConstant.LOCK_ROUND + roomId,
                3,
                () -> insertRound(roomId, user.getId(), scoresByUserId));
    }

    public void addFund(Long roomId, User user, AddFundRequest request) {
        if (request.getType() != null && request.getType() != CardConstant.FUND_TYPE_ADD) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "资金平摊类型错误");
        }
        int amountFen = CardAmountValidator.requirePositiveIntegerYuanAsFen(request.getAmount());
        List<Long> participantIds = validateFundParticipants(roomId, user.getId(), request.getParticipantIds());

        cardRedisLockExecutor.run(
                CardConstant.LOCK_FUND + roomId,
                3,
                () -> insertFund(roomId, user.getId(), amountFen, participantIds));
    }

    private Map<Long, Integer> buildTransferScores(
            Long roomId,
            User user,
            AddTransferRequest request) {
        Set<Long> seenUserIds = new HashSet<>();
        Map<Long, Integer> receiverAmountsByUserId = new LinkedHashMap<>();
        int totalOut = 0;

        for (AddTransferRequest.TransferEntry transfer : request.getTransfers()) {
            int amount = CardAmountValidator.requirePositiveIntegerYuan(transfer.getAmount());
            Long receiverId = transfer.getToUserId();
            if (!seenUserIds.add(receiverId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能重复转账给同一个人");
            }
            if (receiverId.equals(user.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能转账给自己");
            }
            receiverAmountsByUserId.put(receiverId, amount);
            totalOut = Math.addExact(totalOut, amount);
        }

        List<Long> activeMemberIds = cardRoomMapper.selectActiveMemberIds(roomId);
        if (!new HashSet<>(activeMemberIds).containsAll(seenUserIds)) {
            throw new BusinessException(ErrorCode.ROUND_MEMBER_MISSING, "收款人必须是在房成员");
        }

        Map<Long, Integer> scoresByUserId = new LinkedHashMap<>();
        scoresByUserId.put(user.getId(), -totalOut);
        scoresByUserId.putAll(receiverAmountsByUserId);
        int sum = scoresByUserId.values().stream().mapToInt(Integer::intValue).sum();
        if (sum != 0) throw new BusinessException(ErrorCode.ROUND_SUM_NOT_ZERO);
        return scoresByUserId;
    }

    private List<Long> validateFundParticipants(Long roomId, Long creatorId, List<Long> participantIds) {
        Set<Long> participantSet = new LinkedHashSet<>(participantIds);
        if (participantSet.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请至少选择一位平摊成员");
        }
        if (participantSet.contains(creatorId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "发起人不用选进平摊成员");
        }
        if (participantSet.size() != participantIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "平摊成员不能重复");
        }

        List<Long> activeMemberIds = cardRoomMapper.selectActiveMemberIds(roomId);
        if (!new HashSet<>(activeMemberIds).containsAll(participantSet)) {
            throw new BusinessException(ErrorCode.ROUND_MEMBER_MISSING, "平摊成员包含非房间成员");
        }
        return new ArrayList<>(participantSet);
    }

    private void insertRound(Long roomId, Long creatorId, Map<Long, Integer> scoresByUserId) {
        int nextRoundNo = cardRoundMapper.selectMaxRoundNo(roomId) + 1;
        CardRound round = new CardRound();
        round.setRoomId(roomId);
        round.setRoundNo(nextRoundNo);
        round.setCreatorId(creatorId);
        round.setSettled(CardConstant.ROUND_UNSETTLED);
        cardRoundMapper.insert(round);

        List<CardRoundScore> scores = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : scoresByUserId.entrySet()) {
            CardRoundScore score = new CardRoundScore();
            score.setRoundId(round.getId());
            score.setUserId(entry.getKey());
            score.setScore(entry.getValue());
            scores.add(score);
        }
        cardRoundScoreMapper.insertBatch(scores);

        for (CardRoundScore score : scores) {
            CardRoomMember member = cardRoomAccessGuard.requireActiveMember(roomId, score.getUserId());
            cardRoomMemberMapper.updateScoreIncrement(member.getId(), score.getScore());
        }
    }

    private void insertFund(Long roomId, Long creatorId, int amountFen, List<Long> participantIds) {
        CardFundRecord fund = new CardFundRecord();
        fund.setRoomId(roomId);
        fund.setType(CardConstant.FUND_TYPE_ADD);
        fund.setAmount(amountFen);
        fund.setCreatorId(creatorId);
        cardFundRecordMapper.insert(fund);

        List<CardFundParticipant> participants = new ArrayList<>();
        for (Long participantId : participantIds) {
            CardFundParticipant participant = new CardFundParticipant();
            participant.setFundId(fund.getId());
            participant.setUserId(participantId);
            participants.add(participant);
        }
        cardFundParticipantMapper.insertBatch(participants);
    }
}
