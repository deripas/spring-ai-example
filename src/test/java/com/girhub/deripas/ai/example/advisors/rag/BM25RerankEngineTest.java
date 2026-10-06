package com.girhub.deripas.ai.example.advisors.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BM25RerankEngineTest {

    private final BM25RerankEngine engine = BM25RerankEngine.builder().build();

    @Test
    void nullCorpusReturnsEmptyStream() {
        assertThat(engine.rerank(null, "spring")).isEmpty();
    }

    @Test
    void emptyCorpusReturnsEmptyStream() {
        assertThat(engine.rerank(List.of(), "spring")).isEmpty();
    }

    @Test
    void keepsAllDocumentsAndOnlyReordersThem() {
        final List<Document> corpus = List.of(
                doc("a", "Погода сегодня солнечная"),
                doc("b", "Spring создаёт бины"),
                doc("c", "Кошка спит на диване"));

        assertThat(engine.rerank(corpus, "бины"))
                .containsExactlyInAnyOrderElementsOf(corpus);
    }

    @Test
    void documentWithQueryTermRanksAboveDocumentsWithout() {
        final List<Document> corpus = List.of(
                doc("weather", "Погода сегодня солнечная и тёплая"),
                doc("proxy", "Spring оборачивает бин в прокси"),
                doc("cat", "Кошка спит на диване весь день"));

        assertThat(ids(engine.rerank(corpus, "прокси")))
                .first().isEqualTo("proxy");
    }

    @Test
    void higherTermFrequencyRanksHigherForSameLength() {
        final List<Document> corpus = List.of(
                doc("once", "прокси контекст фабрика"),
                doc("twice", "прокси прокси фабрика"),
                doc("none", "конфигурация контекст фабрика"));

        assertThat(ids(engine.rerank(corpus, "прокси")))
                .containsExactly("twice", "once", "none");
    }

    @Test
    void rareTermOutweighsCommonTerm() {
        // "контекст" есть во всех документах, "прокси" только в одном
        final List<Document> corpus = List.of(
                doc("common", "контекст контекст фабрика"),
                doc("rare", "прокси фабрика конфигурация"),
                doc("other", "контекст фабрика конфигурация"));

        assertThat(ids(engine.rerank(corpus, "контекст прокси")))
                .first().isEqualTo("rare");
    }

    @Test
    void shorterDocumentRanksHigherForSameTermFrequency() {
        final List<Document> corpus = List.of(
                doc("long", "прокси фабрика конфигурация контекст аннотация рефлексия"),
                doc("short", "прокси фабрика"));

        assertThat(ids(engine.rerank(corpus, "прокси")))
                .containsExactly("short", "long");
    }

    @Test
    void withoutLengthNormalizationEqualTermFrequencyKeepsOriginalOrder() {
        final BM25RerankEngine noLengthNorm = BM25RerankEngine.builder().B(0.0).build();
        final List<Document> corpus = List.of(
                doc("long", "прокси фабрика конфигурация контекст аннотация рефлексия"),
                doc("short", "прокси фабрика"));

        assertThat(ids(noLengthNorm.rerank(corpus, "прокси")))
                .containsExactly("long", "short");
    }

    @Test
    void russianQueryMatchesOtherWordFormsViaStemming() {
        final List<Document> corpus = List.of(
                doc("other", "Кошка спит на диване весь день"),
                doc("beans", "Жизненный цикл бинов в контейнере"));

        assertThat(ids(engine.rerank(corpus, "бины")))
                .first().isEqualTo("beans");
    }

    @Test
    void englishQueryMatchesOtherWordFormsViaStemming() {
        final List<Document> corpus = List.of(
                doc("other", "The cat sleeps on the sofa all day"),
                doc("proxies", "Spring creates proxies around beans"));

        assertThat(ids(engine.rerank(corpus, "proxy")))
                .first().isEqualTo("proxies");
    }

    @Test
    void stopWordsDoNotAffectRankingSoOriginalOrderIsKept() {
        final List<Document> corpus = List.of(
                doc("a", "the cat and the dog"),
                doc("b", "the the the and and"),
                doc("c", "a bird"));

        assertThat(ids(engine.rerank(corpus, "the and a")))
                .containsExactly("a", "b", "c");
    }

    @Test
    void noMatchingTermsKeepsOriginalOrder() {
        final List<Document> corpus = List.of(
                doc("a", "Погода сегодня солнечная"),
                doc("b", "Кошка спит на диване"),
                doc("c", "Spring создаёт бины"));

        assertThat(ids(engine.rerank(corpus, "квантовая хромодинамика")))
                .containsExactly("a", "b", "c");
    }

    private static Document doc(String id, String text) {
        return Document.builder().id(id).text(text).build();
    }

    private static List<String> ids(java.util.stream.Stream<Document> documents) {
        return documents.map(Document::getId).toList();
    }
}
