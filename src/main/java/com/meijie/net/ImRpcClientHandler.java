package com.meijie.net;

import com.meijie.ImClient;
import com.meijie.proto.ImRequestProtos;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ImRpcClientHandler extends SimpleChannelInboundHandler<ImRequestProtos.ImResponse> {

    private ImClient imClient;

    public ImRpcClientHandler(ImClient imClient) {
        this.imClient = imClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ImRequestProtos.ImResponse msg) throws Exception {
        imClient.addMessage(msg);
    }
}
