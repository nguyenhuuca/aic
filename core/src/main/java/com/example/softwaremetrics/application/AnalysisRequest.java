package com.example.softwaremetrics.application;

import com.example.softwaremetrics.config.CheckConfigLoader;

/**
 * Immutable input to {@link AnalysisService#analyze(AnalysisRequest)} — the single way a consumer
 * asks for a project to be analyzed, so callers never need to know how the engine is wired.
 *
 * @param projectPath   filesystem path of the (compiled) project to scan
 * @param overrides     CLI-flag-style overrides layered on top of the project's {@code aic-check.yaml}
 *                      (never {@code null}; use {@link CheckConfigLoader.Overrides#none()})
 * @param toolVersion   version string stamped into the exported {@link com.example.softwaremetrics.domain.MetricsExport}
 * @param evaluateGates whether to evaluate the quality gates and attach the result to the export
 *                      (the CLI/CI sets this; the web UI leaves gates out of its JSON response)
 */
public record AnalysisRequest(String projectPath, CheckConfigLoader.Overrides overrides,
                              String toolVersion, boolean evaluateGates) {

    public AnalysisRequest {
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("projectPath must not be blank");
        }
        if (overrides == null) {
            overrides = CheckConfigLoader.Overrides.none();
        }
        if (toolVersion == null) {
            toolVersion = "unknown";
        }
    }

    /** A plain request: no flag overrides, gates not evaluated (suitable for the web UI). */
    public static AnalysisRequest of(String projectPath, String toolVersion) {
        return new AnalysisRequest(projectPath, CheckConfigLoader.Overrides.none(), toolVersion, false);
    }

    /** Returns a copy with the given overrides. */
    public AnalysisRequest withOverrides(CheckConfigLoader.Overrides newOverrides) {
        return new AnalysisRequest(projectPath, newOverrides, toolVersion, evaluateGates);
    }

    /** Returns a copy that evaluates (or skips) the quality gates. */
    public AnalysisRequest withGateEvaluation(boolean evaluate) {
        return new AnalysisRequest(projectPath, overrides, toolVersion, evaluate);
    }
}
