package com.cinoo.matchmateserver.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Common business error codes.
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "ok", ""),
    PARAM_ERROR(40000, HttpStatus.BAD_REQUEST, "Parameter error", ""),
    NOT_LOGIN(40100, HttpStatus.UNAUTHORIZED, "Not logged in", ""),
    NO_AUTH(40300, HttpStatus.FORBIDDEN, "No permission", ""),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "Resource not found", ""),
    FILE_SIZE_EXCEEDED(41300, HttpStatus.PAYLOAD_TOO_LARGE,
            "File size exceeds limit", "头像大小不能超过 5MB"),
    FILE_TYPE_ERROR(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported file type", "仅支持 JPG、PNG、GIF、WebP 图片"),
    SYSTEM_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "Internal system error", ""),
    ROOM_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "Room not found", "房间不存在或已结束"),
    ROOM_FULL(40001, HttpStatus.BAD_REQUEST, "Room is full", "房间已满"),
    ROOM_NOT_OWNER(40301, HttpStatus.FORBIDDEN, "Not room owner", "仅房主可执行此操作"),
    ROOM_ALREADY_IN(40002, HttpStatus.BAD_REQUEST, "Already in a room", "你已在其他房间中"),
    ROOM_NOT_MEMBER(40003, HttpStatus.BAD_REQUEST, "Not a room member", "你不是该房间成员"),
    ROUND_SUM_NOT_ZERO(40004, HttpStatus.BAD_REQUEST, "Round score sum must be zero", "牌局积分之和必须为0"),
    ROUND_MEMBER_MISSING(40005, HttpStatus.BAD_REQUEST, "Round member missing", "牌局包含非房间成员"),
    ROOM_ALREADY_ENDED(40006, HttpStatus.BAD_REQUEST, "Room already ended", "房间已结束"),
    ROOM_ALREADY_SETTLED(40007, HttpStatus.BAD_REQUEST, "Already settled", "已结算，不能重复操作");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
    private final String description;

    ErrorCode(int code, HttpStatus httpStatus, String message, String description) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
        this.description = description;
    }
}
