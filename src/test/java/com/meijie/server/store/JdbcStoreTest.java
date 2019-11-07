package com.meijie.server.store;

import com.meijie.domain.Message;
import com.meijie.store.JdbcMessageStore;
import com.zaxxer.hikari.HikariConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

public class JdbcStoreTest {
    private JdbcMessageStore jdbcMessageStore;

    @Before
    public void initStore() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://192.168.212.141:3306/crazy_im");
        config.setUsername("platform");
        config.setPassword("platform2018");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "10");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "10");
        jdbcMessageStore = new JdbcMessageStore(config);
    }

    @Test
    public void storeMessage() {
        Message message = new Message();
        message.setUserId1(0);
        message.setUserId2(1);
        message.setOwner(0);
        message.setContent("test");
        for (int i = 0; i < 100; i++) {
            jdbcMessageStore.saveMessage(message);
        }
    }

    @Test
    public void testCalculateVersion() {
        for (int i = 0; i < 10; i++) {
            long version = jdbcMessageStore.calculateVersion(Pair.of((long)0, (long)1));
            System.out.println(version);
        }

    }
}
