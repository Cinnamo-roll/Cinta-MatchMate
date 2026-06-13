package com.cinoo.matchmateserver.controller;

import com.cinoo.matchmateserver.common.BaseResponse;
import com.cinoo.matchmateserver.common.ResultUtils;
import com.cinoo.matchmateserver.model.request.AddExpenseRequest;
import com.cinoo.matchmateserver.model.request.AddFundRequest;
import com.cinoo.matchmateserver.model.request.AddTransferRequest;
import com.cinoo.matchmateserver.model.request.JoinRoomRequest;
import com.cinoo.matchmateserver.model.vo.CardRoomHistoryVO;
import com.cinoo.matchmateserver.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.model.vo.UserVO;
import com.cinoo.matchmateserver.service.CardRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/card-room")
@Tag(name = "打牌记账本", description = "多人打牌记账房间接口")
public class CardRoomController {

    private final CardRoomService cardRoomService;

    public CardRoomController(CardRoomService cardRoomService) {
        this.cardRoomService = cardRoomService;
    }

    @Operation(summary = "创建房间", description = "创建新房间，创建者自动成为房主和成员")
    @PostMapping("/create")
    public BaseResponse<CardRoomVO> createRoom(HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.createRoom(request));
    }

    @Operation(summary = "加入房间", description = "通过6位数字房间号加入房间")
    @PostMapping("/join")
    public BaseResponse<CardRoomVO> joinRoom(
            @Valid @RequestBody JoinRoomRequest req,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.joinRoom(req.getRoomCode(), request));
    }

    @Operation(summary = "获取房间详情", description = "获取房间完整详情，含成员、最近牌局和费用")
    @GetMapping("/{roomId}")
    public BaseResponse<CardRoomVO> getRoomDetail(
            @PathVariable Long roomId,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.getRoomDetail(roomId, request));
    }

    @Operation(summary = "获取当前活跃房间", description = "获取当前用户所在的活跃房间，无则返回null")
    @GetMapping("/active-room")
    public BaseResponse<CardRoomVO> getActiveRoom(HttpServletRequest request) {
        CardRoomVO room = cardRoomService.getActiveRoom(request);
        return ResultUtils.success(room);
    }

    @Operation(summary = "查询房间记录", description = "查询当前用户最近参与的房间")
    @GetMapping("/history")
    public BaseResponse<List<CardRoomHistoryVO>> getHistory(
            @RequestParam(defaultValue = "6") int limit,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.getHistory(limit, request));
    }

    @Operation(summary = "查询牌友排名", description = "按累计积分查询当前用户和共同牌友")
    @GetMapping("/ranking")
    public BaseResponse<List<UserVO>> getRanking(
            @RequestParam(defaultValue = "5") int limit,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.getRanking(limit, request));
    }

    @Operation(summary = "退出房间", description = "成员退出房间。房主不能直接退出活跃房间")
    @PostMapping("/{roomId}/leave")
    public BaseResponse<Void> leaveRoom(
            @PathVariable Long roomId,
            HttpServletRequest request) {
        cardRoomService.leaveRoom(roomId, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "每局转账", description = "成员向其他成员转账，转出扣分、转入加分，总分必须为0")
    @PostMapping("/{roomId}/transfer")
    public BaseResponse<CardRoomVO> addTransfer(
            @PathVariable Long roomId,
            @Valid @RequestBody AddTransferRequest req,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.addTransfer(roomId, req, request));
    }

    @Operation(summary = "金额平摊", description = "成员发起平摊资金（加钱/扣钱），不计入排行榜积分")
    @PostMapping("/{roomId}/fund")
    public BaseResponse<CardRoomVO> addFund(
            @PathVariable Long roomId,
            @Valid @RequestBody AddFundRequest req,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.addFund(roomId, req, request));
    }

    @Operation(summary = "新增费用", description = "房主新增茶钱/饭钱记录，选择参与分摊的成员")
    @PostMapping("/{roomId}/expense")
    public BaseResponse<CardRoomVO> addExpense(
            @PathVariable Long roomId,
            @Valid @RequestBody AddExpenseRequest req,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.addExpense(roomId, req, request));
    }

    @Operation(summary = "结束房间", description = "房主结束房间，结算所有成员积分并更新用户统计")
    @PostMapping("/{roomId}/end")
    public BaseResponse<CardRoomVO> endRoom(
            @PathVariable Long roomId,
            HttpServletRequest request) {
        return ResultUtils.success(cardRoomService.endRoom(roomId, request));
    }
}
