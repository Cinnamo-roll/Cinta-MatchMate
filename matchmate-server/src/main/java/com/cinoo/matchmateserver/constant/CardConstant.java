package com.cinoo.matchmateserver.constant;

/**
 * 打牌记账房间模块常量。
 */
public final class CardConstant {

    private CardConstant() {
    }

    // ── 房间状态 ──
    /** 进行中 */
    public static final int ROOM_STATUS_ACTIVE = 0;
    /** 已结束 */
    public static final int ROOM_STATUS_ENDED = 1;

    // ── 成员状态 ──
    /** 在房间 */
    public static final int MEMBER_STATUS_ACTIVE = 0;
    /** 已退出 */
    public static final int MEMBER_STATUS_LEFT = 1;
    /** 已结算 */
    public static final int MEMBER_STATUS_SETTLED = 2;

    // ── 牌局结算状态 ──
    /** 未结算 */
    public static final int ROUND_UNSETTLED = 0;
    /** 已结算 */
    public static final int ROUND_SETTLED = 1;

    // ── 费用类型 ──
    /** 茶钱 */
    public static final int EXPENSE_TYPE_TEA = 1;
    /** 饭钱 */
    public static final int EXPENSE_TYPE_MEAL = 2;

    // ── 房间配置 ──
    /** 默认最大人数 */
    public static final int DEFAULT_MAX_MEMBERS = 8;
    /** 房间号长度 */
    public static final int ROOM_CODE_LENGTH = 6;
    /** 房间号字符集 */
    public static final String ROOM_CODE_CHARS = "0123456789";

    // ── 平摊资金类型 ──
    /** 加钱 */
    public static final int FUND_TYPE_ADD = 1;
    /** 扣钱 */
    public static final int FUND_TYPE_DEDUCT = 2;

    // ── Redis 锁 Key ──
    public static final String LOCK_ROUND = "card:round:";
    public static final String LOCK_EXPENSE = "card:expense:";
    public static final String LOCK_FUND = "card:fund:";
    public static final String LOCK_END = "card:end:";
    public static final String LOCK_JOIN = "card:join:";
}
