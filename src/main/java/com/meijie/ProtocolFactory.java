package com.meijie;

import com.google.protobuf.BlockingService;
import com.meijie.proto.ImClientProtocol.ImClientProtocolService;
import com.meijie.service.ImClientProtocolPBServiceImpl;
import com.zaxxer.hikari.HikariConfig;

/**
 * 用于管理Im中用到的各种协议
 */
public class ProtocolFactory {

    public static final BlockingService imClientProtocol() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://192.168.212.141:3306/crazy_im");
        config.setUsername("platform");
        config.setPassword("platform2018");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "10");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "10");
        return ImClientProtocolService.newReflectiveBlockingService(new ImClientProtocolPBServiceImpl(config));
    }
}
