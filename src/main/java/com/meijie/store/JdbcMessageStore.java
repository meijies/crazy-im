package com.meijie.store;

import com.google.common.base.Preconditions;
import com.meijie.domain.Message;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 应该替换为列式存储
 */
public class JdbcMessageStore {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HikariDataSource dataSource;
    private Map<Pair<Long, Long>, Long> savedVersionMap = new HashMap<>();
    private Map<Pair<Long, Long>, Long> sessionVersionMap = new ConcurrentHashMap<>();

    private Function<Pair<Long, Long>, Long> saveVersionMapComputeFunction = sessionPair ->
            getVersion(sessionPair.getKey(), sessionPair.getValue());

    public JdbcMessageStore(HikariConfig config) {
        dataSource = new HikariDataSource(config);
    }

    public void saveMessage(Message message) {

        // 检查消息不能为空
        Preconditions.checkArgument(StringUtils.isNotBlank(message.getContent()),
                "message content can't be blank");

        // 如果userId1 永远不大于 userId2, 那么所有消息能够按照version进行排序，在列存中能够顺序读写
        if (message.getUserId1() > message.getUserId2()) {
            long tempUserId = message.getUserId1();
            message.setUserId1(message.getUserId2());
            message.setUserId2(tempUserId);
        }

        // 查看数据库中保存的version
        Pair<Long, Long> sessionPair = Pair.of(message.getUserId1(), message.getUserId2());
        message.setVersion(calculateVersion(sessionPair));
        saveMessageToMysql(message);

    }

    private void saveMessageToMysql(Message message) {
        // 保存到数据库
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "insert into im_message(user_id1, user_id2, version, content, owner) values (?, ?, ?, ?, ?)")) {
            pstmt.setLong(1, message.getUserId1());
            pstmt.setLong(2, message.getUserId2());
            pstmt.setLong(3, message.getVersion());
            pstmt.setString(4, message.getContent());
            pstmt.setLong(5, message.getOwner());
            pstmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO 精简逻辑
    public synchronized long calculateVersion(Pair<Long, Long> sessionPair) {
        long version = sessionVersionMap.computeIfAbsent(sessionPair, pair ->
                savedVersionMap.computeIfAbsent(pair, saveVersionMapComputeFunction));
        final long lastVersion = version + 1;
        if (version >= savedVersionMap.get(sessionPair)) {
            updateVersion(sessionPair.getKey(), sessionPair.getValue(), version);
            savedVersionMap.put(sessionPair, version + 1000);
        }
        sessionVersionMap.put(sessionPair, lastVersion);
        return lastVersion;
    }


    public List<Message> getMessageList(long userId1, long userId2, long version) {
        if (userId1 >= userId2) {
            long tempUserId = userId1;
            userId1 = userId2;
            userId2 = tempUserId;
        }

        if (version == sessionVersionMap.get(Pair.of(userId1, userId2))) {
            return null;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "select user_id1, user_id2, version, content, owner from im_message " +
                             "where user_id1 = ? and user_id2 = ? and version > ? and content != '' order by version")) {
            pstmt.setLong(1, userId1);
            pstmt.setLong(2, userId2);
            pstmt.setLong(3, version);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Message> messageList = new ArrayList<>();
                while (rs.next()) {
                    Message message = new Message();
                    message.setUserId1(rs.getLong("user_id1"));
                    message.setUserId2(rs.getLong("user_id2"));
                    message.setVersion(rs.getLong("version"));
                    message.setContent(rs.getString("content"));
                    message.setOwner(rs.getLong("owner"));
                    messageList.add(message);
                }
                return messageList;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long getVersion(long userId1, long userId2) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "select version from im_message " +
                             "where user_id1 = ? and user_id2 = ? and content = '' ")) {
            pstmt.setLong(1, userId1);
            pstmt.setLong(2, userId2);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getLong(1);
                }
            }

            // 不存在记录
            Message message = new Message();
            message.setUserId1(userId1);
            message.setUserId2(userId2);
            message.setContent("");
            message.setVersion(0);
            saveMessageToMysql(message);
            return 0;
        } catch (SQLException e) {
            log.info("get saved version for " + userId1 + "," + userId2 + " error", e);
            throw new RuntimeException(e);
        }
    }

    public void updateVersion(long userId1, long userId2, long version) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "update im_message set version = version + 1000 where " +
                             "user_id1 = ? and user_id2 = ? and content = '' and version = ?")) {
            pstmt.setLong(1, userId1);
            pstmt.setLong(2, userId2);
            pstmt.setLong(3, version);
            Preconditions.checkArgument(pstmt.executeUpdate() == 1,
                    userId1 + "," + userId2 + "," + version + " is not the latest version at database");
        } catch (SQLException e) {
            log.error("update version failure", e);
            throw new RuntimeException(e);
        }
    }

}
