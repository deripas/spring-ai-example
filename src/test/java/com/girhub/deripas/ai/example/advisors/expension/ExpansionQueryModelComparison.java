package com.girhub.deripas.ai.example.advisors.expension;

import com.girhub.deripas.ai.example.advisors.expension.KnowledgeBaseRetriever.Retrieval;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.ThinkOption;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Исследовательский запускатор (не тест): прогоняет набор контрольных вопросов через
 * ExpansionQueryAdvisor на разных моделях и параметрах Ollama.
 * <p>
 * Для каждого ответа:
 * <ul>
 *     <li>время ответа;</li>
 *     <li>автопроверки: вопрос сохранён, формат одной строкой, сколько слов добавлено,
 *     нет Spring-терминов в вопросах не про Spring, нет отказа;</li>
 *     <li>поиск по базе знаний как в приложении: близость к чанку с ответом, место среди чанков
 *     и попал ли чанк в контекст LLM. Для сравнения - те же метрики без расширения.</li>
 * </ul>
 * Запуск: main() из IDE. Нужен локальный Ollama с перечисленными моделями ({@code ollama list}).
 * Промпт - {@link ExpansionQueryAdvisor#TEMPLATE_RESOURCE}, тот же, что в приложении.
 */
public class ExpansionQueryModelComparison {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    /**
     * Как spring.ai.ollama.embedding.model в приложении (по умолчанию mxbai-embed-large).
     */
    private static final String EMBEDDING_MODEL = "mxbai-embed-large";

    /**
     * Варианты для сравнения. Одна модель может идти с разными параметрами:
     * app(...) - ровно как в приложении, остальные - рекомендации из карточек моделей.
     * Рассуждения выключены везде, кроме qwenThinking, чтобы сравнивать модели в одном режиме.
     */
    private static final List<OllamaChatOptions> VARIANTS = List.of(
            app("gemma3:4b-it-q4_K_M"),
            gemma("gemma3:4b-it-q4_K_M"),
            app("gemma3:12b"),
            gemma("gemma3:12b"),
            app("gemma4:e4b-it-q4_K_M"),
            gemma("gemma4:e4b-it-q4_K_M"),
            app("qwen2.5:7b"),
            qwen25("qwen2.5:7b"),
            app("qwen3:4b-instruct-2507-q4_K_M"),
            qwenNoThinking("qwen3:4b-instruct-2507-q4_K_M"),
            app("qwen3:8b"),
            qwenNoThinking("qwen3:8b"),
            qwenThinking("qwen3:8b"),
            app("t-tech/T-lite-it-2.1:q4_K_M"),
            qwenNoThinking("t-tech/T-lite-it-2.1:q4_K_M"),
            app("yandex/YandexGPT-5-Lite-8B-instruct-GGUF:latest"),
            app("llama3.1:8b"),
            llama("llama3.1:8b")
    );

    private static final List<Case> CASES = List.of(
            // о себе - Spring-термины здесь лишние
            personal("на что у тебя алергия", "аллергии на весну"),
            personal("почему ты ушел из компании", "ушёл в свободные художники"),
            personal("чем ты сейчас занимаешься", "сейчас я пишу курсы"),
            personal("где ты живешь", "я живу в Израиле"),
            personal("что ты имеешь в виду под словом штрудель", "штрудель"),
            personal("на каких конференциях ты выступаешь", "JPoint (Москва"),
            personal("какое у тебя прозвище", "Неофициальное прозвище"),
            personal("с какого года ты программируешь", "Java с 2001 года"),
            // про Spring
            spring("что такое BeanPostProcessor", "Зачем нужен BeanPostProcessor"),
            spring("какой паттерн используется в BeanPostProcessor", "Chain of Responsibility"),
            spring("чем CGLib отличается от динамического прокси", "наследование от класса"),
            spring("зачем два прохода через BeanPostProcessor", "два прохода через BeanPostProcessor"),
            spring("почему транзакции не работают в PostConstruct", "транзакции на этапе работы @PostConstruct"),
            spring("когда создаются singleton и prototype бины", "все singleton сразу создаются"),
            spring("сколько видов контекста в спринге", "4 вида контекста"),
            spring("что такое двухфазный конструктор", "двухфазный конструктор"),
            spring("какой твой любимый интерфейс", "Любимый интерфейс"),
            spring("что случилось 26 ноября 2003 года", "26 ноября 2003"),
            // ответа в базе нет - проверяем только поведение модели
            personal("какая погода в Москве", null),
            personal("как приготовить борщ", null)
    );

    /**
     * Повторы каждого вопроса - имеет смысл при temperature > 0.
     */
    private static final int RUNS_PER_CASE = 1;

    /**
     * Правило шаблона "от 2 до 5 слов" с небольшим запасом.
     */
    private static final int MAX_ADDED_WORDS = 7;

    private static final int ANSWER_PREVIEW_LENGTH = 110;

    // ---------- параметры моделей ----------

    /**
     * Как в приложении: ExpansionQueryAdvisor.defaultOptions() - жадное декодирование, без рассуждений.
     */
    private static OllamaChatOptions app(String model) {
        return ExpansionQueryAdvisor.defaultOptions().model(model).build();
    }

    /**
     * Рекомендации Google для Gemma 3. Для gemma4 взяты те же - сверьте с карточкой модели.
     */
    private static OllamaChatOptions gemma(String model) {
        return OllamaChatOptions.builder().model(model)
                .temperature(1.0).topK(64).topP(0.95)
                .disableThinking()
                .build();
    }

    /**
     * Рекомендации Qwen3 для режима без рассуждений (и для Qwen3-Instruct-2507).
     */
    private static OllamaChatOptions qwenNoThinking(String model) {
        return OllamaChatOptions.builder().model(model)
                .temperature(0.7).topK(20).topP(0.8)
                .disableThinking()
                .build();
    }

    /**
     * Рекомендации Qwen3 для режима с рассуждениями.
     */
    private static OllamaChatOptions qwenThinking(String model) {
        return OllamaChatOptions.builder().model(model)
                .temperature(0.6).topK(20).topP(0.95)
                .enableThinking()
                .build();
    }

    /**
     * Рекомендации Qwen2.5-Instruct.
     */
    private static OllamaChatOptions qwen25(String model) {
        return OllamaChatOptions.builder().model(model)
                .temperature(0.7).topK(20).topP(0.8).repeatPenalty(1.05)
                .disableThinking()
                .build();
    }

    /**
     * Рекомендации Meta для Llama 3.1.
     */
    private static OllamaChatOptions llama(String model) {
        return OllamaChatOptions.builder().model(model)
                .temperature(0.6).topP(0.9)
                .disableThinking()
                .build();
    }

    // ---------- запуск ----------

    public static void main(String[] args) throws Exception {
        final OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .build();
        final KnowledgeBaseRetriever retriever = new KnowledgeBaseRetriever(OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaEmbeddingOptions.builder().model(EMBEDDING_MODEL).build())
                .build());
        System.out.printf("База знаний: %d чанков, вопросов: %d, вариантов: %d%n%n",
                retriever.chunkCount(), CASES.size(), VARIANTS.size());

        final Map<Case, Document> targets = findTargets(retriever);
        final Map<Case, Retrieval> baseline = baseline(retriever, targets);

        final List<VariantResult> results = new ArrayList<>();
        for (OllamaChatOptions options : VARIANTS) {
            final VariantResult result = run(ollamaApi, options, retriever, targets, baseline);
            results.add(result);
            print(result, baseline);
        }

        printSummary(results, baseline);
    }

    private static Map<Case, Document> findTargets(KnowledgeBaseRetriever retriever) {
        final Map<Case, Document> targets = new LinkedHashMap<>();
        for (Case c : CASES) {
            if (c.expectedPhrase() == null) {
                continue;
            }
            retriever.findChunk(c.expectedPhrase()).ifPresentOrElse(
                    chunk -> targets.put(c, chunk),
                    () -> System.out.printf("ВНИМАНИЕ: фраза \"%s\" не найдена ни в одном чанке - метрики поиска для \"%s\" не считаются%n",
                            c.expectedPhrase(), c.question()));
        }
        return targets;
    }

    private static Map<Case, Retrieval> baseline(KnowledgeBaseRetriever retriever, Map<Case, Document> targets) {
        final Map<Case, Retrieval> baseline = new LinkedHashMap<>();
        System.out.println("=== Без расширения (исходный вопрос)");
        targets.forEach((c, target) -> {
            final Retrieval retrieval = retriever.retrieve(c.question(), target);
            baseline.put(c, retrieval);
            System.out.printf("  %s | %s%n", formatRetrieval(retrieval), c.question());
        });
        System.out.println();
        return baseline;
    }

    private static VariantResult run(
            OllamaApi ollamaApi,
            OllamaChatOptions options,
            KnowledgeBaseRetriever retriever,
            Map<Case, Document> targets,
            Map<Case, Retrieval> baseline
    ) {
        final OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .build();
        final ExpansionQueryAdvisor advisor = ExpansionQueryAdvisor.builder(chatModel, options.mutate()).build();

        try {
            // прогрев: загрузка модели в память, в статистику не входит
            final Duration cold = expand(advisor, CASES.getFirst().question()).elapsed();

            final List<CaseResult> caseResults = new ArrayList<>();
            for (Case c : CASES) {
                for (int run = 0; run < RUNS_PER_CASE; run++) {
                    final Expansion expansion = expand(advisor, c.question());
                    final Retrieval retrieval = Optional.ofNullable(targets.get(c))
                            .map(target -> retriever.retrieve(expansion.answer(), target))
                            .orElse(null);
                    caseResults.add(new CaseResult(c, expansion, problems(c, expansion.answer()), retrieval));
                }
            }
            return new VariantResult(label(options), cold, caseResults, null);
        } catch (Exception e) {
            return new VariantResult(label(options), null, List.of(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Expansion expand(ExpansionQueryAdvisor advisor, String question) {
        final ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(question))
                .context(Map.of())
                .build();

        final long start = System.nanoTime();
        // before() не использует цепочку advisors, поэтому null допустим
        final ChatClientRequest enriched = advisor.before(request, null);
        final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        return new Expansion(String.valueOf(enriched.context().get(ExpansionQueryAdvisor.ENRICHED_QUESTION)), elapsed);
    }

    // ---------- автопроверки ----------

    private static final Pattern FORMAT_GARBAGE = Pattern.compile("→|->|Вход:|Выход:|Вопрос:|Запрос:|[*#`\\[\\]]");

    private static final Pattern REFUSAL = Pattern.compile(
            "не могу|не имею возможности|извините|i can't|i cannot",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final List<String> SPRING_TERMS = List.of(
            "spring", "спринг", "бин", "bean", "прокси", "proxy", "cglib", "aop", "applicationcontext",
            "beanfactory", "beandefinition", "postconstruct", "contextlistener", "jmx", "mbean", "reflection",
            "аннотац", "dependency", "injection");

    private static List<String> problems(Case c, String answer) {
        final List<String> problems = new ArrayList<>();
        final String trimmed = answer.strip();

        if (trimmed.contains("\n") || FORMAT_GARBAGE.matcher(trimmed).find()) {
            problems.add("формат");
        }
        if (REFUSAL.matcher(trimmed).find()) {
            problems.add("отказ");
        }

        final List<String> questionWords = words(c.question());
        final List<String> answerWords = words(trimmed);

        final List<String> lost = questionWords.stream()
                .filter(word -> word.length() >= 3)
                .filter(word -> answerWords.stream().noneMatch(candidate -> sameWord(word, candidate)))
                .toList();
        if (!lost.isEmpty()) {
            problems.add("потеряно: " + String.join(",", lost));
        }

        final int added = answerWords.size() - questionWords.size();
        if (added < 1 || added > MAX_ADDED_WORDS) {
            problems.add("добавлено слов: " + added);
        }

        if (!c.spring()) {
            final List<String> leaked = answerWords.stream()
                    .filter(word -> SPRING_TERMS.stream().anyMatch(word::startsWith))
                    .filter(word -> questionWords.stream().noneMatch(q -> sameWord(q, word)))
                    .distinct()
                    .toList();
            if (!leaked.isEmpty()) {
                problems.add("spring: " + String.join(",", leaked));
            }
        }
        return problems;
    }

    private static List<String> words(String text) {
        final String normalized = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return Pattern.compile("[^\\p{L}\\p{N}]+").splitAsStream(normalized)
                .filter(word -> !word.isEmpty())
                .toList();
    }

    /**
     * Та же словоформа или исправленная опечатка: "алергия" ~ "аллергия", "спринге" ~ "спринг".
     */
    private static boolean sameWord(String expected, String actual) {
        return commonPrefix(expected, actual) >= Math.min(5, expected.length())
                || levenshtein(expected, actual) <= 1;
    }

    private static int commonPrefix(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    // ---------- вывод ----------

    private static void print(VariantResult result, Map<Case, Retrieval> baseline) {
        System.out.println("=== " + result.label());
        if (result.error() != null) {
            System.out.println("ОШИБКА: " + result.error());
            System.out.println();
            return;
        }
        System.out.printf("холодный старт: %d ms%n", result.cold().toMillis());
        for (CaseResult r : result.cases()) {
            final String retrieval = r.retrieval() == null
                    ? " ".repeat(formatRetrieval(new Retrieval(0, 0, 0)).length())
                    : formatRetrieval(r.retrieval());
            final String delta = r.retrieval() == null
                    ? "      "
                    : "%+.3f".formatted(r.retrieval().similarity() - baseline.get(r.c()).similarity());
            System.out.printf("  %s %5d ms | %s %s | %s => %s%s%n",
                    r.problems().isEmpty() ? "✓" : "✗",
                    r.expansion().elapsed().toMillis(),
                    retrieval,
                    delta,
                    r.c().question(),
                    oneLine(r.expansion().answer()),
                    r.problems().isEmpty() ? "" : "  " + r.problems());
        }
        System.out.println();
    }

    private static String formatRetrieval(Retrieval r) {
        return "sim %.3f rank %3d ctx %s".formatted(
                r.similarity(), r.vectorRank(), r.inContext() ? "%2d".formatted(r.contextPosition()) : " -");
    }

    private static void printSummary(List<VariantResult> results, Map<Case, Retrieval> baseline) {
        final long baselineInContext = baseline.values().stream().filter(Retrieval::inContext).count();

        System.out.println("=== Итого");
        System.out.printf("Без расширения: в контексте LLM %d/%d%n%n", baselineInContext, baseline.size());
        System.out.printf("%-58s %8s %8s %10s %10s %8s%n",
                "вариант", "холодный", "среднее", "проверки", "контекст", "Δsim");
        for (VariantResult result : results) {
            if (result.error() != null) {
                System.out.printf("%-58s ОШИБКА: %s%n", result.label(), oneLine(result.error()));
                continue;
            }
            final List<CaseResult> withTarget = result.cases().stream()
                    .filter(r -> r.retrieval() != null)
                    .toList();
            System.out.printf("%-58s %5d ms %5d ms %10s %10s %+8.3f%n",
                    result.label(),
                    result.cold().toMillis(),
                    (long) result.cases().stream().mapToLong(r -> r.expansion().elapsed().toMillis()).average().orElse(0),
                    "%d/%d".formatted(result.cases().stream().filter(r -> r.problems().isEmpty()).count(), result.cases().size()),
                    "%d/%d".formatted(withTarget.stream().filter(r -> r.retrieval().inContext()).count(), withTarget.size()),
                    withTarget.stream()
                            .mapToDouble(r -> r.retrieval().similarity() - baseline.get(r.c()).similarity())
                            .average()
                            .orElse(0));
        }
        System.out.println();
        System.out.println("проверки - ответы без замечаний; контекст - чанк с ответом попал в контекст LLM;");
        System.out.println("Δsim - средний прирост близости к чанку с ответом относительно исходного вопроса.");
    }

    private static String label(OllamaChatOptions options) {
        return "%s t=%s k=%s p=%s think=%s".formatted(
                options.getModel(), options.getTemperature(), options.getTopK(), options.getTopP(),
                think(options.getThinkOption()));
    }

    private static String think(ThinkOption option) {
        if (option == null) {
            return "default";
        }
        if (option instanceof ThinkOption.ThinkBoolean think) {
            return think.enabled() ? "on" : "off";
        }
        return option.toString();
    }

    private static String oneLine(String text) {
        final String line = text.replaceAll("\\s+", " ").strip();
        return line.length() <= ANSWER_PREVIEW_LENGTH ? line : line.substring(0, ANSWER_PREVIEW_LENGTH) + "…";
    }

    // ---------- модель данных ----------

    private static Case personal(String question, String expectedPhrase) {
        return new Case(question, expectedPhrase, false);
    }

    private static Case spring(String question, String expectedPhrase) {
        return new Case(question, expectedPhrase, true);
    }

    /**
     * @param expectedPhrase фраза из базы знаний, по которой ищется чанк с ответом; null - ответа в базе нет
     * @param spring         вопрос про Spring/Java - Spring-термины в расширении допустимы
     */
    private record Case(String question, String expectedPhrase, boolean spring) {
    }

    private record Expansion(String answer, Duration elapsed) {
    }

    private record CaseResult(Case c, Expansion expansion, List<String> problems, Retrieval retrieval) {
    }

    private record VariantResult(String label, Duration cold, List<CaseResult> cases, String error) {
    }
}
