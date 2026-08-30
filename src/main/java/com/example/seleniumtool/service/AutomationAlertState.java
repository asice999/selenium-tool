package com.example.seleniumtool.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AutomationAlertState {

    private final List<String> targetFailures = new ArrayList<>();
    private final List<String> startupWarnings = new ArrayList<>();

    public synchronized void addTargetFailure(String message) {
        targetFailures.add(message);
    }

    public synchronized List<String> drainTargetFailures() {
        List<String> copy = new ArrayList<>(targetFailures);
        targetFailures.clear();
        return copy;
    }

    public synchronized void addStartupWarning(String message) {
        if (!startupWarnings.contains(message)) {
            startupWarnings.add(message);
        }
    }

    public synchronized List<String> drainStartupWarnings() {
        List<String> copy = new ArrayList<>(startupWarnings);
        startupWarnings.clear();
        return copy;
    }
}
