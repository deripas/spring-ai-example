package com.girhub.deripas.ai.example.vision;

import lombok.SneakyThrows;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;


public class Recognition {

    private static final String OLLAMA_BASE_URL = "http://192.168.1.224:11434";

    private static final String IMAGE = "receipts/IMG_4177.jpg";

    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    private static final int MAX_TOKENS = 1024;

    private static final String PROMPT = """
        Распознай данные кассового чека на изображении.
        """;

    private static final String PROMPT_SYSTEM = """
        Ты извлекаешь данные из фотографий кассовых чеков.
        Извлекай информацию только из приложенного изображения.

        Соблюдай переданную JSON-схему.
        Названия магазина и товаров переписывай точно как на чеке,
        не переводи и не транслитерируй.

        Суммы и количества представляй десятичными числами
        с точкой и без разделителей тысяч.
        Например: "1.120,00" -> 1120.00.

        Если значение поля отсутствует или неразборчиво,
        возвращай null. Не придумывай данные.
        """;

    static void main() throws IOException {
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(READ_TIMEOUT);
        final BeanOutputConverter<ReceiptRecognitionComparison.Receipt> converter = new BeanOutputConverter<>(ReceiptRecognitionComparison.Receipt.class);

        final OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        final OllamaChatOptions options = OllamaChatOptions.builder()
                .model("qwen2.5vl:7b")
                .numCtx(4096)
                .numPredict(MAX_TOKENS)
                .format(converter.getJsonSchemaMap())
                .disableThinking()
                .build();
        final OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
        final ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(PROMPT_SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        final String json = chatClient.prompt()
                .user(user -> user
                        .text(PROMPT)
                        .media(MimeTypeUtils.IMAGE_JPEG, load("IMG_4177.jpg"))
                )
                .call()
                .content();
        final ReceiptRecognitionComparison.Receipt receipt = converter.convert(json);

        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(receipt));
    }

    @SneakyThrows
    private static @NonNull ByteArrayResource load(String name)  {
        final byte[] image = new ClassPathResource("receipts/" + name).getContentAsByteArray();
        System.out.printf("Изображение: %s, %d КБ%n%n", name, image.length / 1024);
        byte[] resizedImage = resizeImage(image, 1200);
        BufferedImage img = ImageIO.read(
                new ByteArrayInputStream(resizedImage));

        System.out.printf(
                "Image: %d x %d, JPEG: %d KB%n",
                img.getWidth(),
                img.getHeight(),
                resizedImage.length / 1024);
        return new ByteArrayResource(resizedImage);
    }

    public static byte[] resizeImage(byte[] imageBytes, int maxWidth)
            throws IOException {

        final BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));

        if (original == null) {
            throw new IOException("Не удалось прочитать изображение");
        }

        int width = original.getWidth();
        int height = original.getHeight();

        // Если изображение уже достаточно маленькое — оставляем как есть
        if (width <= maxWidth) {
            return imageBytes;
        }

        final int newWidth = maxWidth;
        final int newHeight = (int) Math.round(
                (double) height * newWidth / width);

        final BufferedImage resized = new BufferedImage(
                newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

        final Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            graphics.drawImage(original, 0, 0, newWidth, newHeight, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(resized, "jpg", output)) {
                throw new IOException("Не удалось сохранить JPEG");
            }
            return output.toByteArray();
        }
    }
}
