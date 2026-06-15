package com.cinoo.matchmateserver.card.service.assembler;

import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.mapper.CardFundParticipantMapper;
import com.cinoo.matchmateserver.card.mapper.CardFundRecordMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoomMemberMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoundMapper;
import com.cinoo.matchmateserver.card.mapper.CardRoundScoreMapper;
import com.cinoo.matchmateserver.card.mapper.CardUndoApprovalMapper;
import com.cinoo.matchmateserver.card.mapper.CardUndoRequestMapper;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.card.model.entity.CardFundParticipant;
import com.cinoo.matchmateserver.card.model.entity.CardFundRecord;
import com.cinoo.matchmateserver.card.model.entity.CardRoom;
import com.cinoo.matchmateserver.card.model.entity.CardRoomMember;
import com.cinoo.matchmateserver.card.model.entity.CardRound;
import com.cinoo.matchmateserver.card.model.entity.CardRoundScore;
import com.cinoo.matchmateserver.card.model.entity.CardUndoApproval;
import com.cinoo.matchmateserver.card.model.entity.CardUndoRequest;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.card.model.vo.CardFundRecordVO;
import com.cinoo.matchmateserver.card.model.vo.CardRoomMemberVO;
import com.cinoo.matchmateserver.card.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.card.model.vo.CardRoundVO;
import com.cinoo.matchmateserver.card.model.vo.CardUndoStatusVO;
import com.cinoo.matchmateserver.card.service.support.CardLedgerParticipantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardRoomViewAssembler {

    private static final int RECENT_LIMIT = 20;

    private final CardRoomMemberMapper cardRoomMemberMapper;
    private final CardRoundMapper cardRoundMapper;
    private final CardRoundScoreMapper cardRoundScoreMapper;
    private final CardFundRecordMapper cardFundRecordMapper;
    private final CardFundParticipantMapper cardFundParticipantMapper;
    private final CardUndoRequestMapper cardUndoRequestMapper;
    private final CardUndoApprovalMapper cardUndoApprovalMapper;
    private final UserMapper userMapper;

    public CardRoomVO toRoomVO(CardRoom room, Long currentUserId) {
        List<CardRoomMember> members = cardRoomMemberMapper.selectByRoomId(room.getId());
        List<CardRound> rounds = cardRoundMapper.selectByRoomId(room.getId(), RECENT_LIMIT);

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

        List<CardUndoRequest> undoRequests;
        List<CardUndoApproval> undoApprovals;
        try {
            undoRequests = cardUndoRequestMapper.selectPendingByRoomId(room.getId());
            undoApprovals = undoRequests.isEmpty()
                    ? List.of()
                    : cardUndoApprovalMapper.selectByRequestIds(
                            undoRequests.stream().map(CardUndoRequest::getId).toList());
        } catch (Exception e) {
            log.warn("Undo tables not available for room {}: {}", room.getId(), e.getMessage());
            undoRequests = List.of();
            undoApprovals = List.of();
        }

        Map<Long, User> usersById = loadUsersById(room, members, scores, funds, fundParticipants, undoRequests, undoApprovals);
        Map<Long, List<CardRoundScore>> scoresByRoundId = scores.stream()
                .collect(Collectors.groupingBy(CardRoundScore::getRoundId));
        Map<Long, List<CardFundParticipant>> fundParticipantsByFundId = fundParticipants.stream()
                .collect(Collectors.groupingBy(CardFundParticipant::getFundId));
        Map<String, CardUndoRequest> undoByTarget = undoRequests.stream()
                .collect(Collectors.toMap(
                        undo -> undoTargetKey(undo.getTargetType(), undo.getTargetId()),
                        Function.identity(),
                        (a, b) -> a));
        Map<Long, Set<Long>> approvalUserIdsByRequestId = undoApprovals.stream()
                .collect(Collectors.groupingBy(
                        CardUndoApproval::getRequestId,
                        Collectors.mapping(CardUndoApproval::getUserId, Collectors.toSet())));

        CardRoomVO vo = new CardRoomVO();
        vo.setRoomId(room.getId());
        vo.setRoomCode(room.getRoomCode());
        vo.setRoomPassword(room.getRoomPassword());
        vo.setOwnerId(room.getOwnerId());
        vo.setStatus(room.getStatus());
        vo.setMaxMembers(room.getMaxMembers());
        vo.setSettleTime(room.getSettleTime());
        vo.setCreateTime(room.getCreateTime());

        User owner = usersById.get(room.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getUsername() : null);
        vo.setMembers(toMemberVOs(members, usersById));
        vo.setRecentRounds(rounds.stream()
                .map(round -> toRoundVO(
                        round,
                        scoresByRoundId.getOrDefault(round.getId(), List.of()),
                        usersById,
                        undoByTarget,
                        approvalUserIdsByRequestId,
                        currentUserId))
                .toList());
        vo.setRecentFunds(funds.stream()
                .map(fund -> toFundRecordVO(
                        fund,
                        fundParticipantsByFundId.getOrDefault(fund.getId(), List.of()),
                        usersById,
                        undoByTarget,
                        approvalUserIdsByRequestId,
                        currentUserId))
                .toList());
        vo.setFundBalance(calculateFundBalance(currentUserId, funds, fundParticipantsByFundId));
        return vo;
    }

    private Map<Long, User> loadUsersById(
            CardRoom room,
            List<CardRoomMember> members,
            List<CardRoundScore> scores,
            List<CardFundRecord> funds,
            List<CardFundParticipant> fundParticipants,
            List<CardUndoRequest> undoRequests,
            List<CardUndoApproval> undoApprovals) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(room.getOwnerId());
        members.forEach(member -> userIds.add(member.getUserId()));
        scores.forEach(score -> userIds.add(score.getUserId()));
        funds.forEach(fund -> userIds.add(fund.getCreatorId()));
        fundParticipants.forEach(participant -> userIds.add(participant.getUserId()));
        undoRequests.forEach(undo -> userIds.add(undo.getRequesterId()));
        undoApprovals.forEach(approval -> userIds.add(approval.getUserId()));
        return userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<CardRoomMemberVO> toMemberVOs(
            List<CardRoomMember> members,
            Map<Long, User> usersById) {
        List<CardRoomMemberVO> memberVOs = new ArrayList<>();
        for (CardRoomMember member : members) {
            User user = usersById.get(member.getUserId());
            CardRoomMemberVO vo = new CardRoomMemberVO();
            vo.setUserId(member.getUserId());
            vo.setUsername(user != null ? user.getUsername() : null);
            vo.setAvatarUrl(user != null ? user.getAvatarUrl() : null);
            vo.setTotalScore(member.getTotalScore());
            vo.setStatus(member.getStatus());
            vo.setWins(member.getWins());
            vo.setLosses(member.getLosses());
            vo.setJoinTime(member.getJoinTime());
            memberVOs.add(vo);
        }
        return memberVOs;
    }

    private CardRoundVO toRoundVO(
            CardRound round,
            List<CardRoundScore> scores,
            Map<Long, User> usersById,
            Map<String, CardUndoRequest> undoByTarget,
            Map<Long, Set<Long>> approvalUserIdsByRequestId,
            Long currentUserId) {
        CardRoundVO vo = new CardRoundVO();
        vo.setRoundId(round.getId());
        vo.setRoundNo(round.getRoundNo());
        vo.setCreatorId(round.getCreatorId());
        vo.setCreateTime(round.getCreateTime());

        List<CardRoundVO.ScoreEntry> entries = new ArrayList<>();
        for (CardRoundScore score : scores) {
            User user = usersById.get(score.getUserId());
            CardRoundVO.ScoreEntry entry = new CardRoundVO.ScoreEntry();
            entry.setUserId(score.getUserId());
            entry.setUsername(user != null ? user.getUsername() : null);
            entry.setScore(score.getScore());
            entries.add(entry);
        }
        vo.setScores(entries);
        vo.setUndoStatus(toUndoStatusVO(
                undoByTarget.get(undoTargetKey(CardConstant.UNDO_TARGET_ROUND, round.getId())),
                CardLedgerParticipantUtils.roundScoreUserIds(scores),
                approvalUserIdsByRequestId,
                usersById,
                currentUserId));
        return vo;
    }

    private CardFundRecordVO toFundRecordVO(
            CardFundRecord fund,
            List<CardFundParticipant> participants,
            Map<Long, User> usersById,
            Map<String, CardUndoRequest> undoByTarget,
            Map<Long, Set<Long>> approvalUserIdsByRequestId,
            Long currentUserId) {
        CardFundRecordVO vo = new CardFundRecordVO();
        vo.setFundId(fund.getId());
        vo.setType(fund.getType());
        vo.setAmount(fund.getAmount());
        vo.setCreatorId(fund.getCreatorId());
        vo.setCreateTime(fund.getCreateTime());

        User creator = usersById.get(fund.getCreatorId());
        vo.setCreatorName(creator != null ? creator.getUsername() : null);

        List<CardFundRecordVO.Participant> participantVOs = new ArrayList<>();
        for (CardFundParticipant participant : participants) {
            User user = usersById.get(participant.getUserId());
            CardFundRecordVO.Participant participantVO = new CardFundRecordVO.Participant();
            participantVO.setUserId(participant.getUserId());
            participantVO.setUsername(user != null ? user.getUsername() : null);
            participantVOs.add(participantVO);
        }
        vo.setParticipants(participantVOs);
        vo.setUndoStatus(toUndoStatusVO(
                undoByTarget.get(undoTargetKey(CardConstant.UNDO_TARGET_FUND, fund.getId())),
                CardLedgerParticipantUtils.fundParticipantUserIds(fund, participants),
                approvalUserIdsByRequestId,
                usersById,
                currentUserId));
        return vo;
    }

    private int calculateFundBalance(
            Long currentUserId,
            List<CardFundRecord> funds,
            Map<Long, List<CardFundParticipant>> participantsByFundId) {
        int balance = 0;
        for (CardFundRecord fund : funds) {
            List<CardFundParticipant> participants = participantsByFundId.getOrDefault(fund.getId(), List.of());
            int participantCount = participants.size();
            if (participantCount == 0) continue;

            int totalPeople = participantCount + 1;
            int sharePerPerson = fund.getAmount() / totalPeople;
            int remainder = fund.getAmount() % totalPeople;
            int creatorShare = sharePerPerson + (remainder > 0 ? 1 : 0);
            int receivableAmount = fund.getAmount() - creatorShare;

            if (fund.getCreatorId().equals(currentUserId)) {
                balance += fund.getType() == CardConstant.FUND_TYPE_ADD
                        ? receivableAmount
                        : -receivableAmount;
            }

            int index = 0;
            for (CardFundParticipant participant : participants) {
                int charge = sharePerPerson + (index + 1 < remainder ? 1 : 0);
                if (participant.getUserId().equals(currentUserId)) {
                    balance += fund.getType() == CardConstant.FUND_TYPE_ADD
                            ? -charge
                            : charge;
                }
                index++;
            }
        }
        return balance;
    }

    private CardUndoStatusVO toUndoStatusVO(
            CardUndoRequest undo,
            Set<Long> participantIds,
            Map<Long, Set<Long>> approvalUserIdsByRequestId,
            Map<Long, User> usersById,
            Long currentUserId) {
        if (undo == null) return null;
        Set<Long> approvedUserIds = approvalUserIdsByRequestId.getOrDefault(undo.getId(), Set.of());
        CardUndoStatusVO vo = new CardUndoStatusVO();
        vo.setRequestId(undo.getId());
        vo.setRequesterId(undo.getRequesterId());
        User requester = usersById.get(undo.getRequesterId());
        vo.setRequesterName(requester != null ? requester.getUsername() : null);
        vo.setApprovedCount(approvedUserIds.size());
        vo.setRequiredCount(participantIds.size());
        vo.setApprovedByMe(approvedUserIds.contains(currentUserId));
        vo.setCanApprove(participantIds.contains(currentUserId) && !approvedUserIds.contains(currentUserId));
        return vo;
    }

    private String undoTargetKey(Integer targetType, Long targetId) {
        return targetType + ":" + targetId;
    }
}
