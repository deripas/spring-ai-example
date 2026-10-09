package com.girhub.deripas.ai.example.vision;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Исследовательский запускатор (не тест): распознаёт фото чека vision-моделями Ollama,
 * просит ответ строго по JSON-схеме {@link Receipt} и сверяет поля с эталоном.
 * <p>
 * Запуск: main() из IDE, рабочая директория - корень проекта. Нужен локальный Ollama с моделями из {@link #MODELS}.
 * Фото чека - готовый JPEG в src/test/resources/receipts; HEIC с iPhone конвертировать через {@link HeicToJpegConverter}.
 */
public class ReceiptRecognitionComparison {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    private static final String IMAGE = "receipts/IMG_4177.jpg";

    private static final List<String> MODELS = List.of(
            "gemma3:4b-it-q4_K_M",
            "gemma4:e4b-it-q4_K_M",
             "gemma3:12b",
             "qwen2.5vl:3b",
             "qwen2.5vl:7b",
             "minicpm-v:8b",
             "granite3.2-vision:2b"
             // qwen3-vl:4b, qwen3-vl:8b - тег по умолчанию thinking: think=false игнорирует,
             //   рассуждает 4096+ токенов и не доходит до ответа; пробовать -instruct теги
             // llama3.2-vision:11b - Ollama 0.40: "unknown model architecture: 'mllama'"
    );

    /** Один и тот же промпт на разных языках: проверяем, влияет ли язык инструкции на качество. */
    private static final Map<String, String> PROMPTS = new LinkedHashMap<>();

    static {
        PROMPTS.put("ru", """
                Это фото кассового чека. Извлеки данные строго по JSON-схеме.
                Названия магазина и товаров переписывай как на чеке, не переводи и не транслитерируй.
                Суммы и количества - десятичные числа с точкой без разделителей тысяч: "1.120,00" -> 1120.00.
                Если поля нет на чеке - null.
                """);
        PROMPTS.put("en", """
                This is a photo of a sales receipt. Extract the data strictly according to the JSON schema.
                Copy the store and item names exactly as printed; do not translate or transliterate them.
                Amounts and quantities are decimal numbers with a dot and no thousands separators: "1.120,00" -> 1120.00.
                Use null if a field is not on the receipt.
                """);
    }

    /**
     * По умолчанию у RestClient read timeout 10 с, а распознавание с рассуждениями (qwen3-vl) идёт дольше:
     * запрос обрывается и Spring AI повторяет его заново, тест "висит" без нагрузки.
     */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    /** Ответ - JSON на несколько сотен токенов; лимит защищает от зацикливания. */
    private static final int MAX_TOKENS = 1024;

    // ---------- эталон для IMG_4177.HEIC ----------

    private static final List<Check> CHECKS = List.of(
            check("магазин PEPCO", r -> contains(r.store(), "pepco")),
            check("ПИБ 111295900", r -> digits(r.taxId()).equals("111295900")),
            check("дата 25.09.2026", r -> contains(r.dateTime(), "25.09.2026") || contains(r.dateTime(), "2026-09-25")),
            check("время 18:53", r -> contains(r.dateTime(), "18:53")),
            check("2 позиции", r -> r.items() != null && r.items().size() == 2),
            check("majica 1000", r -> hasItem(r, "majica", 1000.0)),
            check("kesa 120", r -> hasItem(r, "kesa", 120.0)),
            check("итого 1120", r -> equalsAmount(r.total(), 1120.0)),
            check("НДС 186.67", r -> equalsAmount(r.vatAmount(), 186.67)),
            check("номер SRPW4AYN-…-132368", r -> contains(r.receiptNumber(), "srpw4ayn") && digits(r.receiptNumber()).endsWith("132368"))
    );

    public static void main(String[] args) throws Exception {
        final byte[] image = new ClassPathResource(IMAGE).getContentAsByteArray();
        System.out.printf("Изображение: %s, %d КБ%n%n", IMAGE, image.length / 1024);

        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(READ_TIMEOUT);
        final OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        final BeanOutputConverter<Receipt> converter = new BeanOutputConverter<>(Receipt.class);

        final List<Result> results = new ArrayList<>();
        for (String model : MODELS) {
            for (Map.Entry<String, String> prompt : PROMPTS.entrySet()) {
                final Result result = run(ollamaApi, model, prompt.getKey(), prompt.getValue(), converter, image);
                results.add(result);
                print(result);
            }
        }
        printSummary(results);
    }

    private static Result run(OllamaApi ollamaApi, String model, String lang, String prompt,
                              BeanOutputConverter<Receipt> converter, byte[] image) {
        final OllamaChatOptions options = OllamaChatOptions.builder()
                .model(model)
                .temperature(0.0)
                .numPredict(MAX_TOKENS)
                .format(converter.getJsonSchemaMap())
                .disableThinking()
                .build();
        final ChatClient chatClient = ChatClient.builder(OllamaChatModel.builder()
                        .ollamaApi(ollamaApi)
                        .options(options)
                        // без повторов: ошибка или таймаут должны сразу попасть в отчёт
                        .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                        .build())
                .build();

        String raw = null;
        try {
            // прогрев: загрузка модели в память, в замер не входит
            final long coldStart = System.nanoTime();
            recognize(chatClient, prompt, image);
            final Duration cold = Duration.ofNanos(System.nanoTime() - coldStart);

            final long start = System.nanoTime();
            raw = recognize(chatClient, prompt, image);
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            final Receipt receipt = converter.convert(raw);
            final List<String> failed = CHECKS.stream()
                    .filter(check -> !check.passes(receipt))
                    .map(Check::name)
                    .toList();
            return new Result(model, lang, cold, elapsed, receipt, raw, failed, null);
        } catch (Exception e) {
            return new Result(model, lang, null, null, null, raw, List.of(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String recognize(ChatClient chatClient, String prompt, byte[] image) {
        return chatClient.prompt()
                .user(user -> user
                        .text(prompt)
                        .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(image)))
                .call()
                .content();
    }

    // ---------- проверки ----------

    private static Check check(String name, Predicate<Receipt> predicate) {
        return new Check(name, predicate);
    }

    private static boolean contains(String text, String part) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(part);
    }

    private static String digits(String text) {
        return text == null ? "" : text.replaceAll("\\D", "");
    }

    private static boolean equalsAmount(Double actual, double expected) {
        return actual != null && Math.abs(actual - expected) < 0.005;
    }

    private static boolean hasItem(Receipt receipt, String namePart, double total) {
        return receipt.items() != null && receipt.items().stream()
                .anyMatch(item -> contains(item.name(), namePart) && equalsAmount(item.total(), total));
    }

    // ---------- вывод ----------

    private static void print(Result result) {
        System.out.println("=== " + result.model() + " [" + result.lang() + "]");
        if (result.error() != null) {
            System.out.println("ОШИБКА: " + result.error());
            if (result.raw() != null) {
                System.out.println("ответ модели: " + result.raw());
            }
            System.out.println();
            return;
        }
        System.out.printf("холодный старт: %d ms, распознавание: %d ms%n", result.cold().toMillis(), result.elapsed().toMillis());
        System.out.printf("проверки: %d/%d%s%n", CHECKS.size() - result.failed().size(), CHECKS.size(),
                result.failed().isEmpty() ? "" : "  не прошли: " + result.failed());
        final Receipt r = result.receipt();
        System.out.printf("  магазин: %s | ПИБ: %s | дата: %s | номер: %s%n", r.store(), r.taxId(), r.dateTime(), r.receiptNumber());
        if (r.items() != null) {
            r.items().forEach(item -> System.out.printf("  - %s: %s x %s = %s%n", item.name(), item.price(), item.quantity(), item.total()));
        }
        System.out.printf("  итого: %s | оплата: %s | НДС: %s%n%n", r.total(), r.paymentMethod(), r.vatAmount());
    }

    private static void printSummary(List<Result> results) {
        System.out.println("=== Итого");
        System.out.printf("%-28s %4s %10s %10s %10s%n", "модель", "язык", "холодный", "время", "проверки");
        for (Result result : results) {
            if (result.error() != null) {
                System.out.printf("%-28s %4s ОШИБКА: %s%n", result.model(), result.lang(), result.error().replaceAll("\\s+", " "));
                continue;
            }
            System.out.printf("%-28s %4s %7d ms %7d ms %10s%n",
                    result.model(), result.lang(), result.cold().toMillis(), result.elapsed().toMillis(),
                    "%d/%d".formatted(CHECKS.size() - result.failed().size(), CHECKS.size()));
        }
    }

    // ---------- модель данных ----------

    record Receipt(
            @JsonPropertyDescription("Название продавца, как на чеке")
            String store,
            @JsonPropertyDescription("ИНН/налоговый номер продавца (ПИБ, PIB)")
            String taxId,
            @JsonPropertyDescription("Дата и время чека, как на чеке")
            String dateTime,
            @JsonPropertyDescription("Купленные товары")
            List<Item> items,
            @JsonPropertyDescription("Итоговая сумма к оплате")
            Double total,
            @JsonPropertyDescription("Способ оплаты: наличные, карта и т.п.")
            String paymentMethod,
            @JsonPropertyDescription("Общая сумма налога (НДС, ПДВ, порез)")
            Double vatAmount,
            @JsonPropertyDescription("Номер чека (фискальный номер)")
            String receiptNumber) {
    }

    record Item(
            @JsonPropertyDescription("Название товара без штрихкода")
            String name,
            @JsonPropertyDescription("Цена за единицу")
            Double price,
            @JsonPropertyDescription("Количество")
            Double quantity,
            @JsonPropertyDescription("Сумма по позиции")
            Double total) {
    }

    private record Check(String name, Predicate<Receipt> predicate) {

        boolean passes(Receipt receipt) {
            return predicate.test(receipt);
        }
    }

    private record Result(String model, String lang, Duration cold, Duration elapsed, Receipt receipt, String raw,
                          List<String> failed, String error) {
    }
}
