package com.meijie.net;

import com.google.protobuf.Any;
import com.google.protobuf.BlockingService;

import static com.meijie.proto.ImRequestProtos.*;

import static com.google.protobuf.Descriptors.*;

import com.google.protobuf.Message;
import com.google.protobuf.ServiceException;
import com.meijie.exception.RpcNoSuchMethodException;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Im Rpc 调用
 * @author meijie
 */
@ChannelHandler.Sharable
public class ImRpcServerHandler extends SimpleChannelInboundHandler<ImRequest> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final Map<ProtoNameVer, BlockingService> protoProtocolServiceMap = new HashMap();

    /**
     * 注册服务到 protoProtocolServiceMap
     *
     * @param declaringClassProtoName
     * @param version
     * @param service
     */
    public synchronized void registry(String declaringClassProtoName, int version, BlockingService service) {
        ProtoNameVer protoNameVer = new ProtoNameVer(declaringClassProtoName, version);
        protoProtocolServiceMap.putIfAbsent(protoNameVer, service);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ImRequest msg) throws Exception {
        RequestHeaderProto requestHeader = msg.getHeader();
        ProtoNameVer protoNameVer = new ProtoNameVer(
                requestHeader.getDeclaringClassProtocolName(),
                requestHeader.getClientProtocolVersion());
        BlockingService service = protoProtocolServiceMap.get(protoNameVer);
        MethodDescriptor methodDescriptor = service.getDescriptorForType()
                .findMethodByName(requestHeader.getMethodName());
        if (methodDescriptor == null) {
            String errorMsg = "Unknown method " + requestHeader.getMethodName() + " called on "
                    + requestHeader.getDeclaringClassProtocolName() + " protocol.";
            log.warn(errorMsg);
            throw new RpcNoSuchMethodException(errorMsg);
        }

        Message prototype = service.getRequestPrototype(methodDescriptor);
        Message param = msg.getPlayload().unpack(prototype.getClass());
        CompletableFuture.supplyAsync(() -> param)
                .thenApplyAsync(param0 -> {
                    try {
                        return service.callBlockingMethod(methodDescriptor, null, param0);
                    } catch (ServiceException e) {
                        throw new RuntimeException("failure save the message");
                    }
                })
                .thenApplyAsync(result -> ImResponse.newBuilder()
                        .setMethodName(requestHeader.getMethodName())
                        .setPlayload(Any.pack(result))
                        .build())
                .thenAcceptAsync(imResponse -> ctx.writeAndFlush(imResponse));
    }


    private class ProtoNameVer {
        final String protocol;
        final long version;

        ProtoNameVer(String protocol, long ver) {
            this.protocol = protocol;
            this.version = ver;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;
            if (this == o)
                return true;
            if (!(o instanceof ProtoNameVer))
                return false;
            ProtoNameVer pv = (ProtoNameVer) o;
            return ((pv.protocol.equals(this.protocol)) &&
                    (pv.version == this.version));
        }

        @Override
        public int hashCode() {
            return protocol.hashCode() * 37 + (int) version;
        }
    }
}
