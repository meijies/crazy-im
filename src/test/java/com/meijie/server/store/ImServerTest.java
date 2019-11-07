package com.meijie.server.store;

import com.meijie.ProtocolEnum;
import com.meijie.common.Address;
import com.meijie.service.NettyMessageService;
import org.junit.After;
import org.junit.Before;

public class ImServerTest {

    private static final NettyMessageService nettyMessageService = new NettyMessageService();

    private Thread serverThread = new Thread(() -> {
        try {
            nettyMessageService.init();
            nettyMessageService.registryServerProtocol(ProtocolEnum.CLIENT_PROTOCOL);
            nettyMessageService.start(Address.from("localhost", 8089));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    });

    @Before
    public void runServer() {
        serverThread.start();
    }

    @After
    public void releaseResource() {
        nettyMessageService.shutdownServer();
        serverThread.interrupt();
    }

}
