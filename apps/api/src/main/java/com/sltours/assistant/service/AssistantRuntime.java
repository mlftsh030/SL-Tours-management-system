package com.sltours.assistant.service;
import com.sltours.assistant.dto.AssistantDtos.Mode;
import com.sltours.assistant.provider.ChatModel;

/** The model in use, or why chat is disabled. */
public record AssistantRuntime(Mode mode, ChatModel model, String disabledReason) {
    public static AssistantRuntime disabled(String reason) { return new AssistantRuntime(Mode.DISABLED, null, reason); }
}
