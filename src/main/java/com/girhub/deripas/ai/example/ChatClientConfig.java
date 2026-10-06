package com.girhub.deripas.ai.example;

import com.girhub.deripas.ai.example.advisors.expension.ExpansionQueryAdvisor;
import com.girhub.deripas.ai.example.advisors.rag.RagAdvisor;
import com.girhub.deripas.ai.example.repo.ChatEntryRepository;
import com.girhub.deripas.ai.example.services.PostgresChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    private static final PromptTemplate SYSTEM_PROMPT = new PromptTemplate(
            """
                    Ты — Евгений Борисов, Java-разработчик и эксперт по Spring. Отвечай от первого лица, кратко и по делу.
                    
                    Вопрос может быть о СЛЕДСТВИИ факта из Context.
                    ВСЕГДА связывай: факт Context → вопрос.
                    
                    Нет связи, даже косвенной = "я не говорил об этом в докладах".
                    Есть связь = отвечай.
                    """
    );

    private final ChatEntryRepository chatEntryRepository;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultAdvisors(
                        expansionQueryAdvisor(0),
                        historyAdvisor(1),
                        simpleLoggerAdvisor(2),
                        ragAdvisor(3),
                        simpleLoggerAdvisor(4)
                )
                .defaultOptions(
                        OllamaChatOptions.builder()
                                .temperature(0.3)
                                .topP(0.7)
                                .topK(20)
                                .repeatPenalty(1.1)
                )
                .defaultSystem(SYSTEM_PROMPT.render())
                .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return PostgresChatMemory.builder()
                .maxMessages(2)
                .chatEntryRepository(chatEntryRepository)
                .build();
    }

    public ExpansionQueryAdvisor expansionQueryAdvisor(int order) {
        return ExpansionQueryAdvisor.builder(chatModel)
                .order(order)
                .build();
    }

    public MessageChatMemoryAdvisor historyAdvisor(int order) {
        return MessageChatMemoryAdvisor.builder(chatMemory())
                .order(order)
                .build();
    }

    public RagAdvisor ragAdvisor(int order) {
        return RagAdvisor.builder(vectorStore)
                .order(order)
                .build();
    }

    public SimpleLoggerAdvisor simpleLoggerAdvisor(int order) {
        return SimpleLoggerAdvisor.builder()
                .order(order)
                .build();
    }
}
