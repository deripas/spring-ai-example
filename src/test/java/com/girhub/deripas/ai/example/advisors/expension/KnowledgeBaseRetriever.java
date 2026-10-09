package com.girhub.deripas.ai.example.advisors.expension;

import com.girhub.deripas.ai.example.advisors.rag.BM25RerankEngine;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Поиск по базе знаний в памяти, повторяющий путь приложения без Postgres:
 * чанки как в DocumentLoaderService, затем similarity search + BM25 rerank как в RagAdvisor.
 * При изменении параметров в приложении - обновить константы здесь.
 */
class KnowledgeBaseRetriever {

    // DocumentLoaderService
    private static final String KNOWLEDGE_BASE = "classpath:/knowledgebase/**/*.txt";
    private static final int CHUNK_SIZE = 200;

    // RagAdvisor
    private static final int TOP_K = 10;
    private static final double SIMILARITY_THRESHOLD = 0.62;
    private static final int SEARCH_SCALE_FACTOR = 2;

    private final EmbeddingModel embeddingModel;
    private final BM25RerankEngine rerankEngine = BM25RerankEngine.builder().build();
    private final List<Document> chunks;
    private final List<float[]> vectors;

    KnowledgeBaseRetriever(EmbeddingModel embeddingModel) throws IOException {
        this.embeddingModel = embeddingModel;
        final TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .build();
        this.chunks = Arrays.stream(new PathMatchingResourcePatternResolver().getResources(KNOWLEDGE_BASE))
                .flatMap(resource -> splitter.apply(new TextReader(resource).get()).stream())
                .toList();
        this.vectors = embeddingModel.embed(chunks.stream().map(Document::getText).toList());
    }

    int chunkCount() {
        return chunks.size();
    }

    /** Чанк, в котором лежит ответ на вопрос. */
    Optional<Document> findChunk(String phrase) {
        return chunks.stream()
                .filter(chunk -> chunk.getText().contains(phrase))
                .findFirst();
    }

    Retrieval retrieve(String query, Document target) {
        final float[] queryVector = embeddingModel.embed(query);
        final List<Scored> ranked = IntStream.range(0, chunks.size())
                .mapToObj(i -> new Scored(chunks.get(i), cosine(queryVector, vectors.get(i))))
                .sorted(Comparator.comparingDouble(Scored::similarity).reversed())
                .toList();

        final List<Document> candidates = ranked.stream()
                .filter(scored -> scored.similarity() >= SIMILARITY_THRESHOLD)
                .limit((long) TOP_K * SEARCH_SCALE_FACTOR)
                .map(Scored::document)
                .toList();
        final List<Document> llmContext = rerankEngine.rerank(candidates, query)
                .limit(TOP_K)
                .toList();

        final int vectorRank = ranked.stream().map(Scored::document).toList().indexOf(target) + 1;
        final double similarity = ranked.get(vectorRank - 1).similarity();
        return new Retrieval(similarity, vectorRank, llmContext.indexOf(target) + 1);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / Math.sqrt(normA * normB);
    }

    private record Scored(Document document, double similarity) {
    }

    /**
     * @param similarity    косинусная близость запроса к чанку с ответом
     * @param vectorRank    место чанка среди всех чанков по близости (1 - лучший)
     * @param contextPosition место чанка в контексте LLM после порога и BM25; 0 - не попал
     */
    record Retrieval(double similarity, int vectorRank, int contextPosition) {

        boolean inContext() {
            return contextPosition > 0;
        }
    }
}
