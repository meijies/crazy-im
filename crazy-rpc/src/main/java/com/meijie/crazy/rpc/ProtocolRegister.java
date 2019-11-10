package com.meijie.crazy.rpc;

import com.google.protobuf.BlockingService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A proto Protocol Register, which will cache the Protocol Identify with one Protocol instance
 *
 * @author meijie
 */
public class ProtocolRegister {

    private static final Map<ProtoNameVer, BlockingService> protoProtocolMap = new ConcurrentHashMap<>();

    public static void registry(ProtoNameVer nameVer, BlockingService blockingService) {
        protoProtocolMap.putIfAbsent(nameVer, blockingService);
    }

    public static BlockingService getProtocolService(ProtoNameVer nameVer) {
        return protoProtocolMap.get(nameVer);
    }

    static class ProtoNameVer {
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
