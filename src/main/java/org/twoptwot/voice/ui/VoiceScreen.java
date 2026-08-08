package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.SignalingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VoiceScreen extends Screen {

    private static String roomTab = "channel";

    private final VoiceController controller = TwoptwotVoiceClient.get().controller();
    private final SignalingClient signaling = TwoptwotVoiceClient.get().signaling();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int sideW = 128;
    private float anim;
    private VoiceButton muteBtn;
    private VoiceButton deafBtn;
    private VoiceButton channelTab;
    private VoiceButton everyoneTab;
    private final List<PeerRowWidget> peerRows = new ArrayList<>();
    private final List<AbstractWidget> contextMenu = new ArrayList<>();
    private final List<String> sidebarChannels = new ArrayList<>();
    private String lastPeerFingerprint = "";
    private String lastGroupFingerprint = "";
    private String menuStatus = "";

    public VoiceScreen() {
        super(Component.literal("2p2t Voice"));
    }

    @Override
    protected void init() {
        peerRows.clear();
        panelW = Math.min(540, width - 24);
        panelH = Math.min(340, height - 24);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        sideW = 132;

        signaling.refreshGroups();

        int topBarY = panelY + 28;
        int listX = panelX + sideW + 10;

        muteBtn = addRenderableWidget(new VoiceButton(
                panelX + panelW - (controller.isAdmin() ? 288 : 220), topBarY, 56, 18,
                Component.literal(controller.isMuted() ? "Unmute" : "Mute"),
                controller.isMuted() ? VoiceButton.Style.DANGER : VoiceButton.Style.GHOST,
                b -> {
                    controller.toggleMute();
                    refreshControls();
                }));
        deafBtn = addRenderableWidget(new VoiceButton(
                panelX + panelW - (controller.isAdmin() ? 228 : 160), topBarY, 56, 18,
                Component.literal(controller.isDeafened() ? "Undeafen" : "Deafen"),
                controller.isDeafened() ? VoiceButton.Style.DANGER : VoiceButton.Style.GHOST,
                b -> {
                    controller.toggleDeafen();
                    refreshControls();
                }));
        addRenderableWidget(new VoiceButton(
                panelX + panelW - 100, topBarY, 64, 18,
                Component.literal("Settings"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(new VoiceSettingsScreen(this))));
        if (controller.isAdmin()) {
            addRenderableWidget(new VoiceButton(
                    panelX + panelW - 168, topBarY, 64, 18,
                    Component.literal("Admin"),
                    VoiceButton.Style.PRIMARY,
                    b -> minecraft.setScreen(new VoiceAdminScreen(this))));
        }
        addRenderableWidget(new VoiceButton(
                panelX + panelW - 22, panelY + 4, 16, 16,
                Component.literal("X"),
                VoiceButton.Style.QUIET,
                b -> onClose()));

        sidebarChannels.clear();
        sidebarChannels.add("global");
        sidebarChannels.add("proximity");
        sidebarChannels.add("spawn");
        if (controller.canAccessStaffChannel()) {
            sidebarChannels.add("staff");
        }
        if (controller.isAdmin()) {
            sidebarChannels.add("lobby");
        }

        int cy = panelY + 52;
        for (String ch : sidebarChannels) {
            boolean active = ch.equals(controller.getChannel());
            VoiceButton btn = addRenderableWidget(new VoiceButton(
                    panelX + 8, cy, sideW - 16, 18,
                    Component.literal(VoiceUi.channelTitle(ch)),
                    active ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                    b -> {
                        if (!controller.trySetChannel(ch, true)) {
                            return;
                        }
                        minecraft.setScreen(new VoiceScreen());
                    }));
            btn.setSelected(active);
            cy += 20;
        }

        cy += 4;
        
        rebuildGroupButtons(cy + 10);

        addRenderableWidget(new VoiceButton(
                panelX + 8, panelY + panelH - 48, sideW - 16, 18,
                Component.literal("Leave Channel"),
                VoiceButton.Style.QUIET,
                b -> {
                    controller.leaveChannel(true);
                    minecraft.setScreen(new VoiceScreen());
                }));
        addRenderableWidget(new VoiceButton(
                panelX + 8, panelY + panelH - 28, sideW - 16, 18,
                Component.literal("+ Group"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(new GroupEditorScreen(this))));

        addRenderableWidget(new VoiceButton(
                panelX + panelW - 78, panelY + panelH - 28, 64, 18,
                Component.literal("Reconnect"),
                VoiceButton.Style.QUIET,
                b -> TwoptwotVoiceClient.get().pluginBridge().requestSession()));

        // Peers first, then tabs, so tab widgets stay above the list for hit-testing/render.
        rebuildPeers();
        ensureRoomTabs(listX);
    }

    private int groupButtonsStartY() {
        return panelY + 52 + sidebarChannels.size() * 20 + 4 + 10;
    }

    private int roomTabY() {
        // Below status text (panelY+50) with a clear gap.
        return panelY + 66;
    }

    private int roomTabH() {
        return 18;
    }

    /** Peer rows start below the In Channel / Everyone tabs. */
    private int peerListTop() {
        return roomTabY() + roomTabH() + 6;
    }

    private void ensureRoomTabs(int listX) {
        if (channelTab != null) {
            removeWidget(channelTab);
            channelTab = null;
        }
        if (everyoneTab != null) {
            removeWidget(everyoneTab);
            everyoneTab = null;
        }
        int tabY = roomTabY();
        int tabH = roomTabH();
        channelTab = addRenderableWidget(new VoiceButton(
                listX, tabY, 78, tabH,
                Component.literal("In Channel"),
                VoiceButton.Style.GHOST,
                b -> {
                    roomTab = "channel";
                    refreshTabs();
                    rebuildPeers();
                }));
        everyoneTab = addRenderableWidget(new VoiceButton(
                listX + 82, tabY, 78, tabH,
                Component.literal("Everyone"),
                VoiceButton.Style.GHOST,
                b -> {
                    roomTab = "everyone";
                    refreshTabs();
                    rebuildPeers();
                }));
        refreshTabs();
    }

    private void rebuildGroupButtons(int startY) {
        closeContextMenu();
        int cy = startY;
        int maxY = panelY + panelH - 72;
        for (SignalingClient.GroupInfo group : signaling.groups()) {
            if (cy + 18 > maxY) {
                break;
            }
            boolean active = group.channelId().equals(controller.getChannel());
            GroupChannelButton btn = addRenderableWidget(new GroupChannelButton(
                    panelX + 8, cy, sideW - 16, 18, group,
                    () -> selectGroup(group),
                    this::openGroupContextMenu));
            btn.setSelected(active);
            cy += 20;
        }
        lastGroupFingerprint = currentGroupFingerprint();
    }

    private void openInviteMenu(SignalingClient.PeerInfo peer) {
        closeContextMenu();
        List<SignalingClient.GroupInfo> inviteGroups = new ArrayList<>();
        for (SignalingClient.GroupInfo group : signaling.groups()) {
            if (group.isOwner || group.joined || group.channelId().equals(controller.getChannel())) {
                inviteGroups.add(group);
            }
        }
        if (inviteGroups.isEmpty()) {
            controller.setStatus("Join or create a group first to invite.");
            return;
        }
        int menuW = 120;
        int itemH = 16;
        int menuX = Math.min(width - menuW - 4, panelX + sideW + 40);
        int menuY = panelY + 72;
        int y = menuY;
        for (SignalingClient.GroupInfo group : inviteGroups) {
            VoiceButton btn = addRenderableWidget(new VoiceButton(
                    menuX, y, menuW, itemH,
                    Component.literal("→ " + group.name),
                    VoiceButton.Style.GHOST,
                    b -> {
                        closeContextMenu();
                        signaling.inviteToGroup(group.id, peer.uuid, () -> {
                            if (minecraft != null) {
                                minecraft.execute(() -> controller.setStatus("Invited " + peer.name + " to " + group.name));
                            }
                        }, err -> {
                            if (minecraft != null) {
                                minecraft.execute(() -> controller.setStatus("Invite failed: " + err));
                            }
                        });
                    }));
            contextMenu.add(btn);
            y += itemH + 2;
        }
        VoiceButton cancel = addRenderableWidget(new VoiceButton(
                menuX, y, menuW, itemH,
                Component.literal("Cancel"),
                VoiceButton.Style.QUIET,
                b -> closeContextMenu()));
        contextMenu.add(cancel);
    }

    private void closeContextMenu() {
        for (AbstractWidget w : contextMenu) {
            removeWidget(w);
        }
        contextMenu.clear();
        menuStatus = "";
    }

    private void openGroupContextMenu(GroupChannelButton source) {
        closeContextMenu();
        SignalingClient.GroupInfo group = source.group();
        if (group == null) {
            return;
        }

        int menuW = 108;
        int itemH = 16;
        final int menuX = Math.min(width - menuW - 4, source.getX() + source.getWidth() + 4);

        int count = 0;
        if (group.isOwner) {
            count += group.isPublic ? 2 : 3; 
        }
        boolean inGroup = group.channelId().equals(controller.getChannel()) || group.joined;
        if (inGroup) {
            count += 1;
        }
        if (count == 0) {
            controller.setStatus("Only the owner can manage this group.");
            return;
        }

        int menuY = source.getY();
        int totalH = count * (itemH + 2) + 4;
        if (menuY + totalH > height - 4) {
            menuY = Math.max(4, height - 4 - totalH);
        }
        final int finalMenuY = menuY;

        List<MenuAction> actions = new ArrayList<>();
        if (group.isOwner) {
            actions.add(new MenuAction(group.isPublic ? "Rename" : "Edit…", VoiceButton.Style.GHOST, () -> {
                closeContextMenu();
                minecraft.setScreen(new GroupEditorScreen(this, group));
            }));
            if (!group.isPublic) {
                actions.add(new MenuAction("Members…", VoiceButton.Style.GHOST, () -> {
                    closeContextMenu();
                    minecraft.setScreen(new GroupEditorScreen(this, group));
                }));
            }
        }
        if (inGroup) {
            actions.add(new MenuAction("Leave", VoiceButton.Style.QUIET, () -> leaveGroup(group)));
        }
        if (group.isOwner) {
            actions.add(new MenuAction("Delete", VoiceButton.Style.DANGER,
                    () -> showDeleteConfirm(group, menuX, finalMenuY)));
        }

        int y = finalMenuY;
        for (MenuAction action : actions) {
            VoiceButton btn = addRenderableWidget(new VoiceButton(
                    menuX, y, menuW, itemH,
                    Component.literal(action.label),
                    action.style,
                    b -> action.run.run()));
            contextMenu.add(btn);
            y += itemH + 2;
        }
    }

    private void showDeleteConfirm(SignalingClient.GroupInfo group, int mx, int my) {
        closeContextMenu();
        int menuW = 108;
        int itemH = 16;
        VoiceButton confirm = addRenderableWidget(new VoiceButton(
                mx, my, menuW, itemH,
                Component.literal("Confirm delete"),
                VoiceButton.Style.DANGER,
                b -> deleteGroup(group)));
        VoiceButton cancel = addRenderableWidget(new VoiceButton(
                mx, my + itemH + 2, menuW, itemH,
                Component.literal("Cancel"),
                VoiceButton.Style.QUIET,
                b -> closeContextMenu()));
        contextMenu.add(confirm);
        contextMenu.add(cancel);
    }

    private void deleteGroup(SignalingClient.GroupInfo group) {
        closeContextMenu();
        controller.setStatus("Deleting " + group.name + "…");
        signaling.deleteGroup(group.id, () -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (group.channelId().equals(controller.getChannel())) {
                        controller.setChannel("global", true);
                    }
                    controller.setStatus("Deleted group " + group.name);
                    minecraft.setScreen(new VoiceScreen());
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> controller.setStatus("Delete failed: " + err));
            }
        });
    }

    private void leaveGroup(SignalingClient.GroupInfo group) {
        closeContextMenu();
        controller.setStatus("Leaving " + group.name + "…");
        signaling.leaveGroup(group.id, () -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (group.channelId().equals(controller.getChannel())) {
                        controller.setChannel("global", true);
                    }
                    controller.setStatus("Left " + group.name);
                    minecraft.setScreen(new VoiceScreen());
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> controller.setStatus("Leave failed: " + err));
            }
        });
    }

    private record MenuAction(String label, VoiceButton.Style style, Runnable run) {
    }

    private void selectGroup(SignalingClient.GroupInfo group) {
        closeContextMenu();
        if (group == null || group.id == null) {
            return;
        }
        if (group.channelId().equals(controller.getChannel())) {
            return;
        }
        controller.setStatus("Joining " + group.name + "…");
        signaling.joinGroup(group.id, () -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    controller.setChannel(group.channelId(), true);
                    minecraft.setScreen(new VoiceScreen());
                });
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> controller.setStatus("Group join failed: " + err));
            }
        });
    }

    private void refreshControls() {
        if (muteBtn != null) {
            muteBtn.setMessage(Component.literal(controller.isMuted() ? "Unmute" : "Mute"));
            muteBtn.setStyle(controller.isMuted() ? VoiceButton.Style.DANGER : VoiceButton.Style.GHOST);
        }
        if (deafBtn != null) {
            deafBtn.setMessage(Component.literal(controller.isDeafened() ? "Undeafen" : "Deafen"));
            deafBtn.setStyle(controller.isDeafened() ? VoiceButton.Style.DANGER : VoiceButton.Style.GHOST);
        }
    }

    private void refreshTabs() {
        if (channelTab != null) {
            channelTab.setSelected("channel".equals(roomTab));
        }
        if (everyoneTab != null) {
            everyoneTab.setSelected("everyone".equals(roomTab));
        }
    }

    private void rebuildPeers() {
        for (PeerRowWidget row : peerRows) {
            removeWidget(row);
        }
        peerRows.clear();

        int listX = panelX + sideW + 10;
        int listW = panelW - sideW - 20;
        int py = peerListTop();
        List<Map.Entry<String, SignalingClient.PeerInfo>> list = new ArrayList<>(signaling.peers().entrySet());
        list.sort((a, b) -> {
            String an = a.getValue().name == null ? "" : a.getValue().name;
            String bn = b.getValue().name == null ? "" : b.getValue().name;
            return an.compareToIgnoreCase(bn);
        });
        StringBuilder fp = new StringBuilder(roomTab).append('|').append(controller.getChannel());
        for (Map.Entry<String, SignalingClient.PeerInfo> e : list) {
            SignalingClient.PeerInfo peer = e.getValue();
            if (peer.hidden) {
                continue;
            }
            if ("channel".equals(roomTab) && peer.channel != null
                    && !peer.channel.equals(controller.getChannel())) {
                continue;
            }
            if (py + 28 > panelY + panelH - 36) {
                break;
            }
            PeerRowWidget row = addRenderableWidget(new PeerRowWidget(
                    listX, py, listW, 26, peer, controller, () -> {
            }, this::openInviteMenu));
            peerRows.add(row);
            fp.append('|').append(peer.uuid);
            py += 28;
        }
        lastPeerFingerprint = fp.toString();
        // Keep tabs above freshly rebuilt rows.
        if (channelTab != null || everyoneTab != null) {
            ensureRoomTabs(listX);
        }
    }

    private String currentGroupFingerprint() {
        StringBuilder fp = new StringBuilder();
        for (SignalingClient.GroupInfo group : signaling.groups()) {
            fp.append(group.id).append(':').append(group.name).append(';');
        }
        return fp.toString();
    }

    @Override
    public void tick() {
        anim += 0.05f;
        for (PeerRowWidget row : peerRows) {
            row.tickAnim(anim);
        }
        StringBuilder fp = new StringBuilder(roomTab).append('|').append(controller.getChannel());
        for (Map.Entry<String, SignalingClient.PeerInfo> e : signaling.peers().entrySet()) {
            SignalingClient.PeerInfo peer = e.getValue();
            if (peer.hidden) {
                continue;
            }
            if ("channel".equals(roomTab) && peer.channel != null
                    && !peer.channel.equals(controller.getChannel())) {
                continue;
            }
            fp.append('|').append(peer.uuid);
        }
        if (!fp.toString().equals(lastPeerFingerprint)) {
            rebuildPeers();
        }
        String gf = currentGroupFingerprint();
        if (!gf.equals(lastGroupFingerprint)) {
            minecraft.setScreen(new VoiceScreen());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.dimWorld(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.panel(graphics, panelX, panelY, panelW, panelH);
        VoiceUi.accentBar(graphics, panelX + 1, panelY + 1, panelW - 2);
        VoiceUi.sidebar(graphics, panelX, panelY + 26, sideW, panelH - 26);

        String title = controller.getName().isBlank() ? "2p2t Voice" : "2p2t Voice  ·  " + controller.getName();
        graphics.drawString(font, title, panelX + 10, panelY + 8, VoiceUi.TEXT, false);

        String statusLine = controller.getStatus();
        if (controller.isServerMuted()) {
            statusLine = "SERVER MUTED  ·  " + statusLine;
        }
        int statusY = panelY + 50;
        int statusX = panelX + sideW + 10;
        int statusMax = panelW - sideW - 20;
        while (statusLine.length() > 4 && font.width(statusLine) > statusMax) {
            statusLine = statusLine.substring(0, statusLine.length() - 1);
        }
        graphics.drawString(font, statusLine, statusX, statusY, VoiceUi.TEXT_DIM, false);

        if (controller.config().hudDebug) {
            String meta = "peers " + controller.getPeerCount();
            if (TwoptwotVoiceClient.get().webRtc().isAvailable()) {
                meta += "  ·  RTC " + TwoptwotVoiceClient.get().webRtc().connectedPeerCount();
                String path = TwoptwotVoiceClient.get().webRtc().pathSummary();
                if (path != null && !path.isBlank()) {
                    meta += "  ·  " + path;
                }
            }
            graphics.drawString(font, meta, panelX + sideW + 10, panelY + panelH - 22, VoiceUi.TEXT_FAINT, false);
        }

        graphics.drawString(font, "CHANNELS", panelX + 12, panelY + 34, VoiceUi.TEXT_FAINT, false);
        int groupsLabelY = groupButtonsStartY() - 10;
        graphics.drawString(font, "GROUPS", panelX + 12, groupsLabelY, VoiceUi.TEXT_FAINT, false);

        if (peerRows.isEmpty()) {
            graphics.drawCenteredString(font, "No one here yet",
                    panelX + sideW + (panelW - sideW) / 2,
                    panelY + panelH / 2, VoiceUi.TEXT_FAINT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!contextMenu.isEmpty() && event.button() == 0) {
            boolean onMenu = false;
            for (AbstractWidget w : contextMenu) {
                if (w.isMouseOver(event.x(), event.y())) {
                    onMenu = true;
                    break;
                }
            }
            if (!onMenu) {
                closeContextMenu();
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
