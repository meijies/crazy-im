package com.meijie.crazy.rpc;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * * +------------------+------------------++----------------+
 * * | FRAME LENGTH (4) | MAGIC NUMBER (4) || CUSTOM MESSAGE |
 * * +------------------+------------------++----------------+
 *
 * @author meijie
 */
public class NettyMessageProtocol {

    static final int MAGIC_NUMBER = 0xDBCFE0FF;

    public class NettyMessageDecoder extends LengthFieldBasedFrameDecoder {
        public NettyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
            super(Integer.MAX_VALUE, 0, 4, -4, 4);
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            ByteBuf msg = (ByteBuf) super.decode(ctx, in);
            if (msg == null) {
                return null;
            }

            if (MAGIC_NUMBER != msg.readInt()) {
                throw new IllegalStateException(
                        "Network stream corrupted: received incorrect magic number.");
            }
            return NettyMessage.getInstance(in);
        }
    }

    public class NettyMessageEncoder extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof NettyMessage) {
                ByteBuf serialized = null;
                serialized = ((NettyMessage) msg).writeTo(ctx.alloc());
                if (serialized != null) {
                    ctx.write(serialized, promise);
                }
            } else {
                ctx.write(msg, promise);
            }
        }
    }
}
