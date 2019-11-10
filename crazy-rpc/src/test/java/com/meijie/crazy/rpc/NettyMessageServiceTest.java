package com.meijie.crazy.rpc;

import com.meijie.crazy.rpc.proto.PingProtocol;
import com.meijie.crazy.rpc.proto.RpcProtocol;
import com.meijie.crazy.rpc.service.PingServicePbImpl;
import io.netty.channel.Channel;
import org.junit.Before;
import org.junit.Test;

public class NettyMessageServiceTest {

    private NettyMessageService nettyMessageService;

    @Before
    public void initNettyMessageService() throws InterruptedException {
        nettyMessageService = new NettyMessageService();
        nettyMessageService.init();
        nettyMessageService.registryProtocol("com.meijie.crazy.rpc.service.PingServicePbImpl", 1,
                PingProtocol.PingService.newReflectiveBlockingService(new PingServicePbImpl()));
        nettyMessageService.start(Address.from(8089));
    }

    @Test
    public void testSendMessageService() throws InterruptedException {
        Channel channel =  nettyMessageService.bootstrapClient(Address.from(8089)).sync().channel();
        RpcProtocol.RpcRequestHeaderProto requestHeader = RpcProtocol.RpcRequestHeaderProto.newBuilder()
                .setClientProtocolVersion(1)
                .setDeclaringClassProtocolName("com.meijie.crazy.rpc.service.PingServicePbImpl")
                .setMethodName("sendPingRequest")
                .build();
        PingProtocol.PingProto pingRequest = PingProtocol.PingProto.newBuilder().setMessage("ping").build();
        NettyMessage nettyMessage = new NettyMessage(requestHeader, pingRequest);
        channel.writeAndFlush(nettyMessage);
    }
}
