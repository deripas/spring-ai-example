package com.girhub.deripas.ai.example.advisors.rag;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.girhub.deripas.ai.example.advisors.expension.ExpansionQueryAdvisor.ENRICHED_QUESTION;
import static java.util.Objects.requireNonNull;


@Builder
public class RagAdvisor implements BaseAdvisor {

    @Builder.Default
    private final PromptTemplate promptTemplate = PromptTemplate.builder()
            .template("""
                    CONTEXT: {context}
                    Question: {question}
                    """)
            .build();


    @Builder.Default
    private final SearchRequest searchRequest = SearchRequest.builder()
            .topK(3)
            .similarityThreshold(0.62)
            .build();

    @Builder.Default
    private final BM25RerankEngine rerankEngine = BM25RerankEngine.builder()
            .build();

    @Getter
    private final int order;

    private final int searchScaleFactor = 2;

    private final VectorStore vectorStore;

    public static RagAdvisorBuilder builder(VectorStore vectorStore) {
        return new RagAdvisorBuilder().vectorStore(vectorStore);
    }

    @NonNull
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {
        final String originalUserQuestion = requireNonNull(
                chatClientRequest.prompt()
                        .getUserMessage()
                        .getText()
        );
        final String queryToRag = String.valueOf(
                chatClientRequest.context()
                        .getOrDefault(ENRICHED_QUESTION, originalUserQuestion)
        );
        final SearchRequest request = SearchRequest.from(searchRequest)
                .query(queryToRag)
                .topK(searchRequest.getTopK() * searchScaleFactor)
                .build();
        final List<Document> documents = vectorStore.similaritySearch(request);
        if (documents.isEmpty()) {
            return chatClientRequest.mutate()
                    .context("CONTEXT", "ТУТ ПУСТО - ни один документ моя собачка не обнаружила")
                    .build();
        }

        final String llmContext = rerankEngine.rerank(documents, queryToRag)
                .limit(searchRequest.getTopK())
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));

        final String finalUserPrompt = promptTemplate.render(
                Map.of(
                        "context", llmContext,
                        "question", originalUserQuestion
                )
        );
        final Prompt prompt = chatClientRequest.prompt()
                .augmentUserMessage(finalUserPrompt);
        return chatClientRequest.mutate()
                .prompt(prompt)
                .build();
    }

    @NonNull
    @Override
    public ChatClientResponse after(@NonNull ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
        return chatClientResponse;
    }
}
