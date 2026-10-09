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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

@Builder
public class ExpansionQueryAdvisor implements BaseAdvisor {

    public static final String ENRICHED_QUESTION = "ENRICHED_QUESTION";
    public static final String ORIGINAL_QUESTION = "ORIGINAL_QUESTION";
    public static final String EXPANSION_RATIO = "EXPANSION_RATIO";

    public static final String TEMPLATE_RESOURCE = "prompts/expansion-query.st";

    private static final PromptTemplate TEMPLATE = PromptTemplate.builder()
            .resource(new ClassPathResource(TEMPLATE_RESOURCE))
            .build();

    /** Запас на 2-5 дописанных слов: по замерам добавка занимает до ~25 токенов. */
    private static final int EXPANSION_TOKENS = 32;

    /**
     * Оценка по cl100k. Точного токенизатора модели нет, но для русского текста cl100k
     * даёт оценку сверху: у gemma3, qwen3, T-lite, YandexGPT, llama3.1 токенов в 0.7-0.9 раза меньше.
     */
    private static final TokenCountEstimator TOKEN_COUNT_ESTIMATOR = new JTokkitTokenCountEstimator();

    private ChatClient chatClient;

    private ChatOptions.Builder<?> options;

    public static ExpansionQueryAdvisorBuilder builder(ChatModel chatModel) {
        return builder(chatModel, defaultOptions());
    }

    public static ExpansionQueryAdvisorBuilder builder(ChatModel chatModel, ChatOptions.Builder<?> options) {
        return new ExpansionQueryAdvisorBuilder()
                .chatClient(ChatClient.builder(chatModel).build())
                .options(options);
    }

    public static OllamaChatOptions.Builder defaultOptions() {
        return OllamaChatOptions.builder()
                .temperature(0.0)
                .topK(1)
                .topP(0.1)
                .repeatPenalty(1.0)
                .disableThinking();
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
                        .options(options.clone().maxTokens(maxTokens(userQuestion)))
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

    static int maxTokens(String userQuestion) {
        return TOKEN_COUNT_ESTIMATOR.estimate(userQuestion) + EXPANSION_TOKENS;
    }

}
