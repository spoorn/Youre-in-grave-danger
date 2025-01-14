package com.b1n4ry.yigd.client.gui;

import com.b1n4ry.yigd.Yigd;
import com.b1n4ry.yigd.api.YigdApi;
import com.b1n4ry.yigd.core.DeadPlayerData;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GraveSelectScreen extends Screen {
    private final Identifier GRAVE_SELECT_TEXTURE = new Identifier("yigd", "textures/gui/select_menu.png");
    private final Identifier SELECT_ELEMENT_TEXTURE = new Identifier("yigd", "textures/gui/select_elements.png");

    private final List<DeadPlayerData> data;
    private final List<GuiGraveInfo> graveInfo;
    private final int page;

    private final Screen previousScreen;

    private final GameProfile graveOwner;

    private boolean mouseIsClicked = false;
    private String hoveredElement = null;

    public GraveSelectScreen(List<DeadPlayerData> data, int page, Screen previousScreen) {
        super(Text.of("Grave Select"));
        List<GuiGraveInfo> info = new ArrayList<>();
        for (DeadPlayerData deadData : data) {
            int size = 0;
            for (ItemStack stack : deadData.inventory) {
                if (!stack.isEmpty()) size++;
            }
            for (int i = 0; i < deadData.modInventories.size(); i++) {
                YigdApi yigdApi = Yigd.apiMods.get(i);
                size += yigdApi.getInventorySize(deadData.modInventories.get(i));
            }

            int points = deadData.xp;
            int i;
            for (i = 0; points >= 0; i++) {
                if (i < 16) points -= (2 * i) + 7;
                else if (i < 31) points -= (5 * i) - 38;
                else points -= (9 * i) - 158;
            }

            info.add(new GuiGraveInfo(deadData, size, i - 1));
        }

        this.data = data;
        this.graveInfo = info;
        this.page = page;
        this.previousScreen = previousScreen;

        if (data.size() > 0) {
            this.graveOwner = data.get(0).graveOwner;
        } else {
            this.graveOwner = null;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.keyInventory.matchesKey(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (previousScreen == null) {
                this.onClose();
                return true;
            }
            if (client != null) {
                client.setScreen(previousScreen);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredElement != null) {
            mouseIsClicked = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseIsClicked = false;
        if (button == 0 && hoveredElement != null && client != null) {
            if (hoveredElement.equals("left") && page > 1) {
                GraveSelectScreen screen = new GraveSelectScreen(data, page - 1, this.previousScreen);
                client.setScreen(screen);
            } else if (hoveredElement.equals("right") && graveInfo.size() > page * 4) {
                GraveSelectScreen screen = new GraveSelectScreen(data, page + 1, this.previousScreen);
                client.setScreen(screen);
            } else if (isInt(hoveredElement)) {
                int parsedString = Integer.parseInt(hoveredElement) - 1;
                if (data.size() > parsedString && parsedString >= 0) {
                    GraveViewScreen screen = new GraveViewScreen(data.get(parsedString), this);
                    client.setScreen(screen);
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        final int screenWidth = 220;
        final int screenHeight = 200;
        final int originX = this.width / 2;
        final int originY = this.height / 2;

        final int screenLeft = originX - screenWidth / 2;
        final int screenTop = originY - screenHeight / 2;

        hoveredElement = null;

        RenderSystem.setShaderTexture(0, GRAVE_SELECT_TEXTURE);
        drawTexture(matrices, screenLeft, screenTop, 0, 0, screenWidth, screenHeight);

        RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE);
        if (mouseX > screenLeft + 6 && mouseX < screenLeft + 14 && mouseY > originY - 8 && mouseY < originY + 7) {
            hoveredElement = "left";
        } else if (mouseX > screenLeft + screenWidth - 14 && mouseX < screenLeft + screenWidth - 6 && mouseY > originY - 8 && mouseY < originY + 7) {
            hoveredElement = "right";
        }
        if (hoveredElement != null && hoveredElement.equals("left") && mouseIsClicked) {
            drawTexture(matrices, screenLeft + 6, originY - 8, 16, 84, 8, 15);
        } else {
            drawTexture(matrices, screenLeft + 6, originY - 8, 0, 84, 8, 15);
        }
        if (hoveredElement != null && hoveredElement.equals("right") && mouseIsClicked) {
            drawTexture(matrices, screenLeft + screenWidth - 14, originY - 8, 24, 84, 8, 15);
        } else {
            drawTexture(matrices, screenLeft + screenWidth - 14, originY - 8, 8, 84, 8, 15);
        }

        int infoSize = this.graveInfo.size();
        int startValue = infoSize - (page - 1) * 4;
        int whileMoreThan = Math.max(startValue - 4, 0);
        int iterations = 0;
        for (int i = startValue; i > whileMoreThan; i--) {
            RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE); // If not present, gui bugs out
            int left = screenLeft + 19;
            int top = screenTop + 24 + 42 * iterations;
            int width = screenWidth - 19 * 2;
            int height = 42;
            if (mouseX > left && mouseX < left + width && mouseY > top && mouseY < top + height) {
                hoveredElement = "" + i;
            }
            if (isInt(hoveredElement) && Integer.parseInt(hoveredElement) == i && mouseIsClicked) {
                drawTexture(matrices, left, top, 0, height, width, height);
            } else {
                drawTexture(matrices, left, top, 0, 0, width, height);
            }

            GuiGraveInfo info = this.graveInfo.get(i - 1);
            textRenderer.draw(matrices, info.data.gravePos.getX() + " " + info.data.gravePos.getY() + " " + info.data.gravePos.getZ() + " " + info.data.dimensionName, left + 5f, top + 5f, 0xCC00CC);
            textRenderer.draw(matrices, info.itemSize + " items", left + 5f, top + 17f, 0x0000CC);
            textRenderer.draw(matrices, info.xpLevels + " levels", left + 5f, top + 29f, 0x299608);
            iterations++;
        }

        super.render(matrices, mouseX, mouseY, delta);

        int firstElement = (page - 1) * 4 + 1;
        String gravesDisplayed = firstElement + "-" + (firstElement + Math.min(3, infoSize - firstElement)) + "/" + infoSize;
        textRenderer.draw(matrices, "Graves of " + graveOwner.getName(), screenLeft + 19f, screenTop + 10f, 0x555555);

        int offset = textRenderer.getWidth(gravesDisplayed);
        textRenderer.draw(matrices, gravesDisplayed, screenLeft + screenWidth - 19f - offset, screenTop + 10f, 0x007700);
    }

    private boolean isInt(String intString) {
        if (intString == null) return false;
        try {
            Integer.parseInt(intString);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private record GuiGraveInfo(DeadPlayerData data, int itemSize, int xpLevels) { }
}
