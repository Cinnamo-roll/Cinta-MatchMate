package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.model.request.AddExpenseRequest;
import com.cinoo.matchmateserver.model.request.AddFundRequest;
import com.cinoo.matchmateserver.model.request.AddTransferRequest;
import com.cinoo.matchmateserver.model.vo.CardRoomHistoryVO;
import com.cinoo.matchmateserver.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 打牌记账房间服务。
 */
public interface CardRoomService {

    /**
     * 创建房间。创建者自动成为房主和第一个成员。
     */
    CardRoomVO createRoom(HttpServletRequest request);

    /**
     * 通过房间号加入房间。
     */
    CardRoomVO joinRoom(String roomCode, HttpServletRequest request);

    /**
     * 获取房间详情。
     */
    CardRoomVO getRoomDetail(Long roomId, HttpServletRequest request);

    /**
     * 获取当前用户活跃的房间。
     */
    CardRoomVO getActiveRoom(HttpServletRequest request);

    /**
     * 查询当前用户最近参与的房间。
     */
    List<CardRoomHistoryVO> getHistory(int limit, HttpServletRequest request);

    /**
     * 查询当前用户与共同牌友的积分排名。
     */
    List<UserVO> getRanking(int limit, HttpServletRequest request);

    /**
     * 成员退出房间。
     */
    void leaveRoom(Long roomId, HttpServletRequest request);

    /**
     * 成员发起转账牌局。当前用户向其他成员转账，总分必须为0。
     */
    CardRoomVO addTransfer(Long roomId, AddTransferRequest req, HttpServletRequest request);

    /**
     * 成员发起平摊资金（加钱/扣钱）。不计入积分。
     */
    CardRoomVO addFund(Long roomId, AddFundRequest req, HttpServletRequest request);

    /**
     * 房主新增费用（茶/饭）。
     */
    CardRoomVO addExpense(Long roomId, AddExpenseRequest req, HttpServletRequest request);

    /**
     * 房主结束房间并结算。
     */
    CardRoomVO endRoom(Long roomId, HttpServletRequest request);
}
