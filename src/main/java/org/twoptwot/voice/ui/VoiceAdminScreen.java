package org.twoptwot.voice.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.net.SignalingClient;

import java.util.ArrayList;
import java.util.List;

public final class VoiceAdminScreen extends Screen {

    private final Screen parent;
    private final SignalingClient signaling = TwoptwotVoiceClient.get().signaling();
    private final List<AdminClient> clients = new ArrayList<>();
    private String status = "Loading…";
    private String turnStatus = "TURN: …";
    private int selected;
    private int scroll;
    private long lastRefreshMs;

    private static final String[] MOVE_CHANNELS = {
            "lobby", "global", "proximity", "spawn", "staff"
    };

    public VoiceAdminScreen(Screen parent) {
        super(Component.literal("Voice Admin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refresh();
        int panelW = Math.min(520, width - 24);
        int panelH = Math.min(360, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int y = panelY + panelH - 52;

        addRenderableWidget(new VoiceButton(panelX + 12, y, 70, 20, Component.literal("Refresh"),
                VoiceButton.Style.GHOST, b -> refresh()));
        addRenderableWidget(new VoiceButton(panelX + 88, y, 70, 20, Component.literal("TURN"),
                VoiceButton.Style.QUIET, b -> checkTurn()));

        int bx = panelX + 170;
        for (String ch : MOVE_CHANNELS) {
            String label = switch (ch) {
                case "global" -> "Global";
                case "proximity" -> "Prox";
                case "spawn" -> "Spawn";
                case "staff" -> "Staff";
                default -> "Lobby";
            };
            addRenderableWidget(new VoiceButton(bx, y, 52, 20, Component.literal(label),
                    VoiceButton.Style.GHOST, b -> moveSelected(ch)));
            bx += 56;
        }

        y += 24;
        addRenderableWidget(new VoiceButton(panelX + 12, y, 90, 20, Component.literal("Server Mute"),
                VoiceButton.Style.DANGER, b -> serverMute(true)));
        addRenderableWidget(new VoiceButton(panelX + 108, y, 100, 20, Component.literal("Server Unmute"),
                VoiceButton.Style.PRIMARY, b -> serverMute(false)));
        addRenderableWidget(new VoiceButton(panelX + 214, y, 90, 20, Component.literal("Force Deaf"),
                VoiceButton.Style.DANGER, b -> serverDeaf(true)));
        addRenderableWidget(new VoiceButton(panelX + 310, y, 100, 20, Component.literal("Force Undeaf"),
                VoiceButton.Style.PRIMARY, b -> serverDeaf(false)));
        addRenderableWidget(new VoiceButton(panelX + panelW - 72, y, 60, 20, Component.literal("Back"),
                VoiceButton.Style.QUIET, b -> minecraft.setScreen(parent)));
    }

    private void refresh() {
        status = "Refreshing…";
        lastRefreshMs = System.currentTimeMillis();
        signaling.adminPost("/api/admin/status", new JsonObject(), res -> {
            if (minecraft != null) {
                minecraft.execute(() -> applyStatus(res));
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> status = "Error: " + err);
            }
        });
        checkTurn();
    }

    private void checkTurn() {
        signaling.adminPost("/api/admin/turn-health", new JsonObject(), res -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    boolean ok = res.has("ok") && res.get("ok").getAsBoolean();
                    String detail = res.has("detail") ? res.get("detail").getAsString() : "";
                    turnStatus = ok ? ("TURN OK · " + detail) : ("TURN FAIL · " + detail);
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> turnStatus = "TURN error: " + err);
            }
        });
    }

    private void applyStatus(JsonObject res) {
        clients.clear();
        JsonArray arr = res.has("clients") && res.get("clients").isJsonArray()
                ? res.getAsJsonArray("clients") : null;
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject c = el.getAsJsonObject();
                AdminClient ac = new AdminClient();
                ac.uuid = str(c, "uuid");
                ac.name = str(c, "name");
                ac.channel = str(c, "channel");
                ac.serverMuted = bool(c, "serverMuted");
                ac.deafened = bool(c, "deafened");
                ac.speaking = bool(c, "speaking");
                ac.muted = bool(c, "muted") || bool(c, "selfMuted");
                clients.add(ac);
            }
        }
        clients.sort((a, b) -> {
            int lobby = Boolean.compare("lobby".equals(b.channel), "lobby".equals(a.channel));
            if (lobby != 0) {
                return lobby;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        if (selected >= clients.size()) {
            selected = Math.max(0, clients.size() - 1);
        }
        status = clients.size() + " clients";
    }

    private void moveSelected(String channel) {
        AdminClient c = selectedClient();
        if (c == null) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetUuid", c.uuid);
        body.addProperty("channel", channel);
        signaling.adminPost("/api/admin/move-channel", body, res -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = "Moved " + c.name + " → " + VoiceUi.channelTitle(channel);
                    refresh();
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> status = "Move failed: " + err);
            }
        });
    }

    private void serverMute(boolean muted) {
        AdminClient c = selectedClient();
        if (c == null) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetUuid", c.uuid);
        body.addProperty("name", c.name);
        body.addProperty("muted", muted);
        signaling.adminPost("/api/admin/server-mute", body, res -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = (muted ? "Muted " : "Unmuted ") + c.name;
                    refresh();
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> status = "Mute failed: " + err);
            }
        });
    }

    private void serverDeaf(boolean deafened) {
        AdminClient c = selectedClient();
        if (c == null) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetUuid", c.uuid);
        body.addProperty("deafened", deafened);
        signaling.adminPost("/api/admin/server-deafen", body, res -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = (deafened ? "Deafened " : "Undeafened ") + c.name;
                    refresh();
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> status = "Deafen failed: " + err);
            }
        });
    }

    private AdminClient selectedClient() {
        if (selected < 0 || selected >= clients.size()) {
            status = "Select a client first";
            return null;
        }
        return clients.get(selected);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int panelW = Math.min(520, width - 24);
        int panelH = Math.min(360, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int listTop = panelY + 48;
        int rowH = 14;
        int visible = 12;
        double mx = event.x();
        double my = event.y();
        if (mx >= panelX + 12 && mx <= panelX + panelW - 12 && my >= listTop && my < listTop + visible * rowH) {
            int idx = scroll + (int) ((my - listTop) / rowH);
            if (idx >= 0 && idx < clients.size()) {
                selected = idx;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, clients.size() - 12);
        scroll = (int) Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.dimWorld(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (System.currentTimeMillis() - lastRefreshMs > 8000L) {
            refresh();
        }
        int panelW = Math.min(520, width - 24);
        int panelH = Math.min(360, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        VoiceUi.panel(graphics, panelX, panelY, panelW, panelH);
        VoiceUi.accentBar(graphics, panelX + 1, panelY + 1, panelW - 2);
        graphics.drawString(font, "Voice Admin", panelX + 14, panelY + 10, VoiceUi.TEXT, false);
        graphics.drawString(font, status, panelX + 120, panelY + 10, VoiceUi.TEXT_DIM, false);
        graphics.drawString(font, turnStatus, panelX + 14, panelY + 24, VoiceUi.TEXT_FAINT, false);

        int listTop = panelY + 48;
        int rowH = 14;
        int visible = 12;
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= clients.size()) {
                break;
            }
            AdminClient c = clients.get(idx);
            int y = listTop + i * rowH;
            if (idx == selected) {
                graphics.fill(panelX + 12, y - 1, panelX + panelW - 12, y + rowH - 1, VoiceUi.BG_ROW_HOT);
            }
            String flags = "";
            if (c.serverMuted) {
                flags += " SM";
            }
            if (c.deafened) {
                flags += " DF";
            }
            if (c.muted) {
                flags += " M";
            }
            if (c.speaking) {
                flags += " *";
            }
            String line = c.name + "  ·  " + VoiceUi.channelTitle(c.channel) + flags;
            graphics.drawString(font, line, panelX + 16, y, idx == selected ? VoiceUi.GOLD : VoiceUi.TEXT, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }

    private static final class AdminClient {
        String uuid = "";
        String name = "";
        String channel = "";
        boolean serverMuted;
        boolean deafened;
        boolean speaking;
        boolean muted;
    }
}
