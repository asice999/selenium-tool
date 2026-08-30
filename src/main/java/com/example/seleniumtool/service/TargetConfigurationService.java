package com.example.seleniumtool.service;

import com.example.seleniumtool.config.AutomationProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 管理可由 Web 页面修改、并在任务执行时实时使用的目标列表。 */
@Service
public class TargetConfigurationService {

    private final AutomationProperties properties;
    private final ObjectMapper objectMapper;
    private final TargetTemplateService targetTemplateService;
    private final Path configurationFile;

    public TargetConfigurationService(
            AutomationProperties properties,
            ObjectMapper objectMapper,
            TargetTemplateService targetTemplateService,
            @Value("${automation.targets-file:targets.json}") String configurationFile
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.targetTemplateService = targetTemplateService;
        this.configurationFile = Path.of(configurationFile).toAbsolutePath().normalize();
    }

    @PostConstruct
    public synchronized void load() {
        if (!Files.exists(configurationFile)) {
            return;
        }
        try {
            List<AutomationProperties.Target> targets = objectMapper.readValue(
                    Files.readString(configurationFile),
                    new TypeReference<List<AutomationProperties.Target>>() { }
            );
            properties.setTargets(copyTargets(targets));
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取目标配置文件: " + configurationFile, ex);
        }
    }

    public synchronized List<AutomationProperties.Target> getTargets() {
        return copyTargets(properties.getTargets());
    }

    public synchronized List<AutomationProperties.Target> saveTargets(
            List<AutomationProperties.Target> targets
    ) {
        validate(targets);
        List<AutomationProperties.Target> replacement = copyTargets(targets);
        try {
            Path parent = configurationFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = configurationFile.resolveSibling(configurationFile.getFileName() + ".tmp");
            Files.writeString(temporaryFile, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(replacement));
            Files.move(temporaryFile, configurationFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            properties.setTargets(replacement);
            return copyTargets(replacement);
        } catch (IOException ex) {
            throw new IllegalStateException("无法保存目标配置文件: " + configurationFile, ex);
        }
    }

    private void validate(List<AutomationProperties.Target> targets) {
        if (targets == null) {
            throw new IllegalArgumentException("targets 不能为 null");
        }
        for (int i = 0; i < targets.size(); i++) {
            AutomationProperties.Target target = targets.get(i);
            if (target == null || !StringUtils.hasText(target.getUrl())) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个目标的 URL 不能为空");
            }
            try {
                java.net.URI uri = java.net.URI.create(target.getUrl());
                if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                    throw new IllegalArgumentException("URL 必须包含协议和域名");
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个目标的 URL 无效: " + target.getUrl(), ex);
            }
            if (StringUtils.hasText(target.getTemplateId())) {
                validateTemplateTarget(target, i);
            }
        }
    }

    private void validateTemplateTarget(AutomationProperties.Target target, int index) {
        TargetTemplateService.TargetTemplate template = targetTemplateService
                .findTemplate(target.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "第 " + (index + 1) + " 个目标引用了不存在的模板: " + target.getTemplateId()
                ));
        if (!Objects.equals(template.name(), target.getName())
                || !Objects.equals(template.url(), target.getUrl())
                || !Objects.equals(template.warmupPath(), target.getWarmupPath())) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个模板目标的 URL、名称和预热路径不可修改"
            );
        }
        List<AutomationProperties.Target.LocalStorage> storage = target.getLocalStorage() == null
                ? List.of()
                : target.getLocalStorage();
        if (storage.size() != template.localStorageKeys().size()) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个模板目标的 localStorage 键不可增删"
            );
        }
        for (int i = 0; i < storage.size(); i++) {
            AutomationProperties.Target.LocalStorage item = storage.get(i);
            String expectedKey = template.localStorageKeys().get(i);
            if (item == null || !Objects.equals(expectedKey, item.getKey())) {
                throw new IllegalArgumentException(
                        "第 " + (index + 1) + " 个模板目标的 localStorage 键不可修改"
                );
            }
            if (!StringUtils.hasText(item.getValue())) {
                throw new IllegalArgumentException(
                        "第 " + (index + 1) + " 个模板目标的 localStorage["
                                + expectedKey + "] value 为必填项"
                );
            }
        }
    }

    private List<AutomationProperties.Target> copyTargets(List<AutomationProperties.Target> targets) {
        if (targets == null) {
            return new ArrayList<>();
        }
        return objectMapper.convertValue(targets,
                new TypeReference<List<AutomationProperties.Target>>() { });
    }
}
