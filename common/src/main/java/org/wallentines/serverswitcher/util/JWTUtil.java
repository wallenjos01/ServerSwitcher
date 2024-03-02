package org.wallentines.serverswitcher.util;

import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.mdproxy.jwt.CryptCodec;
import org.wallentines.mdproxy.jwt.JWTBuilder;
import org.wallentines.mdproxy.jwt.KeyCodec;
import org.wallentines.midnightlib.registry.Identifier;

import java.security.PublicKey;
import java.util.Objects;
import java.util.UUID;

public class JWTUtil {

    public static String createJWT(PublicKey key, long expire, String hostname, int port, int protocol, String username, UUID uuid, String backend) {

        return new JWTBuilder()
                .expiresIn(expire)
                .issuedBy("serverswitcher")
                .withClaim("hostname", hostname)
                .withClaim("port", Objects.toString(port))
                .withClaim("protocol", Objects.toString(protocol))
                .withClaim("username", username)
                .withClaim("uuid", uuid.toString())
                .withClaim("backend", backend)
                .withClaim("ssid", UUID.randomUUID(), Serializer.UUID)
                .encrypted(KeyCodec.RSA_OAEP(key), CryptCodec.A128CBC_HS256())
                .asString().getOrThrow();
    }

}
