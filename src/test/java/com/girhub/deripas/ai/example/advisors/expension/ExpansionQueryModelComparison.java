package com.girhub.deripas.ai.example.advisors.expension;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Исследовательский запускатор (не тест): прогоняет один и тот же вопрос через
 * ExpansionQueryAdvisor на разных моделях Ollama и печатает расширенный запрос и время ответа.
 * <p>
 * Запуск: main() из IDE. Нужен локальный Ollama с перечисленными моделями ({@code ollama list}).
 * Промпт берётся из {@link ExpansionQueryAdvisor#TEMPLATE_RESOURCE} - тот же, что в приложении.
 */
public class ExpansionQueryModelComparison {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    private static final String QUESTION = "на что у тебя алергия";

    private static final List<String> MODELS = List.of(
            "gemma3:4b-it-q4_K_M",
            "gemma4:e4b-it-q4_K_M",
            "qwen3:8b",
            "llama3.1:8b"
    );

    /** Сколько замеров после прогрева. Первый (холодный) вызов включает загрузку модели в память. */
    private static final int WARM_RUNS = 3;

    /** У thinking-моделей (qwen3, gemma4, deepseek-r1) рассуждения сильно увеличивают время ответа. */
    private static final boolean DISABLE_THINKING = true;

    public static void main(String[] args) {
        final OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .build();

        System.out.printf("Вопрос: %s%n%n", QUESTION);

        final List<Result> results = new ArrayList<>();
        for (String model : MODELS) {
            final Result result = run(ollamaApi, model);
            results.add(result);
            print(result);
        }

        printSummary(results);
    }

    private static Result run(OllamaApi ollamaApi, String model) {
        final OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(model);
        if (DISABLE_THINKING) {
            options.disableThinking();
        }
        final OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options.build())
                .build();
        final ExpansionQueryAdvisor advisor = ExpansionQueryAdvisor.builder(chatModel).build();

        try {
            final Call cold = call(advisor);
            final List<Call> warm = new ArrayList<>();
            for (int i = 0; i < WARM_RUNS; i++) {
                warm.add(call(advisor));
            }
            return Result.success(model, cold, warm);
        } catch (Exception e) {
            return Result.failure(model, e);
        }
    }

    private static Call call(ExpansionQueryAdvisor advisor) {
        final ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(QUESTION))
                .context(Map.of())
                .build();

        final long start = System.nanoTime();
        // before() не использует цепочку advisors, поэтому null допустим
        final ChatClientRequest enriched = advisor.before(request, null);
        final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        return new Call(String.valueOf(enriched.context().get(ExpansionQueryAdvisor.ENRICHED_QUESTION)), elapsed);
    }

    private static void print(Result result) {
        System.out.println("=== " + result.model());
        if (result.error() != null) {
            System.out.println("ОШИБКА: " + result.error());
            System.out.println();
            return;
        }
        System.out.printf("холодный: %5d ms | %s%n", result.cold().elapsed().toMillis(), oneLine(result.cold().answer()));
        for (Call call : result.warm()) {
            System.out.printf("прогретый: %4d ms | %s%n", call.elapsed().toMillis(), oneLine(call.answer()));
        }
        System.out.println();
    }

    private static void printSummary(List<Result> results) {
        System.out.println("=== Итого (среднее по прогретым вызовам)");
        System.out.printf("%-26s %10s %10s  %s%n", "модель", "холодный", "среднее", "ответ");
        for (Result result : results) {
            if (result.error() != null) {
                System.out.printf("%-26s %10s %10s  %s%n", result.model(), "-", "-", "ОШИБКА");
                continue;
            }
            System.out.printf("%-26s %8d ms %7d ms  %s%n",
                    result.model(),
                    result.cold().elapsed().toMillis(),
                    result.avgWarmMillis(),
                    oneLine(result.warm().isEmpty() ? result.cold().answer() : result.warm().getLast().answer()));
        }
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private record Call(String answer, Duration elapsed) {
    }

    private record Result(String model, Call cold, List<Call> warm, String error) {

        static Result success(String model, Call cold, List<Call> warm) {
            return new Result(model, cold, warm, null);
        }

        static Result failure(String model, Exception e) {
            return new Result(model, null, List.of(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        long avgWarmMillis() {
            return (long) warm.stream()
                    .mapToLong(call -> call.elapsed().toMillis())
                    .average()
                    .orElse(cold.elapsed().toMillis());
        }
    }
}
