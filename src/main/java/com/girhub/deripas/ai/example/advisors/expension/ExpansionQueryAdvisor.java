package com.girhub.deripas.ai.example.advisors.expension;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

@Builder
public class ExpansionQueryAdvisor implements BaseAdvisor {

    public static final String TEMPLATE_RESOURCE = "prompts/expansion-query.st";

    private static final PromptTemplate TEMPLATE = PromptTemplate.builder()
            .resource(new ClassPathResource(TEMPLATE_RESOURCE))
            .build();

    public static final String ENRICHED_QUESTION = "ENRICHED_QUESTION";
    public static final String ORIGINAL_QUESTION = "ORIGINAL_QUESTION";
    public static final String EXPANSION_RATIO = "EXPANSION_RATIO";

    private ChatClient chatClient;

    public static ExpansionQueryAdvisorBuilder builder(ChatModel chatModel) {
        return new ExpansionQueryAdvisorBuilder()
                .chatClient(ChatClient.builder(chatModel)
                        .defaultOptions(OllamaChatOptions.builder()
                                .temperature(0.0)
                                .topK(1)
                                .topP(0.1)
                                .repeatPenalty(1.0))
                        .build());
    }

    @Getter
    private final int order;

    @NonNull
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {
        final String userQuestion = String.valueOf(
                chatClientRequest.prompt()
                        .getUserMessage()
                        .getText()
        );
        final String enrichedQuestion = String.valueOf(
                chatClient
                        .prompt()
                        .user(TEMPLATE.render(Map.of("question", userQuestion)))
                        .call()
                        .content()
        );
        final double ratio = enrichedQuestion.length() / (double) userQuestion.length();

        return chatClientRequest.mutate()
                .context(ORIGINAL_QUESTION, userQuestion)
                .context(ENRICHED_QUESTION, enrichedQuestion)
                .context(EXPANSION_RATIO, ratio)
                .build();
    }

    @NonNull
    @Override
    public ChatClientResponse after(@NonNull ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
        return chatClientResponse;
    }

}
