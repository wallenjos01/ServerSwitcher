# ServerSwitcher
A Fabric mod for switching servers via transfer packets.

## Configuration
The root of the config file is a section with 4 keys:
- `server`: The name of the server this mod installed on. 
- `servers`: A map of server ids to definitions. (See below)
- `jwt_expire_sec`: Only relevant when used with [MidnightProxy](https://github.com/wallenjos01/MidnightProxy). Determines
how long switch cookies should remain valid. 
- `clear_reconnect_cookie`: Only relevant when used with [MidnightProxy](https://github.com/wallenjos01/MidnightProxy).
MidnightProxy uses a reconnect cookie to facilitate re-routing players after encryption. The cookies are only valid for
one reconnect anyway, so clearing the cookie is recommended. When this is enabled, the MidnightProxy reconnect cookie, as
well as the switch cookie, are cleared when a player connects.

## Server Definitions
Server definitions are sections with 4 optional keys:
- `hostname`: The hostname of the server to transfer the player to. If omitted, defaults to the hostname the player connected with.
- `port`: The port of the server to transfer the player to. If omitted, defaults to the port the player connected with.
- `proxy_backend`: If set, the server will set a JWT switch cookie on the client before transferring it. This cookie contains 
  the server's backend ID, so MidnightProxy can be configured to route based on this cookie. To configure MidnightProxy
  integration, see below.
- `permission`: If set, the player will need this permission to switch to the server.

### Examples
A configuration which switches based on hostname
```json
{
  "server": "lobby",
  "servers": { 
    "lobby": {
      "hostname": "lobby.server.net",
      "port": 25565
    },
    "survival": {
      "hostname": "survival.server.net",
      "port": 25565
    },
    "creative": {
      "hostname": "creative.server.net",
      "port": 25565,
      "permission": "server.creative"
    }
  }
}
```

A configuration which switches based on [MidnightProxy](https://github.com/wallenjos01/MidnightProxy) backends
```json
{
  "server": "lobby",
  "servers": { 
    "lobby": {
      "hostname": "server.net",
      "port": 25565,
      "proxy_backend": "lobby"
    },
    "survival": {
      "hostname": "server.net",
      "port": 25565,
      "proxy_backend": "survival"
    },
    "creative": {
      "hostname": "server.net",
      "port": 25565,
      "proxy_backend": "creative",
      "permission": "server.creative"
    }
  }
}
```

## Integration with MidnightProxy
ServerSwitcher is designed to integrate with [MidnightProxy](https://github.com/wallenjos01/MidnightProxy). It does this
by setting encrypted JWT switch cookies on clients which can be read by the proxy during the login or config phase in order to 
route players. The JWT plugin will need to be installed on your MidnightProxy instance in order to use this feature.

### Key Setup
In order to ensure integrity of switch cookies after entrusting them to the client, they are encrypted on servers using 
an RSA public key, and decrypted on the proxy using its companion RSA private key. These keys can be generated in any
way RSA keys are typically generated, including by running the command `jwt genKey key -t rsa` on your MidnightProxy instance.
The public key should be put in the mod's config folder in the file `key.pub`

### JWT Format
The switch cookies are JWTs with the following claims:
```json
{
  "iss": "serverswitcher",
  "exp": "<expiration time>",
  "hostname": "<transferred hostname>",
  "port": "<transferred port>",
  "protocol": "<player protocol version>",
  "username": "<player username>",
  "uuid": "<player uuid>",
  "backend": "<the backend to connect to>",
  "ssid": "<a randomly generated token id>"
}
```

### MidnightProxy Route Example
Users of MidnightProxy should validate all the switch token claims in their configuration:
```json
{
  "routes": [
    {
      "requirement": {
        "type": "jwt",
        "key": "key",
        "cookie": "serverswitcher:switch",
        "expect_claims": {
          "hostname": "%client_hostname%",
          "port": "%client_port%",
          "protocol": "%client_protocol%",
          "username": "%client_username%",
          "uuid": "%client_uuid%"
        },
        "output_claims": [
          "backend"
        ],
        "single_use_claim": "ssid",
        "require_auth": false
      },
      "backend": "%jwt.backend%"
    },
    {
      "backend": "lobby"
    }
  ]
}
```