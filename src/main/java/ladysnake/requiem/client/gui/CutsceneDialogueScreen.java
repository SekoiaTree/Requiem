/*
 * Requiem
 * Copyright (C) 2017-2020 Ladysnake
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 */
package ladysnake.requiem.client.gui;

import com.google.common.collect.ImmutableList;
import ladysnake.requiem.api.v1.RequiemPlayer;
import ladysnake.requiem.api.v1.annotation.Unlocalized;
import ladysnake.requiem.api.v1.dialogue.ChoiceResult;
import ladysnake.requiem.api.v1.dialogue.CutsceneDialogue;
import ladysnake.requiem.client.ZaWorldFx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.lwjgl.glfw.GLFW;

public class CutsceneDialogueScreen extends Screen {
    public static final int MIN_RENDER_Y = 40;
    public static final int TITLE_GAP = 20;
    public static final int CHOICE_GAP = 5;
    private final CutsceneDialogue dialogue;
    private int selectedChoice;
    private boolean hoveringChoice;
    public static final int MAX_TEXT_WIDTH = 300;

    public CutsceneDialogueScreen(Text title, CutsceneDialogue dialogue) {
        super(title);
        this.dialogue = dialogue;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hoveringChoice) {
            confirmChoice(selectedChoice);
        }
        return true;
    }

    private ChoiceResult confirmChoice(int selectedChoice) {
        assert minecraft != null;
        ChoiceResult result = this.dialogue.choose(this.dialogue.getCurrentChoices().get(selectedChoice));
        if (result == ChoiceResult.END_DIALOGUE) {
            this.minecraft.openScreen(null);
            RequiemPlayer player = (RequiemPlayer) this.minecraft.player;
            assert player != null;
            player.getDialogueTracker().endDialogue();
            player.getDeathSuspender().setLifeTransient(false);
        } else if (result == ChoiceResult.ASK_CONFIRMATION) {
            ImmutableList<String> choices = this.dialogue.getCurrentChoices();
            this.minecraft.openScreen(new ConfirmScreen(
                    this::onBigChoiceMade,
                    new TranslatableText(this.dialogue.getCurrentText()),
                    new LiteralText(""),
                    I18n.translate(choices.get(0)),
                    I18n.translate(choices.get(1))
            ));
        } else {
            this.selectedChoice = 0;
        }
        return result;
    }

    private void onBigChoiceMade(boolean yes) {
        assert minecraft != null;
        if (this.confirmChoice(yes ? 0 : 1) == ChoiceResult.DEFAULT) {
            this.minecraft.openScreen(this);
        }
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        GameOptions options = MinecraftClient.getInstance().options;
        if (key == GLFW.GLFW_KEY_ENTER || options.keyInventory.matchesKey(key, scancode)) {
            confirmChoice(selectedChoice);
            return true;
        }
        boolean tab = GLFW.GLFW_KEY_TAB == key;
        boolean down = options.keyBack.matchesKey(key, scancode);
        boolean shift = (GLFW.GLFW_MOD_SHIFT & modifiers) != 0;
        if (tab || down || options.keyForward.matchesKey(key, scancode)) {
            scrollDialogueChoice(tab && !shift || down ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
        this.scrollDialogueChoice(scrollAmount);
        return true;
    }

    private void scrollDialogueChoice(double scrollAmount) {
        this.selectedChoice = Math.floorMod((int) (this.selectedChoice - scrollAmount), this.dialogue.getCurrentChoices().size());
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        ImmutableList<@Unlocalized String> choices = this.dialogue.getCurrentChoices();
        String title = I18n.translate(this.dialogue.getCurrentText());
        int y = MIN_RENDER_Y + this.font.getStringBoundedHeight(title, MAX_TEXT_WIDTH) + TITLE_GAP;
        for (int i = 0; i < choices.size(); i++) {
            String choice = I18n.translate(choices.get(i));
            int strHeight = this.font.getStringBoundedHeight(choice, width);
            int strWidth = strHeight == 9 ? this.font.getStringWidth(choice) : width;
            if (mouseX < strWidth && mouseY > y && mouseY < y + strHeight) {
                this.selectedChoice = i;
                this.hoveringChoice = true;
                return;
            }
            y += strHeight + CHOICE_GAP;
            this.hoveringChoice = false;
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        assert minecraft != null;
        if (!ZaWorldFx.INSTANCE.hasFinishedAnimation()) {
            return;
        }
        this.renderBackground();
        int y = MIN_RENDER_Y;
        String title = I18n.translate(this.dialogue.getCurrentText());
        this.font.drawTrimmed(title, 10, y, MAX_TEXT_WIDTH, 0xFFFFFF);
        y += this.font.getStringBoundedHeight(title, MAX_TEXT_WIDTH) + TITLE_GAP;
        ImmutableList<String> choices = this.dialogue.getCurrentChoices();
        for (int i = 0; i < choices.size(); i++) {
            String choice = I18n.translate(choices.get(i));
            int strHeight = this.font.getStringBoundedHeight(choice, MAX_TEXT_WIDTH);
            this.font.drawTrimmed(choice, 10, y, MAX_TEXT_WIDTH, i == this.selectedChoice ? 0xE0E044 : 0xA0A0A0);
            y += strHeight + CHOICE_GAP;
        }
        String tip = I18n.translate("requiem:dialogue.instructions", minecraft.options.keyForward.getLocalizedName().toUpperCase(), minecraft.options.keyBack.getLocalizedName().toUpperCase(), minecraft.options.keyInventory.getLocalizedName().toUpperCase());
        this.font.draw(tip, (this.width - font.getStringWidth(tip)) * 0.5f, this.height - 30, 0x808080);
        super.render(mouseX, mouseY, tickDelta);
    }
}
