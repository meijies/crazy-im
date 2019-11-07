package com.meijie;

import com.google.protobuf.BlockingService;

/**
 * 协议枚举
 * @author meijie
 */
public enum ProtocolEnum {
    CLIENT_PROTOCOL("com.kaiyuan.service.ImClientProtocolPBServiceImpl",
            1);

    private String declaringClassProtoName;
    private int version;

    ProtocolEnum(String declaringClassProtoName, int version) {
        this.declaringClassProtoName = declaringClassProtoName;
        this.version = version;
    }

    public String getDeclaringClassProtoName() {
        return declaringClassProtoName;
    }

    public int getVersion() {
        return version;
    }

    public BlockingService getBlockingService() {
        switch (this.name()) {
            case "CLIENT_PROTOCOL":
                return ProtocolFactory.imClientProtocol();
            default:
                throw new UnsupportedOperationException("unsupported protocol");
        }
    }
}
