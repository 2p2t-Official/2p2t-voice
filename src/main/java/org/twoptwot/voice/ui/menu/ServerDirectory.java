package org.twoptwot.voice.ui.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.twoptwot.voice.ServerGate;

public final class ServerDirectory {

    public static final String OFFICIAL_NAME = "2p2t";
    public static final String OFFICIAL_ADDRESS = "2p2t.org";

    private ServerDirectory() {
    }

    public static void ensureOfficialSaved(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        ServerList list = new ServerList(minecraft);
        list.load();
        if (ensureOfficialEntry(list)) {
            list.save();
        }
    }

    public static boolean ensureOfficialEntry(ServerList list) {
        if (list == null) {
            return false;
        }
        int found = -1;
        for (int i = 0; i < list.size(); i++) {
            ServerData data = list.get(i);
            if (data != null && ServerGate.isAllowedHost(data.ip)) {
                found = i;
                break;
            }
        }
        boolean dirty = false;
        if (found < 0) {
            list.add(new ServerData(OFFICIAL_NAME, OFFICIAL_ADDRESS, ServerData.Type.OTHER), false);
            found = list.size() - 1;
            dirty = true;
        }
        ServerData official = list.get(found);
        if (!OFFICIAL_NAME.equals(official.name)) {
            official.name = OFFICIAL_NAME;
            dirty = true;
        }
        if (official.ip == null || official.ip.isBlank()) {
            official.ip = OFFICIAL_ADDRESS;
            dirty = true;
        }
        while (found > 0) {
            list.swap(found, found - 1);
            found--;
            dirty = true;
        }
        return dirty;
    }

    public static void connectOfficial(Screen parent) {
        connect(parent, OFFICIAL_NAME, OFFICIAL_ADDRESS);
    }

    public static void connect(Screen parent, String name, String address) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || address == null || address.isBlank()) {
            return;
        }
        ensureOfficialSaved(minecraft);
        ServerData data = new ServerData(
                name == null || name.isBlank() ? OFFICIAL_NAME : name,
                address.trim(),
                ServerData.Type.OTHER);
        ServerAddress serverAddress = ServerAddress.parseString(data.ip);
        ConnectScreen.startConnecting(parent, minecraft, serverAddress, data, false, null);
    }
}
