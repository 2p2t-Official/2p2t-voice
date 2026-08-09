package org.twoptwot.voice.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.net.SignalingClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class GroupEditorScreen extends Screen {

    private final Screen parent;
    private final SignalingClient.GroupInfo editing;
    private EditBox nameBox;
    private EditBox allowedBox;
    private boolean isPublic;
    private String status;
    private boolean busy;
    private String nameValue = "";
    private String allowedValue = "";

    private int panelX;
    private int panelY;
    private int panelW = 300;
    private int panelH = 168;

    private final boolean adminManage;

    public GroupEditorScreen(Screen parent) {
        this(parent, null, true, "", "", false);
    }

    public GroupEditorScreen(Screen parent, SignalingClient.GroupInfo editing) {
        this(parent, editing, false);
    }

    public GroupEditorScreen(Screen parent, SignalingClient.GroupInfo editing, boolean adminManage) {
        this(parent, editing,
                editing == null || editing.isPublic,
                editing != null && editing.name != null ? editing.name : "",
                editing != null ? String.join(", ", editing.allowedNames) : "",
                adminManage);
    }

    private GroupEditorScreen(Screen parent, SignalingClient.GroupInfo editing, boolean isPublic,
                              String nameValue, String allowedValue) {
        this(parent, editing, isPublic, nameValue, allowedValue, false);
    }

    private GroupEditorScreen(Screen parent, SignalingClient.GroupInfo editing, boolean isPublic,
                              String nameValue, String allowedValue, boolean adminManage) {
        super(Component.literal(editing == null ? "Create Voice Group" : "Edit Voice Group"));
        this.parent = parent;
        this.editing = editing;
        this.isPublic = isPublic;
        this.adminManage = adminManage;
        this.nameValue = nameValue == null ? "" : nameValue;
        this.allowedValue = allowedValue == null ? "" : allowedValue;
        if (editing != null) {
            this.status = adminManage
                    ? "Admin edit: rename, visibility, and access."
                    : (editing.isPublic
                    ? "Rename your public group."
                    : "Rename and update who can join.");
        } else {
            this.status = "Create a public or private voice group.";
        }
    }

    private Screen backScreen() {
        return parent != null ? parent : new VoiceScreen();
    }

    @Override
    protected void init() {
        boolean showAllowed = !isPublic;
        panelW = Math.min(320, width - 40);
        panelH = showAllowed ? 230 : (editing != null ? 148 : 168);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int cx = panelX + 20;
        int cw = panelW - 40;

        nameBox = new EditBox(font, cx, panelY + 40, cw, 20, Component.literal("Group name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.literal("Group name"));
        nameBox.setValue(nameValue);
        addRenderableWidget(nameBox);

        int y = panelY + 68;
        if (editing == null || adminManage) {
            addRenderableWidget(new VoiceButton(
                    cx, y, cw, 20,
                    Component.literal(isPublic ? "Visibility: Public" : "Visibility: Private"),
                    isPublic ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                    b -> minecraft.setScreen(new GroupEditorScreen(
                            parent, editing, !isPublic, nameBox.getValue(),
                            allowedBox != null ? allowedBox.getValue() : allowedValue,
                            adminManage))));
            y += 28;
        } else {
            addRenderableWidget(new VoiceButton(
                    cx, y, cw, 18,
                    Component.literal(isPublic ? "Public group" : "Private group"),
                    VoiceButton.Style.QUIET,
                    b -> {
                    }));
            y += 24;
        }

        if (showAllowed) {
            allowedBox = new EditBox(font, cx, y, cw, 20, Component.literal("Allowed players"));
            allowedBox.setMaxLength(256);
            allowedBox.setHint(Component.literal("Names, comma-separated"));
            allowedBox.setValue(allowedValue);
            addRenderableWidget(allowedBox);
            y += 32;
        }

        addRenderableWidget(new VoiceButton(
                cx, y, cw / 2 - 4, 22,
                Component.literal(editing == null ? "Create" : "Save"),
                VoiceButton.Style.PRIMARY,
                b -> submit()));
        addRenderableWidget(new VoiceButton(
                cx + cw / 2 + 4, y, cw / 2 - 4, 22,
                Component.literal("Cancel"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(backScreen())));
        addRenderableWidget(new VoiceButton(
                panelX + panelW - 22, panelY + 4, 16, 16,
                Component.literal("X"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(backScreen())));
    }

    private void submit() {
        if (busy) {
            return;
        }
        String name = nameBox.getValue().trim();
        if (name.isBlank()) {
            status = "Enter a group name.";
            return;
        }
        List<String> allowed = parseAllowed();
        SignalingClient signaling = TwoptwotVoiceClient.get().signaling();
        if (!signaling.isConnected() || signaling.sessionId() == null || signaling.sessionId().isBlank()) {
            status = "Not connected to voice.";
            return;
        }
        busy = true;
        if (editing != null) {
            status = "Saving…";
            if (adminManage) {
                JsonObject body = new JsonObject();
                body.addProperty("groupId", editing.id);
                body.addProperty("name", name);
                body.addProperty("isPublic", isPublic);
                if (!isPublic) {
                    JsonArray arr = new JsonArray();
                    for (String n : allowed) {
                        arr.add(n);
                    }
                    body.add("allowedNames", arr);
                }
                signaling.adminPost("/api/admin/groups/update", body, res -> {
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            signaling.refreshGroups();
                            minecraft.setScreen(backScreen());
                        });
                    }
                }, err -> {
                    busy = false;
                    status = "Save failed: " + err;
                });
                return;
            }
            signaling.updateGroup(editing.id, name, editing.isPublic ? List.of() : allowed, () -> {
                if (minecraft != null) {
                    minecraft.execute(() -> {
                        signaling.refreshGroups();
                        minecraft.setScreen(backScreen());
                    });
                }
            }, err -> {
                busy = false;
                status = "Save failed: " + err;
            });
            return;
        }
        createGroup(name, allowed);
    }

    private List<String> parseAllowed() {
        if (allowedBox == null) {
            return List.of();
        }
        String raw = allowedBox.getValue();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void createGroup(String name, List<String> allowed) {
        SignalingClient signaling = TwoptwotVoiceClient.get().signaling();
        String sessionId = signaling.sessionId();
        String apiBase = TwoptwotVoiceClient.get().controller().getApiBase();
        status = "Creating…";
        boolean pub = isPublic;
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("sessionId", sessionId);
                body.addProperty("name", name);
                body.addProperty("isPublic", pub);
                if (!pub && !allowed.isEmpty()) {
                    JsonArray arr = new JsonArray();
                    for (String n : allowed) {
                        arr.add(n);
                    }
                    body.add("allowedNames", arr);
                }
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiBase + "/api/groups/create"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    busy = false;
                    status = describeError(res.statusCode(), res.body());
                    return;
                }
                JsonElement parsed = JsonParser.parseString(res.body());
                SignalingClient.GroupInfo created = null;
                if (parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("group")) {
                    created = SignalingClient.GroupInfo.fromJson(
                            parsed.getAsJsonObject().getAsJsonObject("group"));
                }
                if (created != null) {
                    signaling.upsertGroup(created);
                }
                String groupId = created == null ? null : created.id;
                if (groupId == null || groupId.isBlank()) {
                    busy = false;
                    signaling.refreshGroups();
                    if (minecraft != null) {
                        minecraft.execute(() -> minecraft.setScreen(new VoiceScreen()));
                    }
                    return;
                }
                status = "Joining…";
                signaling.joinGroup(groupId, () -> {
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            TwoptwotVoiceClient.get().controller().setChannel("group:" + groupId, true);
                            signaling.refreshGroups();
                            minecraft.setScreen(backScreen());
                        });
                    }
                }, err -> {
                    busy = false;
                    status = "Created, join failed: " + err;
                    signaling.refreshGroups();
                    if (minecraft != null) {
                        minecraft.execute(() -> minecraft.setScreen(new VoiceScreen()));
                    }
                });
            } catch (Exception e) {
                busy = false;
                status = "Failed: " + e.getMessage();
            }
        });
    }

    private static String describeError(int code, String body) {
        if (body != null && body.contains("public_group_already_owned")) {
            return "You already own a public group.";
        }
        if (body != null && body.contains("invalid_group_name")) {
            return "Invalid group name.";
        }
        return "Failed HTTP " + code;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.dimWorld(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.panel(graphics, panelX, panelY, panelW, panelH);
        VoiceUi.accentBar(graphics, panelX + 1, panelY + 1, panelW - 2);
        graphics.drawString(font, editing == null ? "New Group" : "Edit Group",
                panelX + 14, panelY + 10, VoiceUi.TEXT, false);
        if (allowedBox != null) {
            graphics.drawString(font, "Allowed players (comma-separated)",
                    panelX + 20, allowedBox.getY() - 10, VoiceUi.TEXT_FAINT, false);
        }
        graphics.drawString(font, status, panelX + 14, panelY + panelH - 18, VoiceUi.TEXT_DIM, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
