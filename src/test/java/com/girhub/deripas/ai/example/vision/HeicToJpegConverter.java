package com.girhub.deripas.ai.example.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Конвертация фото с iPhone (HEIC) в JPEG через macOS {@code sips} - Ollama принимает только JPEG/PNG.
 * <p>
 * Запуск: main() из IDE, рабочая директория - корень проекта.
 * Аргументы: {@code <source.heic> [target.jpg] [maxSide]}; по умолчанию кладёт JPEG в {@link #TARGET_DIR}.
 */
public class HeicToJpegConverter {

    public static final Path TARGET_DIR = Path.of("src/test/resources/receipts");

    /** Длинная сторона после конвертации: больше - точнее мелкий текст, но дольше и больше токенов. */
    public static final int DEFAULT_MAX_SIDE = 1600;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Использование: HeicToJpegConverter <source.heic> [target.jpg] [maxSide]");
            return;
        }
        final Path source = Path.of(args[0]);
        final Path target = args.length > 1 ? Path.of(args[1]) : TARGET_DIR.resolve(jpegName(source));
        final int maxSide = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_MAX_SIDE;

        convert(source, target, maxSide);
        System.out.printf("%s -> %s (%d КБ, длинная сторона %d px)%n", source, target, Files.size(target) / 1024, maxSide);
    }

    public static void convert(Path source, Path target, int maxSide) throws IOException, InterruptedException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        final Process process = new ProcessBuilder(
                "sips", "-s", "format", "jpeg", "-Z", String.valueOf(maxSide),
                source.toString(), "--out", target.toString())
                .redirectErrorStream(true)
                .start();
        final String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("sips не смог сконвертировать " + source + ": " + output);
        }
    }

    private static String jpegName(Path source) {
        final String name = source.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ".jpg";
    }
}
