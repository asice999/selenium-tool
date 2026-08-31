package com.example.seleniumtool.web;

import com.example.seleniumtool.config.AutomationProperties;
import com.example.seleniumtool.service.BrowserAutomationService;
import com.example.seleniumtool.service.BrowserAutomationService.BatchRetrySubmission;
import com.example.seleniumtool.service.BrowserAutomationService.ExecutionRequestResult;
import com.example.seleniumtool.service.MoviePilotImportService;
import com.example.seleniumtool.service.TargetConfigurationService;
import com.example.seleniumtool.service.TargetRunHistoryService;
import com.example.seleniumtool.service.TargetRunHistoryService.TargetRunRecord;
import com.example.seleniumtool.service.TargetTemplateService;
import com.example.seleniumtool.service.TargetTemplateService.TargetTemplate;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final BrowserAutomationService browserAutomationService;
    private final TargetConfigurationService targetConfigurationService;
    private final TargetRunHistoryService targetRunHistoryService;
    private final TargetTemplateService targetTemplateService;
    private final MoviePilotImportService moviePilotImportService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public AutomationController(
            BrowserAutomationService browserAutomationService,
            TargetConfigurationService targetConfigurationService,
            TargetRunHistoryService targetRunHistoryService,
            TargetTemplateService targetTemplateService,
            MoviePilotImportService moviePilotImportService
    ) {
        this.browserAutomationService = browserAutomationService;
        this.targetConfigurationService = targetConfigurationService;
        this.targetRunHistoryService = targetRunHistoryService;
        this.targetTemplateService = targetTemplateService;
        this.moviePilotImportService = moviePilotImportService;
    }

    /**
     * 提供手动触发入口，避免 HTTP 请求线程被长时间阻塞。
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runNow() {
        if (!browserAutomationService.tryExecuteAsync(executorService)) {
            return ResponseEntity.status(409).body(Map.of(
                    "accepted", false,
                    "message", "任务仍在执行中，请稍候再试"
            ));
        }
        return ResponseEntity.accepted().body(Map.of(
                "accepted", true,
                "message", "任务已提交"
        ));
    }

    @GetMapping("/targets")
    public List<AutomationProperties.Target> getTargets() {
        return targetConfigurationService.getTargets();
    }

    @GetMapping("/target-templates")
    public List<TargetTemplate> getTargetTemplates() {
        return targetTemplateService.getTemplates();
    }

    @GetMapping("/targets/history")
    public List<TargetRunRecord> getTargetHistory(@RequestParam String url) {
        return targetRunHistoryService.getHistory(url);
    }

    @GetMapping("/targets/history/latest")
    public Map<String, TargetRunRecord> getLatestTargetHistories() {
        return targetRunHistoryService.getLatestHistories();
    }

    @GetMapping("/status")
    public Map<String, Boolean> getStatus() {
        return Map.of("running", browserAutomationService.isRunning());
    }

    @PostMapping("/targets/retry")
    public ResponseEntity<Map<String, Object>> retryTarget(@RequestParam String url) {
        ExecutionRequestResult result = browserAutomationService.tryExecuteTargetAsync(
                url,
                executorService
        );
        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().body(Map.of(
                    "accepted", true,
                    "message", "目标重试已提交"
            ));
            case BUSY -> ResponseEntity.status(409).body(Map.of(
                    "accepted", false,
                    "message", "任务仍在执行中，请稍候再试"
            ));
            case TARGET_NOT_FOUND -> ResponseEntity.status(404).body(Map.of(
                    "accepted", false,
                    "message", "目标不存在，请先保存配置"
            ));
        };
    }

    @PostMapping("/targets/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailedTargets() {
        BatchRetrySubmission submission = browserAutomationService.tryExecuteFailedTargetsAsync(
                executorService
        );
        return switch (submission.result()) {
            case ACCEPTED -> ResponseEntity.accepted().body(Map.of(
                    "accepted", true,
                    "message", "未运行或失败目标的重试任务已提交",
                    "targetUrls", submission.targetUrls()
            ));
            case BUSY -> ResponseEntity.status(409).body(Map.of(
                    "accepted", false,
                    "message", "任务仍在执行中，请稍候再试"
            ));
            case NOTHING_TO_RETRY -> ResponseEntity.ok(Map.of(
                    "accepted", false,
                    "message", "没有需要重试的目标"
            ));
        };
    }

    @PutMapping("/targets")
    public List<AutomationProperties.Target> saveTargets(
            @Valid @RequestBody List<AutomationProperties.Target> targets
    ) {
        return targetConfigurationService.saveTargets(targets);
    }

    @PostMapping("/import/moviepilot")
    public ResponseEntity<Map<String, Object>> importFromMoviePilot() {
        return ResponseEntity.ok(Map.of("files", moviePilotImportService.importFiles()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTarget(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}