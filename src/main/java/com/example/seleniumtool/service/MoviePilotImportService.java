
package com.example.seleniumtool.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MoviePilotImportService {

    private static final Logger log = LoggerFactory.getLogger(MoviePilotImportService.class);

    private final Path configDir;

    public MoviePilotImportService(@Value("${automation.moviepilot-config-dir:/volume1/docker/moviepilot-v3/config}") String configDir) {
        this.configDir = Path.of(configDir).toAbsolutePath().normalize();
    }

    public Map<String, String> importFiles() {
        Map<String, String> result = new LinkedHashMap<>();
        copyIfExists(result, "p115_cookies.txt", configDir.resolve("p115_cookies.txt"), Path.of("./config/p115_cookies.txt"));
        copyIfExists(result, "CloudSubscribe/p115_cookies.txt", configDir.resolve("plugins/CloudSubscribe/p115_cookies.txt"), Path.of("./config/CloudSubscribe/p115_cookies.txt"));
        copyIfExists(result, "cloudsubscribe.db", configDir.resolve("cloudsubscribe.db"), Path.of("./config/cloudsubscribe.db"));
        copyIfExists(result, "hdhive-curl-session.json", configDir.resolve("hdhive-curl-session.json"), Path.of("./config/hdhive-curl-session.json"));
        copyIfExists(result, "targets.json", configDir.resolve("targets.json"), Path.of("./config/targets.json"));
        copyIfExists(result, "user-account.json", configDir.resolve("user-account.json"), Path.of("./config/user-account.json"));
        copyIfExists(result, "cookiecloud-cache.json", configDir.resolve("cookiecloud-cache.json"), Path.of("./config/cookiecloud-cache.json"));
        return result;
    }

    private void copyIfExists(Map<String, String> result, String key, Path src, Path dst) {
        try {
            if (!Files.exists(src)) {
                result.put(key, "missing");
                return;
            }
            Path parent = dst.toAbsolutePath().normalize().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            result.put(key, "ok");
        } catch (IOException e) {
            log.warn("导入 {} 失败", src, e);
            result.put(key, "error:" + e.getMessage());
        }
    }
}

