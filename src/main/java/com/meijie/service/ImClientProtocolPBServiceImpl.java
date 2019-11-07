package com.meijie.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.meijie.domain.Message;
import com.meijie.store.BigFileStore;
import com.meijie.store.JdbcMessageStore;
import com.zaxxer.hikari.HikariConfig;

import java.util.List;

import static com.meijie.proto.ClientRequestProtos.*;

public class ImClientProtocolPBServiceImpl implements ImClientProtocolPB {

    private static final BigFileStore bigFileStore = new BigFileStore("/home/meijie/Videos");
    private JdbcMessageStore messageStore;

    public ImClientProtocolPBServiceImpl(HikariConfig config) {
        messageStore = new JdbcMessageStore(config);
    }

    @Override
    public ImMessageListProto getMessage(RpcController controller, MessageRequestProto request) throws ServiceException {
        List<Message> messageList = messageStore.getMessageList(
                request.getReceiver(), request.getSender(), request.getVersion());
        ImMessageListProto.Builder builder = ImMessageListProto.newBuilder();
        for (Message message : messageList) {
            builder.addImMessageList(ImMessageProto.newBuilder()
                    .setSender(message.getOwner())
                    .setReceiver(message.getOwner() == message.getUserId1() ?
                            message.getUserId2() : message.getUserId1())
                    .setContent(message.getContent())
                    .setVersion(message.getVersion()).build());
        }
        return builder.build();
    }

    @Override
    public MessageResponseAckProto sendMessage(RpcController controller, MessageSendRequestProto request) throws ServiceException {
        Message message = new Message();
        message.setUserId1(request.getMessage().getSender());
        message.setUserId2(request.getMessage().getReceiver());
        message.setOwner(request.getMessage().getSender());
        message.setContent(request.getMessage().getContent());
        messageStore.saveMessage(message);
        return MessageResponseAckProto.newBuilder()
                .setMessageIdentify(request.getMessageIdentify())
                .build();
    }

    @Override
    public ImFileProto getFile(RpcController controller, FileRequestProto request) throws ServiceException {
        byte[] fileByteArray = bigFileStore.readFile(request.getFileId());
        ImFileProto imFileProto = ImFileProto.newBuilder()
                .setFile(ByteString.copyFrom(fileByteArray))
                .setFileId(request.getFileId())
                .build();
        return imFileProto;
    }

    @Override
    public FileResponseAckProto sendFile(RpcController controller, FileSendRequestProto request) throws ServiceException {
        long fileId = bigFileStore.storeBigFile(request.getFile().toByteArray());
        FileResponseAckProto fileResponseAckProto = FileResponseAckProto.newBuilder()
                .setFileId(fileId)
                .setMessageIdentify(request.getMessageIdentify())
                .build();
        return fileResponseAckProto;
    }
}
