package com.cinoo.matchmateserver.card.mapper;

import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.model.entity.*;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.user.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 打牌记账 Mapper 集成测试。
 * 验证实体映射、唯一约束、索引和关键 SQL。
 */
@SpringBootTest(properties = {
        "matchmate.cache.enabled=false",
        "matchmate.cache.warmup.enabled=false",
        "spring.cache.type=simple",
        "spring.session.store-type=none",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
        "DB_URL=jdbc:mysql://localhost:3306/matchmate",
        "DB_USERNAME=root",
        "DB_PASSWORD=1234"
})
@Transactional
class CardLedgerMapperTest {

    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    private CardRoomMapper cardRoomMapper;

    @Autowired
    private CardRoomMemberMapper cardRoomMemberMapper;

    @Autowired
    private CardRoundMapper cardRoundMapper;

    @Autowired
    private CardRoundScoreMapper cardRoundScoreMapper;

    @Autowired
    private CardFundRecordMapper cardFundRecordMapper;

    @Autowired
    private CardFundParticipantMapper cardFundParticipantMapper;

    @Autowired
    private UserMapper userMapper;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        testUser1 = createUser("test1", "account1");
        testUser2 = createUser("test2", "account2");
        testUser3 = createUser("test3", "account3");
    }

    // ── 房间创建 ──

    @Test
    void createRoom_shouldPersist() {
        CardRoom room = createRoom("123456", testUser1.getId());

        assertNotNull(room.getId());
        assertEquals("123456", room.getRoomCode());
        assertEquals(testUser1.getId(), room.getOwnerId());
        assertEquals(CardConstant.ROOM_STATUS_ACTIVE, room.getStatus());
    }

    @Test
    void createRoom_duplicateCode_shouldFail() {
        createRoom("888888", testUser1.getId());

        assertThrows(DuplicateKeyException.class, () ->
                createRoom("888888", testUser2.getId()));
    }

    // ── 成员加入 ──

    @Test
    void joinRoom_shouldPersist() {
        CardRoom room = createRoom("111111", testUser1.getId());
        CardRoomMember member = joinRoom(room.getId(), testUser1.getId());

        assertNotNull(member.getId());
        assertEquals(room.getId(), member.getRoomId());
        assertEquals(testUser1.getId(), member.getUserId());
        assertEquals(CardConstant.MEMBER_STATUS_ACTIVE, member.getStatus());
    }

    @Test
    void joinRoom_duplicateUserInSameRoom_shouldFail() {
        CardRoom room = createRoom("222222", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());

        assertThrows(DuplicateKeyException.class, () ->
                joinRoom(room.getId(), testUser1.getId()));
    }

    @Test
    void selectActiveRoomByUserId_shouldReturnCorrectRoom() {
        CardRoom room = createRoom("333333", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());

        CardRoom activeRoom = cardRoomMapper.selectActiveRoomByUserId(testUser1.getId());
        assertNotNull(activeRoom);
        assertEquals(room.getRoomCode(), activeRoom.getRoomCode());
    }

    @Test
    void selectActiveRoomByUserId_nonMember_shouldReturnNull() {
        createRoom("444444", testUser1.getId());

        CardRoom activeRoom = cardRoomMapper.selectActiveRoomByUserId(testUser2.getId());
        assertNull(activeRoom);
    }

    @Test
    void selectHistoryByUserId_shouldReturnCurrentUserRooms() {
        CardRoom room = createRoom("444445", testUser1.getId());
        CardRoomMember member = joinRoom(room.getId(), testUser1.getId());
        cardRoomMemberMapper.updateScoreIncrement(member.getId(), 12);

        var history = cardRoomMapper.selectHistoryByUserId(testUser1.getId(), 10);

        assertEquals(1, history.size());
        assertEquals(room.getId(), history.get(0).getRoomId());
        assertEquals(12, history.get(0).getScore());
        assertEquals(1, history.get(0).getMemberCount());
    }

    @Test
    void selectCardRanking_shouldOnlyIncludeSharedPlayers() {
        CardRoom room = createRoom("444446", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());
        joinRoom(room.getId(), testUser2.getId());
        userMapper.addStats(testUser2.getId(), 20, 1, 0);
        userMapper.addStats(testUser3.getId(), 100, 1, 0);

        List<User> ranking = userMapper.selectCardRanking(testUser1.getId(), 10);

        assertEquals(2, ranking.size());
        assertEquals(testUser2.getId(), ranking.get(0).getId());
        assertTrue(ranking.stream().noneMatch(user -> user.getId().equals(testUser3.getId())));
    }

    @Test
    void selectExpiredEndedRoomIds_shouldKeepLatestSixForEveryMember() {
        List<CardRoom> rooms = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            CardRoom room = createRoom(String.valueOf(610000 + i), testUser1.getId());
            joinRoom(room.getId(), testUser1.getId());
            room.setStatus(CardConstant.ROOM_STATUS_ENDED);
            cardRoomMapper.updateById(room);
            rooms.add(room);
        }

        List<Long> expiredIds = cardRoomMapper.selectExpiredEndedRoomIds(6);

        assertEquals(List.of(rooms.get(0).getId()), expiredIds);
    }

    // ── 牌局 + 分数 ──

    @Test
    void createRound_shouldPersistWithScores() {
        CardRoom room = createRoom("555555", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());
        joinRoom(room.getId(), testUser2.getId());

        CardRound round = createRound(room.getId(), 1, testUser1.getId());

        assertNotNull(round.getId());
        assertEquals(room.getId(), round.getRoomId());
        assertEquals(1, round.getRoundNo());

        // 分数
        List<CardRoundScore> scores = new ArrayList<>();
        CardRoundScore s1 = new CardRoundScore();
        s1.setRoundId(round.getId());
        s1.setUserId(testUser1.getId());
        s1.setScore(-3);
        scores.add(s1);

        CardRoundScore s2 = new CardRoundScore();
        s2.setRoundId(round.getId());
        s2.setUserId(testUser2.getId());
        s2.setScore(3);
        scores.add(s2);

        int inserted = cardRoundScoreMapper.insertBatch(scores);
        assertEquals(2, inserted);

        List<CardRoundScore> loaded = cardRoundScoreMapper.selectByRoundId(round.getId());
        assertEquals(2, loaded.size());
        assertEquals(0, loaded.stream().mapToInt(CardRoundScore::getScore).sum());
    }

    @Test
    void createFund_shouldUseSnakeCaseTableMappings() {
        CardRoom room = createRoom("620000", testUser1.getId());
        CardFundRecord fund = new CardFundRecord();
        fund.setRoomId(room.getId());
        fund.setType(CardConstant.FUND_TYPE_ADD);
        fund.setAmount(100);
        fund.setCreatorId(testUser1.getId());
        cardFundRecordMapper.insert(fund);

        CardFundParticipant participant = new CardFundParticipant();
        participant.setFundId(fund.getId());
        participant.setUserId(testUser2.getId());
        cardFundParticipantMapper.insertBatch(List.of(participant));

        List<CardFundRecord> funds = cardFundRecordMapper.selectByRoomId(room.getId(), 10);
        List<CardFundParticipant> participants =
                cardFundParticipantMapper.selectByFundIds(List.of(fund.getId()));

        assertEquals(1, funds.size());
        assertEquals(room.getId(), funds.get(0).getRoomId());
        assertEquals(testUser1.getId(), funds.get(0).getCreatorId());
        assertEquals(1, participants.size());
        assertEquals(testUser2.getId(), participants.get(0).getUserId());
    }

    @Test
    void createRound_duplicateRoundNo_shouldFail() {
        CardRoom room = createRoom("666666", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());
        createRound(room.getId(), 1, testUser1.getId());

        assertThrows(DuplicateKeyException.class, () ->
                createRound(room.getId(), 1, testUser1.getId()));
    }

    @Test
    void createRoundScore_duplicateUserInRound_shouldFail() {
        CardRoom room = createRoom("777777", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());
        CardRound round = createRound(room.getId(), 1, testUser1.getId());

        List<CardRoundScore> scores = new ArrayList<>();
        CardRoundScore s = new CardRoundScore();
        s.setRoundId(round.getId());
        s.setUserId(testUser1.getId());
        s.setScore(0);
        scores.add(s);
        cardRoundScoreMapper.insertBatch(scores);

        // 同一用户在同一局插两次
        List<CardRoundScore> dupScores = new ArrayList<>();
        CardRoundScore dup = new CardRoundScore();
        dup.setRoundId(round.getId());
        dup.setUserId(testUser1.getId());
        dup.setScore(0);
        dupScores.add(dup);
        assertThrows(DuplicateKeyException.class, () ->
                cardRoundScoreMapper.insertBatch(dupScores));
    }

    // ── 成员积分更新 ──

    @Test
    void updateMemberScore_shouldIncrementAtomically() {
        CardRoom room = createRoom("000002", testUser1.getId());
        CardRoomMember member = joinRoom(room.getId(), testUser1.getId());

        cardRoomMemberMapper.updateScoreIncrement(member.getId(), 5);
        cardRoomMemberMapper.updateScoreIncrement(member.getId(), -3);

        CardRoomMember updated = cardRoomMemberMapper.selectById(member.getId());
        assertEquals(2, updated.getTotalScore());
    }

    // ── 用户统计 ──

    @Test
    void addUserStats_shouldAccumulateAtomically() {
        // 单次累加验证原子性
        userMapper.addStats(testUser1.getId(), 7, 2, 1);
        User u = userMapper.selectById(testUser1.getId());
        assertEquals(7, u.getTotalScore());
        assertEquals(2, u.getWins());
        assertEquals(1, u.getLosses());
        assertTrue(u.getWinRate().compareTo(new BigDecimal("0.6666")) >= 0
                && u.getWinRate().compareTo(new BigDecimal("0.6667")) <= 0,
                "Expected winRate ~0.6667 but got " + u.getWinRate());
    }

    // ── selectMaxRoundNo ──

    @Test
    void selectMaxRoundNo_empty_shouldReturnZero() {
        CardRoom room = createRoom("000003", testUser1.getId());
        Integer maxRoundNo = cardRoundMapper.selectMaxRoundNo(room.getId());
        assertEquals(0, maxRoundNo);
    }

    @Test
    void selectMaxRoundNo_withRounds_shouldReturnMax() {
        CardRoom room = createRoom("000004", testUser1.getId());
        joinRoom(room.getId(), testUser1.getId());
        createRound(room.getId(), 1, testUser1.getId());
        createRound(room.getId(), 2, testUser1.getId());
        createRound(room.getId(), 3, testUser1.getId());

        Integer maxRoundNo = cardRoundMapper.selectMaxRoundNo(room.getId());
        assertEquals(3, maxRoundNo);
    }

    // ── 成员 batchSettle ──

    @Test
    void batchSettle_shouldUpdateAllMembers() {
        CardRoom room = createRoom("000005", testUser1.getId());
        CardRoomMember m1 = joinRoom(room.getId(), testUser1.getId());
        CardRoomMember m2 = joinRoom(room.getId(), testUser2.getId());

        // 先更新积分
        cardRoomMemberMapper.updateScoreIncrement(m1.getId(), 10);
        cardRoomMemberMapper.updateScoreIncrement(m2.getId(), -5);

        // 读取最新积分
        m1 = cardRoomMemberMapper.selectById(m1.getId());
        m2 = cardRoomMemberMapper.selectById(m2.getId());

        m1.setStatus(CardConstant.MEMBER_STATUS_SETTLED);
        m1.setSettleScore(m1.getTotalScore());
        m1.setWins(1);
        m1.setLosses(0);

        m2.setStatus(CardConstant.MEMBER_STATUS_SETTLED);
        m2.setSettleScore(m2.getTotalScore());
        m2.setWins(0);
        m2.setLosses(1);

        cardRoomMemberMapper.batchSettle(List.of(m1, m2));

        CardRoomMember sm1 = cardRoomMemberMapper.selectById(m1.getId());
        assertEquals(CardConstant.MEMBER_STATUS_SETTLED, sm1.getStatus());
        assertEquals(Integer.valueOf(10), sm1.getSettleScore());
        assertEquals(1, sm1.getWins());

        CardRoomMember sm2 = cardRoomMemberMapper.selectById(m2.getId());
        assertEquals(CardConstant.MEMBER_STATUS_SETTLED, sm2.getStatus());
        assertEquals(Integer.valueOf(-5), sm2.getSettleScore());
        assertEquals(1, sm2.getLosses());
    }

    @Test
    void selectByRoomId_shouldIncludeLeftAndSettledMembers() {
        CardRoom room = createRoom("000006", testUser1.getId());
        CardRoomMember active = joinRoom(room.getId(), testUser1.getId());
        CardRoomMember left = joinRoom(room.getId(), testUser2.getId());
        CardRoomMember settled = joinRoom(room.getId(), testUser3.getId());
        left.setStatus(CardConstant.MEMBER_STATUS_LEFT);
        settled.setStatus(CardConstant.MEMBER_STATUS_SETTLED);
        cardRoomMemberMapper.updateById(left);
        cardRoomMemberMapper.updateById(settled);

        List<CardRoomMember> members = cardRoomMemberMapper.selectByRoomId(room.getId());

        assertEquals(3, members.size());
        assertTrue(members.stream().anyMatch(member -> member.getId().equals(active.getId())));
        assertTrue(members.stream().anyMatch(member -> member.getId().equals(left.getId())));
        assertTrue(members.stream().anyMatch(member -> member.getId().equals(settled.getId())));
    }

    // ── Helpers ──

    private User createUser(String username, String account) {
        User user = new User();
        user.setUsername(username);
        user.setUserAccount(account);
        user.setUserPassword("pw");
        user.setUserStatus(0);
        user.setUserRole(0);
        user.setTotalScore(0);
        user.setWins(0);
        user.setLosses(0);
        user.setWinRate(java.math.BigDecimal.ZERO);
        userMapper.insert(user);
        return user;
    }

    private CardRoom createRoom(String roomCode, Long ownerId) {
        CardRoom room = new CardRoom();
        room.setRoomCode(roomCode);
        room.setRoomPassword("1234");
        room.setOwnerId(ownerId);
        room.setStatus(CardConstant.ROOM_STATUS_ACTIVE);
        room.setMaxMembers(8);
        cardRoomMapper.insert(room);
        return room;
    }

    private CardRoomMember joinRoom(Long roomId, Long userId) {
        CardRoomMember member = new CardRoomMember();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setStatus(CardConstant.MEMBER_STATUS_ACTIVE);
        cardRoomMemberMapper.insert(member);
        return member;
    }

    private CardRound createRound(Long roomId, int roundNo, Long creatorId) {
        CardRound round = new CardRound();
        round.setRoomId(roomId);
        round.setRoundNo(roundNo);
        round.setSettled(CardConstant.ROUND_UNSETTLED);
        round.setCreatorId(creatorId);
        cardRoundMapper.insert(round);
        return round;
    }

}
