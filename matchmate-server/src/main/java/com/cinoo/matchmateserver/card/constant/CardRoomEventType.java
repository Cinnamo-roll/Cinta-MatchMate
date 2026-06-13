package com.cinoo.matchmateserver.card.constant;

public final class CardRoomEventType {

    private CardRoomEventType() {
    }

    public static final String MEMBER_JOINED = "card_room_member_joined";
    public static final String MEMBER_LEFT = "card_room_member_left";
    public static final String ROUND_CREATED = "card_room_round_created";
    public static final String FUND_CREATED = "card_room_fund_created";
    public static final String ROOM_CLOSED = "card_room_closed";
}
