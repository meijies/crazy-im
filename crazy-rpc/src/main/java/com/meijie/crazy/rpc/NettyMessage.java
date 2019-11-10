package com.meijie.crazy.rpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;

import static com.meijie.crazy.rpc.proto.RpcProtocol.RpcRequestHeaderProto;

/**
 * Im Message which decode the message from ByteBuf and encode the message to ByteBuf
 *
 * @author meijie
 */
public class NettyMessage {

    private static final int FRAME_HEADER_LENGTH = 4 + 4; // frame length (4), magic number (4)
    private ByteBuf byteBuf;
    private RpcRequestHeaderProto requestHeader;
    private Message payload;

    public NettyMessage(RpcRequestHeaderProto requestHeader, Message payload) {
        this.requestHeader = requestHeader;
        this.payload = payload;
    }

    public NettyMessage(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    public static NettyMessage getInstance(final ByteBuf byteBuf) {
        return new NettyMessage(byteBuf);
    }

    public RpcRequestHeaderProto getRequestHeader() throws IOException {
        if (byteBuf != null && requestHeader == null) {
            return getValue(RpcRequestHeaderProto.getDefaultInstance());
        }
        return requestHeader;
    }

    private <T extends Message> T getValue(T message) throws IOException {
        final byte[] array;
        final int offset;
        final int length = byteBuf.readableBytes();

        if (byteBuf.hasArray()) {
            array = byteBuf.array();
            offset = byteBuf.arrayOffset() + byteBuf.readerIndex();
        } else {
            array = ByteBufUtil.getBytes(byteBuf, byteBuf.readerIndex(), length, false);
            offset = 0;
        }
        CodedInputStream cis = CodedInputStream.newInstance(
                array, offset, length);
        try {
            return (T) message.getParserForType().parseFrom(cis);
        } finally {
            byteBuf.skipBytes(cis.getTotalBytesRead());
        }
    }

    public <T extends Message> T getPayload(T message) throws IOException {
        message = getValue(message);
        byteBuf.release();
        return message;
    }

    public ByteBuf writeTo(ByteBufAllocator allocator) {
        int length = requestHeader.getSerializedSize() +
                payload.getSerializedSize() + FRAME_HEADER_LENGTH;
        ByteBuf result = allocator.directBuffer();
        result.writeInt(length);
        result.writeInt(CrazyNettyProtocol.MAGIC_NUMBER);
        result.writeBytes(requestHeader.toByteArray());
        result.writeBytes(payload.toByteArray());
        return result;
    }
}
