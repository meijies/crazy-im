package com.meijie.crazy.rpc;

import org.junit.Before;
import org.junit.Test;

public class NettyMessageServiceTest {

    private NettyMessageService nettyMessageService;

    @Before
    public void initNettyMessageService() throws InterruptedException {
        nettyMessageService = new NettyMessageService();
        nettyMessageService.init();
        nettyMessageService.start(Address.from(8089));
    }

    @Test
    public void testSendMessageService() {

    }
}
