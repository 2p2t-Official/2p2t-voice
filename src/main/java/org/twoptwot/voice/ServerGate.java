package org.twoptwot.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;

public final class ServerGate {

    private ServerGate() {
    }

    public static boolean isAllowed() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        if (client.hasSingleplayerServer() || client.isLocalServer()) {
            return false;
        }

        ServerData data = client.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return isAllowedHost(data.ip);
        }

        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            ServerData fromPacket = connection.getServerData();
            if (fromPacket != null && fromPacket.ip != null && !fromPacket.ip.isBlank()) {
                return isAllowedHost(fromPacket.ip);
            }
            SocketAddress remote = connection.getConnection().getRemoteAddress();
            if (remote instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if (host != null && isAllowedHost(host)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isAllowedHost(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String host = raw.trim().toLowerCase(Locale.ROOT);
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.lastIndexOf(':');

        if (colon > 0 && host.indexOf(']') < 0 && host.indexOf('.') >= 0) {
            host = host.substring(0, colon);
        }
        if (host.startsWith("[") && host.contains("]")) {
            host = host.substring(1, host.indexOf(']'));
        }

        return "2p2t.org".equals(host) || host.endsWith(".2p2t.org");
    }
}
