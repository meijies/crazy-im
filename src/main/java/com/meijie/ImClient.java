package com.meijie;

import com.google.common.collect.Queues;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.meijie.common.Address;
import com.meijie.common.Constant;
import com.meijie.net.ImRpcClientHandler;

import com.meijie.service.NettyMessageService;
import io.netty.channel.Channel;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Queue;

import static com.meijie.proto.ClientRequestProtos.*;
import static com.meijie.proto.ClientRequestProtos.MessageRequestProto;
import static com.meijie.proto.ClientRequestProtos.MessageSendRequestProto;
import static com.meijie.proto.ImRequestProtos.*;

/**
 * Im client implement with netty and protobuf
 *
 * @author meijie
 */
public class ImClient {

    private Address address;
    private static final ProtocolEnum imClientProtocol = ProtocolEnum.CLIENT_PROTOCOL;

    private NettyMessageService nettyMessageService = new NettyMessageService();
    private Channel channel;
    private Queue<ImResponse> messageQueue = Queues.newLinkedBlockingQueue();

    public ImClient() {
        init(Constant.DEFAULT_ADDRESS);
    }

    public ImClient(final Address address) {
        init(address);
    }

    private void init(Address address) {
        this.address = address;
        nettyMessageService.init();
        nettyMessageService.setImRpcClientHandler(new ImRpcClientHandler(this));
        prepaeChannel();
    }

    public void sendMessage(long sender, long receiver, String message, long messageIdentify) {
        ImMessageProto imMessageProto = ImMessageProto.newBuilder()
                .setSender(sender)
                .setReceiver(receiver)
                .setContent(message)
                .build();
        MessageSendRequestProto messageSendRequestProto = MessageSendRequestProto.newBuilder()
                .setMessageIdentify(messageIdentify)
                .setMessage(imMessageProto)
                .build();
        sendMessage(messageSendRequestProto);
    }

    private void sendMessage(MessageSendRequestProto messageSendRequestProto) {
        ImRequest imRequest = buildImRequest("sendMessage", messageSendRequestProto);
        channel.writeAndFlush(imRequest);
    }

    public void getMessage(long sender, long receiver, long version) {
        MessageRequestProto messageRequestProto = MessageRequestProto.newBuilder()
                .setSender(sender)
                .setReceiver(receiver)
                .setVersion(version)
                .build();
        getMessage(messageRequestProto);
    }

    private void getMessage(MessageRequestProto messageRequestProto) {
        ImRequest imRequest = buildImRequest("getMessage", messageRequestProto);
        channel.writeAndFlush(imRequest);
    }

    public void sendFile(BufferedInputStream inputStream, long messageIdentify) throws IOException {
        FileSendRequestProto fileSendRequestProto = FileSendRequestProto.newBuilder()
                .setFile(ByteString.readFrom(inputStream))
                .setMessageIdentify(messageIdentify)
                .build();
        sendFile(fileSendRequestProto);
    }

    private void sendFile(FileSendRequestProto fileSendRequestProto) {
        ImRequest imRequest = buildImRequest("sendFile", fileSendRequestProto);
        channel.writeAndFlush(imRequest);
    }

    public void getFile(long fileId) {
        FileRequestProto fileRequestProto = FileRequestProto.newBuilder()
                .setFileId(fileId)
                .build();
        getFile(fileRequestProto);
    }

    private void getFile(FileRequestProto fileRequestProto) {
        ImRequest imRequest = buildImRequest("getFile", fileRequestProto);
        channel.writeAndFlush(imRequest);
    }

    private ImRequest buildImRequest(String methodName, Message message) {
        RequestHeaderProto requestHeader = RequestHeaderProto.newBuilder()
                .setClientProtocolVersion(imClientProtocol.getVersion())
                .setDeclaringClassProtocolName(imClientProtocol.getDeclaringClassProtoName())
                .setMethodName(methodName)
                .build();
        return ImRequest.newBuilder()
                .setHeader(requestHeader)
                .setPlayload(Any.pack(message))
                .build();
    }

    private void prepaeChannel() {
        try {
            channel = nettyMessageService.bootstrapClient(address).sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException("网络无法连接");
        }
    }

    public void addMessage(ImResponse message) {
        messageQueue.add(message);
    }

    public ImResponse takeMessage() {
        return messageQueue.poll();
    }

    public ImResponse takeMessageAndWait(int waitSecond) throws InterruptedException {
        long beginTime = System.currentTimeMillis();
        ImResponse response;
        while ((response = messageQueue.poll()) == null) {
            if ((System.currentTimeMillis() - beginTime) > waitSecond * 1000) {
                Thread.currentThread().sleep(200);
            } else {
                return null;
            }
        }
        return response;
    }

    public static void main(String[] args) throws InvalidProtocolBufferException {
        ImClient imClient = new ImClient();
        ImMessageProto imMessageProto = ImMessageProto.newBuilder()
                .setReceiver(1)
                .setContent("test")
                .setReceiver(2)
                .build();
        MessageSendRequestProto messageSendRequestProto = MessageSendRequestProto.newBuilder()
                .setMessageIdentify(1)
                .setMessage(imMessageProto)
                .build();
        imClient.sendMessage(messageSendRequestProto);

       imClient.sendMessage(messageSendRequestProto);

        ImResponse imResponse = null;
        while (imResponse == null) {
            imResponse= imClient.takeMessage();
            if (imResponse != null) {
                if (StringUtils.equals("sendMessage", imResponse.getMethodName())) {
                    System.out.println(imResponse.getPlayload().unpack(FileResponseAckProto.class).getMessageIdentify());
                }
                break;
            }
        }
    }
}
