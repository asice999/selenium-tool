package com.example.seleniumtool.service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 从 YAML 资源加载新增目标时可选择的内置模板。 */
@Service
public class TargetTemplateService {

    private final ObjectMapper objectMapper;
    private final Resource templateResource;
    private List<TargetTemplate> templates = List.of();

    public TargetTemplateService(
            ObjectMapper objectMapper,
            @Value("${automation.target-templates-file:classpath:target-templates.yml}")
            Resource templateResource
    ) {
        this.objectMapper = objectMapper;
        this.templateResource = templateResource;
    }

    @PostConstruct
    public void load() {
        YamlMapFactoryBean yamlFactory = new YamlMapFactoryBean();
        yamlFactory.setResources(templateResource);
        Map<String, Object> root = yamlFactory.getObject();
        Object rawTemplates = root == null ? null : root.get("templates");
        List<TargetTemplate> loaded = rawTemplates == null
                ? List.of()
                : objectMapper.convertValue(
                        rawTemplates,
                        new TypeReference<List<TargetTemplate>>() { }
                );
        validate(loaded);
        templates = loaded.stream()
                .map(template -> new TargetTemplate(
                        template.id(),
                        template.name(),
                        template.url(),
                        template.warmupPath(),
                        template.localStorageKeys() == null
                                ? List.of()
                                : List.copyOf(template.localStorageKeys())
                ))
                .toList();
    }

    public List<TargetTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    public Optional<TargetTemplate> findTemplate(String templateId) {
        return templates.stream()
                .filter(template -> template.id().equals(templateId))
                .findFirst();
    }

    private void validate(List<TargetTemplate> loaded) {
        Set<String> ids = new HashSet<>();
        for (TargetTemplate template : loaded) {
            if (template == null
                    || !StringUtils.hasText(template.id())
                    || !StringUtils.hasText(template.name())
                    || !StringUtils.hasText(template.url())
                    || !StringUtils.hasText(template.warmupPath())) {
                throw new IllegalStateException("目标模板必须配置 id、name、url 和 warmupPath");
            }
            if (!ids.add(template.id())) {
                throw new IllegalStateException("目标模板 id 重复: " + template.id());
            }
            try {
                URI uri = URI.create(template.url());
                if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                    throw new IllegalArgumentException("URL 缺少协议或域名");
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("目标模板 URL 无效: " + template.url(), ex);
            }
            List<String> keys = template.localStorageKeys() == null
                    ? List.of()
                    : new ArrayList<>(template.localStorageKeys());
            if (keys.stream().anyMatch(key -> !StringUtils.hasText(key))) {
                throw new IllegalStateException("目标模板 localStorageKeys 不能包含空值: " + template.id());
            }
        }
    }

    public record TargetTemplate(
            String id,
            String name,
            String url,
            String warmupPath,
            List<String> localStorageKeys
    ) { }
}
