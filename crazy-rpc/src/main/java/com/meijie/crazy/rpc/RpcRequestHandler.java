package com.meijie.crazy.rpc;


import com.google.protobuf.BlockingService;
import static com.google.protobuf.Descriptors.MethodDescriptor;

import com.google.protobuf.Message;
import com.meijie.crazy.rpc.exception.RpcNoSuchMethodException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.meijie.crazy.rpc.ProtoProtocolRegister.ProtoNameVer;
import static com.meijie.crazy.rpc.proto.RpcProtocol.RpcRequestHeaderProto;

/**
 * Rpc Request Handler
 *
 * @author meijie
 */
public class RpcRequestHandler extends SimpleChannelInboundHandler<NettyMessage> {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyMessage msg) throws Exception {
        RpcRequestHeaderProto requestHeader = msg.getRequestHeader();
        ProtoNameVer nameVer = new ProtoNameVer(requestHeader.getDeclaringClassProtocolName(),
                requestHeader.getClientProtocolVersion());
        BlockingService service = ProtoProtocolRegister.getProtocolService(nameVer);

        MethodDescriptor methodDescriptor = service.getDescriptorForType()
                .findMethodByName(requestHeader.getMethodName());
        if (methodDescriptor == null) {
            String errorMsg = "Unknown method " + requestHeader.getMethodName() + " called on "
                    + requestHeader.getDeclaringClassProtocolName() + " protocol.";
            LOG.warn(errorMsg);
            throw new RpcNoSuchMethodException(errorMsg);
        }

        Message prototype = service.getRequestPrototype(methodDescriptor);
        Message param = msg.getPayload(prototype);

        Message result = service.callBlockingMethod(methodDescriptor, null, param);
    }
}
