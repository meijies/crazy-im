package com.meijie;

import com.meijie.common.Address;
import com.meijie.net.ImRpcServerHandler;
import com.meijie.service.NettyMessageService;

/**
 * @author meijie
 */
public class ImServer {
    public static void main(String[] args) throws InterruptedException {
        NettyMessageService nettyMessageService = new NettyMessageService();
        nettyMessageService.init();
        nettyMessageService.setImRpcServerHandler(new ImRpcServerHandler());
        nettyMessageService.registryServerProtocol(ProtocolEnum.CLIENT_PROTOCOL);
        nettyMessageService.start(Address.from("localhost", 8089));
    }
}
