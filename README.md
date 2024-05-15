# ServerSwitcher
A Fabric mod for switching servers via transfer packets.

## Usage
This mod requires the MidnightCore SQL module to be loaded. Enable it with `/mcore module enable sql` and load it with
`/mcore module load sql`. <br/>
Optionally, the MidnightCore Messenger module will be used to automatically cause all servers to sync when the database is
updated. Enable it with `/mcore module enable messenger` and load it with `/mcore module load messenger`. <br/>
For use with BungeeCord, the MidnightCore plugin message module must also be loaded. It is enabled by default, but to be
sure, enable it with `/mcore module enable plugin_message` and load it with `/mcore module load plugin_message`. <br/>

Once the desired modules are loaded, reload ServerSwitcher with `/svs reload`

### Commands
To switch to another server: `/server <name>` <br/>
To view a GUI containing available servers: `/servers`

To add a server: `/svs add <name> [-h <hostname> | -p <port> | -b <backend> | -P <permission> | -g <in_gui> | -i <item>]` <br/>
To remove a server: `/svs remove <name>`<br/>
To edit a server: `/svs edit <name> [-h <hostname> | -p <port> | -b <backend> | -P <permission> | -g <in_gui> | -i <item>]` <br/>
To list all servers in all namespaces: `/svs list` <br/>
To get info about a server: `/svs info <name>` <br/>
To sync the database: `/svs sync` <br/>
To reload the configuration: `/svs reload` <br/>

`/server` requires the permission `serverswitcher.server` or op level 3<br/>
`/servers` requires the permission `serverswitcher.servers` or op level 3<br/>
`/svs` requires the permission `serverswitcher.admin` or op level 4


## Configuration
The root of the config file is a section with 4 keys:
- `server`: The name of the server this mod installed on.
- `proxy_type`: The type of proxy to use for backend switching. May be `none`, `midnightproxy`, or `bungeecord`
- `storage`: Configuration for the MidnightCore SQL module. Will use the `default` preset by default, with a table prefix of 
`svs_`. See [MidnightCore](https://github.com/wallenjos01/MidnightCore) for more information.
- `messenger`: The name of the MidnightCore messenger to use. Only applicable if the messenger module is loaded.
- `jwt_expire_sec`: Only relevant when used with [MidnightProxy](https://github.com/wallenjos01/MidnightProxy). Determines
how long switch cookies should remain valid. 
- `clear_reconnect_cookie`: Only relevant when used with [MidnightProxy](https://github.com/wallenjos01/MidnightProxy).
MidnightProxy uses a reconnect cookie to facilitate re-routing players after encryption. The cookies are only valid for
one reconnect anyway, so clearing the cookie is recommended. When this is enabled, the MidnightProxy reconnect cookie, as
well as the switch cookie, are cleared when a player connects.

### Examples
Adding servers for a network which switch based on hostname
```
/svs add lobby -h lobby.server.net 
/svs add survival -h survival.server.net 
/svs add creative -h creative.server.net -P server.creative 
```

Adding servers for a network which switch based on port
```
/svs add lobby -h server.net -p 25566
/svs add survival -h server.net -p 25567
/svs add creative -h server.net -p 25568 -P server.creative
```

Adding servers for a network which switch based on proxy backends
```
/svs add lobby -b lobby 
/svs add survival -b survival 
/svs add creative -b creative -P server.creative 
```

Editing a server to add a custom item for the GUI:
```
/svs edit survival -i {"id":"minecraft:grass_block","name":"{\"text\":\"Survival\",\"color\":\"green\"}"]
```
Hiding a server from the GUI:
```
/svs edit creative -g false
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