package com.girhub.deripas.ai.example.advisors.rag;


import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Builder
public class BM25RerankEngine {

    @Builder.Default
    private final LanguageDetector languageDetector = LanguageDetectorBuilder
            .fromLanguages(Language.ENGLISH, Language.RUSSIAN)
            .build();

    @Builder.Default
    private final double K = 1.2;
    @Builder.Default
    private final double B = 0.75;

    public Stream<Document> rerank(List<Document> corpus, String query) {
        if (corpus == null || corpus.isEmpty()) {
            return Stream.empty();
        }

        // Compute corpus statistics
        final CorpusStats stats = computeCorpusStats(corpus);
        final ScoreCalcs calc = createCalcs(stats);

        // Tokenize query
        final List<String> queryTokens = tokenize(query);

        // Score and sort documents
        return corpus.stream()
                .sorted((d1, d2) -> Double.compare(
                        calc.get(d2).score(queryTokens),
                        calc.get(d1).score(queryTokens)
                ));
    }

    private CorpusStats computeCorpusStats(List<Document> corpus) {
        final Map<Document, List<String>> tokenizedDocs = tokenize(corpus);
        final AtomicInteger tokensGlobalCount = new AtomicInteger(0);
        final Map<String, Integer> tokensGlobalFreq = tokenizedDocs.values()
                .stream()
                .flatMap(tokens -> tokens.stream()
                        .peek(token -> tokensGlobalCount.incrementAndGet())
                        .distinct())
                .collect(Collectors.toMap(Function.identity(), item -> 1, Integer::sum));
        final GlobalStat stat = new GlobalStat(
                new TokensStat(tokensGlobalFreq, tokensGlobalCount.get()),
                tokenizedDocs.size(),
                (double) tokensGlobalCount.get() / tokenizedDocs.size()
        );
        final Map<Document, DocumentStat> statMap = tokenizedDocs.entrySet()
                .stream()
                .map(entry -> Map.entry(entry.getKey(), DocumentStat.of(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new CorpusStats(stat, statMap);
    }

    private ScoreCalcs createCalcs(CorpusStats stats) {
        final GlobalStat statGlobal = stats.statGlobal;
        final DocumentStat empty = DocumentStat.of(Collections.emptyList());
        final Map<Document, ScoreCalc> calcMap = stats.statByDocument.entrySet()
                .stream()
                .map(entry -> Map.entry(entry.getKey(), new ScoreCalc(entry.getValue(), statGlobal)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ScoreCalcs(calcMap, new ScoreCalc(empty, statGlobal));
    }

    private Map<Document, List<String>> tokenize(List<Document> documents) {
        return documents.stream()
                .collect(Collectors.toMap(Function.identity(), this::tokenize));
    }

    private List<String> tokenize(Document document) {
        return tokenize(document.getText());
    }

    private List<String> tokenize(String text) {
        final List<String> tokens = new ArrayList<>();
        try (Analyzer analyzer = detectLanguageAnalyzer(text)) {
            try (TokenStream stream = analyzer.tokenStream(null, text)) {
                stream.reset();
                while (stream.incrementToken()) {
                    tokens.add(stream.getAttribute(CharTermAttribute.class).toString());
                }
                stream.end();
            } catch (IOException e) {
                throw new RuntimeException("Tokenization failed", e);
            }
        }
        return tokens;
    }

    private Analyzer detectLanguageAnalyzer(String text) {
        final Language lang = languageDetector.detectLanguageOf(text);
        if (lang == Language.RUSSIAN) {
            return new RussianAnalyzer();
        }

        // Fallback to English analyzer for unsupported languages
        return new EnglishAnalyzer();
    }

    // Inner class to hold corpus statistics
    private record CorpusStats(
            GlobalStat statGlobal,
            Map<Document, DocumentStat> statByDocument
    ) {
    }

    private record TokensStat(
            Map<String, Integer> freq,
            int count
    ) {
        public static TokensStat of(List<String> tokens) {
            return new TokensStat(freq(tokens), tokens.size());
        }

        private static <T> Map<T, Integer> freq(List<T> items) {
            return items.stream()
                    .collect(Collectors.toMap(Function.identity(), item -> 1, Integer::sum));
        }
    }

    private record DocumentStat(TokensStat tokensStat) {

        public static DocumentStat of(List<String> tokens) {
            return new DocumentStat(TokensStat.of(tokens));
        }

        public int tokenFreq(String token) {
            return tokensStat.freq.getOrDefault(token, 0);
        }

        public double tokensCount() {
            return tokensStat.count;
        }
    }

    private record GlobalStat(TokensStat tokensStat, int documentsCount, double tokensCountAvg) {
        public int tokenFreq(String token) {
            return tokensStat.freq.getOrDefault(token, 1);
        }

        public double tokensCount() {
            return tokensStat.count;
        }
    }

    @RequiredArgsConstructor
    private class ScoreCalc {
        private final DocumentStat document;
        private final GlobalStat global;

        public double score(List<String> tokens) {
            return tokens.stream()
                    .mapToDouble(this::score)
                    .sum();
        }

        public double score(String token) {
            final int tf = document.tokenFreq(token);
            final int df = global.tokenFreq(token);

            // BM25 IDF calculation редкость слова - оно поднимает
            final double idf = Math.log(1 + (global.documentsCount() - df + 0.5) / (df + 0.5));

            // BM25 term score calculation
            final double numerator = tf * (K + 1);
            final double denominator = tf + K * (1 - B + B * document.tokensCount() / global.tokensCountAvg());
            return idf * (numerator / denominator);
        }
    }

    @RequiredArgsConstructor
    private class ScoreCalcs {

        private final Map<Document, ScoreCalc> calcMap;
        private final ScoreCalc defaultScoreCalc;

        public ScoreCalc get(Document document) {
            return calcMap.getOrDefault(document, defaultScoreCalc);
        }
    }
}