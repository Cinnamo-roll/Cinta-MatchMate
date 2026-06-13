package com.cinoo.matchmateserver.constant;

/**
 * Constants for card ledger rooms.
 */
public final class CardConstant {

    private CardConstant() {
    }

    public static final int ROOM_STATUS_ACTIVE = 0;
    public static final int ROOM_STATUS_ENDED = 1;

    public static final int MEMBER_STATUS_ACTIVE = 0;
    public static final int MEMBER_STATUS_LEFT = 1;
    public static final int MEMBER_STATUS_SETTLED = 2;

    public static final int ROUND_UNSETTLED = 0;
    public static final int ROUND_SETTLED = 1;

    public static final int DEFAULT_MAX_MEMBERS = 8;
    public static final int ROOM_CODE_LENGTH = 6;
    public static final String ROOM_CODE_CHARS = "0123456789";
    public static final int HISTORY_RETENTION_COUNT = 6;

    public static final int FUND_TYPE_ADD = 1;
    /** Kept only so old type=2 fund records can still be displayed correctly. */
    public static final int FUND_TYPE_DEDUCT = 2;

    public static final int UNDO_TARGET_ROUND = 1;
    public static final int UNDO_TARGET_FUND = 2;
    public static final int UNDO_STATUS_PENDING = 0;
    public static final int UNDO_STATUS_DONE = 1;

    public static final String LOCK_ROUND = "card:round:";
    public static final String LOCK_FUND = "card:fund:";
    public static final String LOCK_END = "card:end:";
    public static final String LOCK_JOIN = "card:join:";
    public static final String LOCK_UNDO = "card:undo:";
}
