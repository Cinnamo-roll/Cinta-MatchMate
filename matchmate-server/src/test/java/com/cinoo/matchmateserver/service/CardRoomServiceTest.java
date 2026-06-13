package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.constant.CardConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.*;
import com.cinoo.matchmateserver.model.domain.*;
import com.cinoo.matchmateserver.model.request.AddExpenseRequest;
import com.cinoo.matchmateserver.model.request.AddFundRequest;
import com.cinoo.matchmateserver.model.request.AddTransferRequest;
import com.cinoo.matchmateserver.model.vo.CardRoomVO;
import com.cinoo.matchmateserver.model.vo.UserVO;
import com.cinoo.matchmateserver.service.impl.CardRoomServiceImpl;
import com.cinoo.matchmateserver.websocket.CardWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CardRoomServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long OTHER2_ID = 3L;

    @Mock private CardRoomMapper cardRoomMapper;
    @Mock private CardRoomMemberMapper cardRoomMemberMapper;
    @Mock private CardRoundMapper cardRoundMapper;
    @Mock private CardRoundScoreMapper cardRoundScoreMapper;
    @Mock private CardExpenseMapper cardExpenseMapper;
    @Mock private CardExpenseParticipantMapper cardExpenseParticipantMapper;
    @Mock private CardFundRecordMapper cardFundRecordMapper;
    @Mock private CardFundParticipantMapper cardFundParticipantMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserService userService;
    @Mock private DataRetentionService dataRetentionService;
    @Mock private CacheInvalidationService cacheInvalidationService;
    @Mock private CardWebSocketHandler cardWebSocketHandler;
    @Mock private org.redisson.api.RedissonClient redissonClient;
    @Mock private org.redisson.api.RLock rLock;

    private CardRoomService cardRoomService;
    private MockHttpServletRequest ownerRequest;
    private MockHttpServletRequest otherRequest;

    @BeforeEach
    void setUp() throws InterruptedException {
        cardRoomService = new CardRoomServiceImpl(
                cardRoomMapper, cardRoomMemberMapper, cardRoundMapper,
                cardRoundScoreMapper, cardExpenseMapper, cardExpenseParticipantMapper,
                cardFundRecordMapper, cardFundParticipantMapper,
                userMapper, userService, dataRetentionService, cacheInvalidationService,
                redissonClient, cardWebSocketHandler
        );

        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("房主");
        User other = new User();
        other.setId(OTHER_ID);
        other.setUsername("成员1");

        ownerRequest = new MockHttpServletRequest();
        ownerRequest.getSession(true).setAttribute("userLoginState", OWNER_ID);
        otherRequest = new MockHttpServletRequest();
        otherRequest.getSession(true).setAttribute("userLoginState", OTHER_ID);

        // loginUser uses userMapper.selectById——return correct user
        lenient().when(userMapper.selectById(OWNER_ID)).thenReturn(owner);
        lenient().when(userMapper.selectById(OTHER_ID)).thenReturn(other);
        lenient().when(userService.getLoginUser(ownerRequest)).thenReturn(owner);
        lenient().when(userService.getLoginUser(otherRequest)).thenReturn(other);
        lenient().when(userService.toUserVO(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            return vo;
        });

        // Redisson lock mock
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), any())).thenReturn(true);
        lenient().doNothing().when(rLock).unlock();
    }

    // ── 创建房间 ──

    @Test
    void createRoom_shouldSucceed() {
        when(cardRoomMapper.selectCount(any())).thenReturn(0L);
        when(cardRoomMapper.insert(any(CardRoom.class))).thenAnswer(inv -> {
            CardRoom r = inv.getArgument(0);
            r.setId(10L);
            return 1;
        });
        when(cardRoomMemberMapper.insert(any(CardRoomMember.class))).thenReturn(1);

        CardRoomVO vo = cardRoomService.createRoom(ownerRequest);

        assertNotNull(vo);
        assertEquals(10L, vo.getRoomId());
        assertEquals(6, vo.getRoomCode().length());
    }

    @Test
    void createRoom_shouldRetryOnDuplicateCode() {
        when(cardRoomMapper.selectCount(any())).thenReturn(1L, 0L);
        when(cardRoomMapper.insert(any(CardRoom.class))).thenAnswer(inv -> {
            CardRoom r = inv.getArgument(0);
            r.setId(11L);
            return 1;
        });
        when(cardRoomMemberMapper.insert(any(CardRoomMember.class))).thenReturn(1);

        CardRoomVO vo = cardRoomService.createRoom(ownerRequest);
        assertNotNull(vo);
    }

    // ── 加入房间 ──

    @Test
    void joinRoom_notFound_shouldThrow() {
        when(cardRoomMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.joinRoom("999999", otherRequest));
        assertEquals(ErrorCode.ROOM_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void joinRoom_alreadyEnded_shouldThrow() {
        CardRoom endedRoom = new CardRoom();
        endedRoom.setId(1L);
        endedRoom.setStatus(CardConstant.ROOM_STATUS_ENDED);
        when(cardRoomMapper.selectOne(any())).thenReturn(endedRoom);

        assertThrows(BusinessException.class,
                () -> cardRoomService.joinRoom("999999", otherRequest));
    }

    @Test
    void joinRoom_full_shouldThrow() {
        CardRoom room = new CardRoom();
        room.setId(1L);
        room.setStatus(CardConstant.ROOM_STATUS_ACTIVE);
        room.setMaxMembers(2);
        when(cardRoomMapper.selectOne(any())).thenReturn(room);
        when(cardRoomMapper.selectById(room.getId())).thenReturn(room);
        when(cardRoomMemberMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.joinRoom("123456", otherRequest));
        assertEquals(ErrorCode.ROOM_FULL.getCode(), ex.getCode());
    }

    @Test
    void joinRoom_alreadyInAnother_shouldThrow() {
        CardRoom room = new CardRoom();
        room.setId(1L);
        room.setStatus(CardConstant.ROOM_STATUS_ACTIVE);
        room.setMaxMembers(8);
        when(cardRoomMapper.selectOne(any())).thenReturn(room);
        when(cardRoomMapper.selectById(room.getId())).thenReturn(room);
        when(cardRoomMemberMapper.selectCount(any())).thenReturn(1L);

        // 用户已在其他房间
        CardRoom otherRoom = new CardRoom();
        otherRoom.setId(99L);
        when(cardRoomMapper.selectActiveRoomByUserId(OTHER_ID)).thenReturn(otherRoom);

        assertThrows(BusinessException.class,
                () -> cardRoomService.joinRoom("123456", otherRequest));
    }

    // ── 退出房间 ──

    @Test
    void leaveRoom_ownerOfActive_shouldThrow() {
        CardRoom room = mockActiveRoom();
        room.setOwnerId(OWNER_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.leaveRoom(room.getId(), ownerRequest));
        assertEquals(ErrorCode.ROOM_NOT_OWNER.getCode(), ex.getCode());
    }

    @Test
    void leaveRoom_notInRoom_shouldThrow() {
        CardRoom room = mockActiveRoom();
        when(cardRoomMemberMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> cardRoomService.leaveRoom(room.getId(), otherRequest));
    }

    @Test
    void leaveRoom_shouldSettleUserStatsImmediately() {
        CardRoom room = mockActiveRoom();
        CardRoomMember member = makeMember(room.getId(), OTHER_ID, 7);
        member.setId(20L);
        when(cardRoomMemberMapper.selectOne(any())).thenReturn(member);

        cardRoomService.leaveRoom(room.getId(), otherRequest);

        assertEquals(CardConstant.MEMBER_STATUS_LEFT, member.getStatus());
        assertEquals(Integer.valueOf(7), member.getSettleScore());
        assertEquals(1, member.getWins());
        verify(userMapper).addStats(OTHER_ID, 7, 1, 0);
        verify(cacheInvalidationService).userChanged(OTHER_ID);
    }

    @Test
    void getRoomDetail_nonMember_shouldThrow() {
        CardRoom room = mockActiveRoom();
        when(cardRoomMemberMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.getRoomDetail(room.getId(), otherRequest));

        assertEquals(ErrorCode.ROOM_NOT_MEMBER.getCode(), ex.getCode());
    }

    @Test
    void getHistory_invalidLimit_shouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.getHistory(21, ownerRequest));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void getRanking_invalidLimit_shouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.getRanking(0, ownerRequest));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void addTransfer_oneYuan_shouldChangeOnePoint() {
        CardRoom room = mockActiveRoom();
        CardRoomMember owner = makeMember(room.getId(), OWNER_ID, 0);
        owner.setId(11L);
        CardRoomMember other = makeMember(room.getId(), OTHER_ID, 0);
        other.setId(12L);
        AddTransferRequest request = makeTransferRequest(OTHER_ID, "1");
        AtomicReference<CardRound> savedRound = new AtomicReference<>();
        AtomicReference<List<CardRoundScore>> savedScores = new AtomicReference<>(List.of());

        when(cardRoomMemberMapper.selectOne(any())).thenReturn(owner, owner, other);
        when(cardRoomMapper.selectActiveMemberIds(room.getId()))
                .thenReturn(List.of(OWNER_ID, OTHER_ID));
        when(cardRoundMapper.selectMaxRoundNo(room.getId())).thenReturn(0);
        when(cardRoundMapper.insert(any(CardRound.class))).thenAnswer(invocation -> {
            CardRound round = invocation.getArgument(0);
            round.setId(30L);
            savedRound.set(round);
            return 1;
        });
        when(cardRoundScoreMapper.insertBatch(anyList())).thenAnswer(invocation -> {
            savedScores.set(invocation.getArgument(0));
            return 2;
        });
        when(cardRoundMapper.selectByRoomId(eq(room.getId()), anyInt()))
                .thenAnswer(invocation -> List.of(savedRound.get()));
        when(cardRoundScoreMapper.selectByRoundIds(anyList()))
                .thenAnswer(invocation -> savedScores.get());
        configureRoomView(room, List.of(owner, other));

        CardRoomVO result = cardRoomService.addTransfer(room.getId(), request, ownerRequest);

        verify(cardRoomMemberMapper).updateScoreIncrement(11L, -1);
        verify(cardRoomMemberMapper).updateScoreIncrement(12L, 1);
        assertEquals(-1, result.getRecentRounds().get(0).getScores().stream()
                .filter(score -> score.getUserId().equals(OWNER_ID))
                .findFirst()
                .orElseThrow()
                .getScore());
    }

    @Test
    void addTransfer_decimalAmount_shouldBeRejected() {
        CardRoom room = mockActiveRoom();
        CardRoomMember owner = makeMember(room.getId(), OWNER_ID, 0);
        when(cardRoomMemberMapper.selectOne(any())).thenReturn(owner);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cardRoomService.addTransfer(
                        room.getId(),
                        makeTransferRequest(OTHER_ID, "1.5"),
                        ownerRequest));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(cardRoundMapper, never()).insert(any(CardRound.class));
    }

    @Test
    void addFund_oneYuan_shouldStoreOneHundredCentsAndKeepCreatorBalance() {
        CardRoom room = mockActiveRoom();
        CardRoomMember owner = makeMember(room.getId(), OWNER_ID, 0);
        CardRoomMember other = makeMember(room.getId(), OTHER_ID, 0);
        AtomicReference<CardFundRecord> savedFund = new AtomicReference<>();
        AtomicReference<List<CardFundParticipant>> savedParticipants =
                new AtomicReference<>(List.of());
        AddFundRequest request = new AddFundRequest();
        request.setType(CardConstant.FUND_TYPE_ADD);
        request.setAmount(BigDecimal.ONE);
        request.setParticipantIds(List.of(OTHER_ID));

        when(cardRoomMemberMapper.selectOne(any())).thenReturn(owner);
        when(cardRoomMapper.selectActiveMemberIds(room.getId()))
                .thenReturn(List.of(OWNER_ID, OTHER_ID));
        when(cardFundRecordMapper.insert(any(CardFundRecord.class))).thenAnswer(invocation -> {
            CardFundRecord fund = invocation.getArgument(0);
            fund.setId(40L);
            savedFund.set(fund);
            return 1;
        });
        when(cardFundParticipantMapper.insertBatch(anyList())).thenAnswer(invocation -> {
            savedParticipants.set(invocation.getArgument(0));
            return 1;
        });
        when(cardFundRecordMapper.selectByRoomId(eq(room.getId()), anyInt()))
                .thenAnswer(invocation -> List.of(savedFund.get()));
        when(cardFundParticipantMapper.selectByFundIds(anyList()))
                .thenAnswer(invocation -> savedParticipants.get());
        configureRoomView(room, List.of(owner, other));

        CardRoomVO result = cardRoomService.addFund(room.getId(), request, ownerRequest);

        assertEquals(100, savedFund.get().getAmount());
        assertEquals(100, result.getFundBalance());
        verify(cardWebSocketHandler).pushEvent(
                eq(room.getId()),
                eq(OWNER_ID),
                eq(CardWebSocketHandler.EVENT_FUND_CREATED),
                any());
    }

    // ── 新增费用 ──

    @Test
    void addExpense_notOwner_shouldThrow() {
        CardRoom room = mockActiveRoom();
        AddExpenseRequest req = makeExpenseRequest(1, 1000, List.of(OTHER_ID));

        assertThrows(BusinessException.class,
                () -> cardRoomService.addExpense(room.getId(), req, otherRequest));
    }

    @Test
    void addExpense_invalidParticipant_shouldThrow() {
        CardRoom room = mockActiveRoom();
        AddExpenseRequest req = makeExpenseRequest(1, 1000, List.of(999L)); // 非成员

        lenient().when(cardRoomMemberMapper.selectActiveByRoomId(room.getId())).thenReturn(
                List.of(makeMember(room.getId(), OWNER_ID, 0))
        );

        assertThrows(BusinessException.class,
                () -> cardRoomService.addExpense(room.getId(), req, ownerRequest));
    }

    @Test
    void addExpense_shouldKeepScoreSumZeroWhenAmountHasRemainder() {
        CardRoom room = mockActiveRoom();
        AddExpenseRequest req = makeExpenseRequest(
                CardConstant.EXPENSE_TYPE_TEA,
                100,
                List.of(OWNER_ID, OTHER_ID, OTHER2_ID)
        );
        CardRoomMember owner = makeMember(room.getId(), OWNER_ID, 0);
        owner.setId(11L);
        CardRoomMember other = makeMember(room.getId(), OTHER_ID, 0);
        other.setId(12L);
        CardRoomMember other2 = makeMember(room.getId(), OTHER2_ID, 0);
        other2.setId(13L);

        when(cardRoomMapper.selectActiveMemberIds(room.getId()))
                .thenReturn(List.of(OWNER_ID, OTHER_ID, OTHER2_ID));
        when(cardRoomMemberMapper.selectOne(any()))
                .thenReturn(owner, other, other2);
        when(cardExpenseMapper.insert(any(CardExpense.class))).thenAnswer(invocation -> {
            CardExpense expense = invocation.getArgument(0);
            expense.setId(30L);
            return 1;
        });
        when(cardExpenseMapper.selectByRoomId(eq(room.getId()), anyInt()))
                .thenAnswer(invocation -> {
                    CardExpense expense = new CardExpense();
                    expense.setId(30L);
                    expense.setRoomId(room.getId());
                    expense.setType(CardConstant.EXPENSE_TYPE_TEA);
                    expense.setAmount(100);
                    expense.setPayerId(OWNER_ID);
                    return List.of(expense);
                });

        cardRoomService.addExpense(room.getId(), req, ownerRequest);

        verify(cardRoomMemberMapper).updateScoreIncrement(11L, 66);
        verify(cardRoomMemberMapper).updateScoreIncrement(12L, -33);
        verify(cardRoomMemberMapper).updateScoreIncrement(13L, -33);
    }

    @Test
    void addExpense_duplicateParticipant_shouldThrow() {
        CardRoom room = mockActiveRoom();
        AddExpenseRequest req = makeExpenseRequest(
                CardConstant.EXPENSE_TYPE_TEA,
                100,
                List.of(OWNER_ID, OWNER_ID)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cardRoomService.addExpense(room.getId(), req, ownerRequest));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    // ── 结束房间 ──

    @Test
    void endRoom_notOwner_shouldThrow() {
        CardRoom room = mockActiveRoom();

        assertThrows(BusinessException.class,
                () -> cardRoomService.endRoom(room.getId(), otherRequest));
    }

    @Test
    void endRoom_alreadyEnded_shouldThrow() {
        CardRoom room = mockEndedRoom();

        assertThrows(BusinessException.class,
                () -> cardRoomService.endRoom(room.getId(), ownerRequest));
    }

    @Test
    void endRoom_shouldSettleAndUpdateStats() throws InterruptedException {
        CardRoom room = mockActiveRoom();
        room.setOwnerId(OWNER_ID);

        List<CardRoomMember> members = new ArrayList<>();
        CardRoomMember m1 = makeMember(room.getId(), OWNER_ID, -3);
        m1.setId(1L);
        CardRoomMember m2 = makeMember(room.getId(), OTHER_ID, 3);
        m2.setId(2L);
        members.add(m1);
        members.add(m2);
        when(cardRoomMemberMapper.selectActiveByRoomId(room.getId())).thenReturn(members);

        // No expenses
        when(cardExpenseMapper.selectByRoomId(room.getId(), 200)).thenReturn(List.of());

        // Mapper updates
        when(cardRoomMapper.updateById(any(CardRoom.class))).thenReturn(1);
        when(cardRoomMemberMapper.batchSettle(anyList())).thenReturn(1);
        when(userMapper.addStats(eq(OWNER_ID), eq(-3), eq(0), eq(1))).thenReturn(1);
        when(userMapper.addStats(eq(OTHER_ID), eq(3), eq(1), eq(0))).thenReturn(1);

        CardRoomVO vo = cardRoomService.endRoom(room.getId(), ownerRequest);

        assertNotNull(vo);
        assertEquals(CardConstant.ROOM_STATUS_ENDED, room.getStatus());

        // 验证 WS 推送
        verify(cardWebSocketHandler).pushEvent(eq(room.getId()), any(), eq(CardWebSocketHandler.EVENT_ROOM_CLOSED), any());
    }

    // ── Helpers ──

    private CardRoom mockActiveRoom() {
        CardRoom room = new CardRoom();
        room.setId(1L);
        room.setRoomCode("123456");
        room.setOwnerId(OWNER_ID);
        room.setStatus(CardConstant.ROOM_STATUS_ACTIVE);
        room.setMaxMembers(8);
        room.setTeaAmount(0);
        room.setMealAmount(0);
        when(cardRoomMapper.selectById(room.getId())).thenReturn(room);
        return room;
    }

    private CardRoom mockEndedRoom() {
        CardRoom room = new CardRoom();
        room.setId(1L);
        room.setRoomCode("123456");
        room.setOwnerId(OWNER_ID);
        room.setStatus(CardConstant.ROOM_STATUS_ENDED);
        when(cardRoomMapper.selectById(room.getId())).thenReturn(room);
        return room;
    }

    private AddExpenseRequest makeExpenseRequest(int type, int amount, List<Long> participantIds) {
        AddExpenseRequest req = new AddExpenseRequest();
        req.setType(type);
        req.setAmount(amount);
        req.setParticipantIds(participantIds);
        return req;
    }

    private AddTransferRequest makeTransferRequest(Long toUserId, String amount) {
        AddTransferRequest.TransferEntry transfer = new AddTransferRequest.TransferEntry();
        transfer.setToUserId(toUserId);
        transfer.setAmount(new BigDecimal(amount));
        AddTransferRequest request = new AddTransferRequest();
        request.setTransfers(List.of(transfer));
        return request;
    }

    private void configureRoomView(CardRoom room, List<CardRoomMember> members) {
        when(cardRoomMemberMapper.selectByRoomId(room.getId())).thenReturn(members);
        when(cardExpenseMapper.selectByRoomId(eq(room.getId()), anyInt())).thenReturn(List.of());
        when(userMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            List<User> users = new ArrayList<>();
            for (CardRoomMember member : members) {
                User user = new User();
                user.setId(member.getUserId());
                user.setUsername("user-" + member.getUserId());
                users.add(user);
            }
            return users;
        });
    }

    private CardRoomMember makeMember(Long roomId, Long userId, int totalScore) {
        CardRoomMember m = new CardRoomMember();
        m.setRoomId(roomId);
        m.setUserId(userId);
        m.setTotalScore(totalScore);
        m.setWins(0);
        m.setLosses(0);
        m.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
        return m;
    }
}
