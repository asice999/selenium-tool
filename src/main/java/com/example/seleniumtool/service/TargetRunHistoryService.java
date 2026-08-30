package com.example.seleniumtool.service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 持久化每个目标最近的运行结果。 */
@Service
public class TargetRunHistoryService {

    private static final Logger log = LoggerFactory.getLogger(TargetRunHistoryService.class);
    private static final int MAX_HISTORY_SIZE = 7;

    private final ObjectMapper objectMapper;
    private final Path historyFile;
    private Map<String, List<TargetRunRecord>> histories = new LinkedHashMap<>();

    public TargetRunHistoryService(
            ObjectMapper objectMapper,
            @Value("${automation.target-history-file:target-run-history.json}") String historyFile
    ) {
        this.objectMapper = objectMapper;
        this.historyFile = Path.of(historyFile).toAbsolutePath().normalize();
    }

    @PostConstruct
    public synchronized void load() {
        if (!Files.exists(historyFile)) {
            return;
        }
        try {
            Map<String, List<TargetRunRecord>> loaded = objectMapper.readValue(
                    Files.readString(historyFile),
                    new TypeReference<Map<String, List<TargetRunRecord>>>() { }
            );
            histories = loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
            histories.replaceAll((url, records) -> trim(records));
        } catch (Exception ex) {
            log.warn("无法读取目标运行记录文件 {}，将使用空记录", historyFile, ex);
            histories = new LinkedHashMap<>();
        }
    }

    public synchronized void record(String targetUrl, boolean success, String message) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return;
        }
        List<TargetRunRecord> records = new ArrayList<>(
                histories.getOrDefault(targetUrl, List.of())
        );
        records.add(0, new TargetRunRecord(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                success,
                message
        ));
        histories.put(targetUrl, trim(records));
        persist();
    }

    public synchronized List<TargetRunRecord> getHistory(String targetUrl) {
        return List.copyOf(histories.getOrDefault(targetUrl, List.of()));
    }

    public synchronized Map<String, TargetRunRecord> getLatestHistories() {
        Map<String, TargetRunRecord> latest = new LinkedHashMap<>();
        histories.forEach((url, records) -> {
            if (records != null && !records.isEmpty()) {
                latest.put(url, records.get(0));
            }
        });
        return latest;
    }

    private List<TargetRunRecord> trim(List<TargetRunRecord> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(records.subList(0, Math.min(records.size(), MAX_HISTORY_SIZE)));
    }

    private void persist() {
        try {
            Path parent = historyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
            Files.writeString(temporaryFile, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(histories));
            Files.move(temporaryFile, historyFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            log.warn("无法保存目标运行记录文件 {}", historyFile, ex);
        }
    }

    public record TargetRunRecord(String executedAt, boolean success, String message) { }
}
