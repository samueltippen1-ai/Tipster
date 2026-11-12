package dev.randomised.dispensertest;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Single place for player-facing messages (easy to change if the chat API shifts between versions). */
public final class Feedback {

    private Feedback() {}

    public static void send(String message) {
        if (!RdtConfig.get().chatFeedback) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().addMessage(Component.literal("[RDT] " + message));
    }
}
