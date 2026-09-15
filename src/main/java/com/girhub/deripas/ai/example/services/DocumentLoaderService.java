package com.girhub.deripas.ai.example.services;

import com.girhub.deripas.ai.example.model.LoadedDocument;
import com.girhub.deripas.ai.example.repo.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentLoaderService implements CommandLineRunner {

    private final DocumentRepository documentRepository;
    private final ResourcePatternResolver resolver;
    private final VectorStore vectorStore;

    @SneakyThrows
    public void loadDocuments() {
        final List<Resource> resources = Arrays
                .stream(resolver.getResources("classpath:/knowledgebase/**/*.txt"))
                .toList();

        resources.stream()
                .map(resource -> Pair.of(resource, calcContentHash(resource)))
                .filter(pair -> !documentRepository.existsByFilenameAndContentHash(pair.getFirst().getFilename(), pair.getSecond()))
                .forEach(pair -> {
                    final Resource resource = pair.getFirst();
                    final List<Document> documents = new TextReader(resource).get();
                    final TokenTextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(500).build();
                    final List<Document> chunks = textSplitter.apply(documents);
                    vectorStore.accept(chunks);

                    final LoadedDocument loadedDocument = LoadedDocument.builder()
                            .documentType("txt")
                            .chunkCount(chunks.size())
                            .filename(resource.getFilename())
                            .contentHash(pair.getSecond())
                            .build();
                    documentRepository.save(loadedDocument);
                });
    }

    @SneakyThrows
    private String calcContentHash(Resource resource) {
        return DigestUtils.md5DigestAsHex(resource.getInputStream());
    }

    @Override
    public void run(String @NonNull ... args) {
        loadDocuments();
    }
}
