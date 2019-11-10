package com.meijie.crazy.rpc.service;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.meijie.crazy.rpc.proto.PingProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingServicePbImpl implements PingService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public PingProtocol.PingProto sendPingRequest(RpcController controller,
                                                  PingProtocol.PingProto request) throws ServiceException {
        log.info("receive ping");
        return PingProtocol.PingProto.newBuilder()
                .setMessage("pong").build();
    }
}
