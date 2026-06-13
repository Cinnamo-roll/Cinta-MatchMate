package com.cinoo.matchmateserver.card.service.support;

import com.cinoo.matchmateserver.card.model.entity.CardFundParticipant;
import com.cinoo.matchmateserver.card.model.entity.CardFundRecord;
import com.cinoo.matchmateserver.card.model.entity.CardRoundScore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CardLedgerParticipantUtils {

    private CardLedgerParticipantUtils() {
    }

    public static Set<Long> roundScoreUserIds(List<CardRoundScore> scores) {
        return scores.stream()
                .map(CardRoundScore::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<Long> fundParticipantUserIds(
            CardFundRecord fund,
            List<CardFundParticipant> participants) {
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(fund.getCreatorId());
        participants.forEach(participant -> userIds.add(participant.getUserId()));
        return userIds;
    }
}
