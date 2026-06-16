package com.example.softwaremetrics.core.application;

import com.example.softwaremetrics.core.domain.GateResult;
import com.example.softwaremetrics.core.domain.MetricsExport;
import com.example.softwaremetrics.core.domain.PackageMetrics;
import com.example.softwaremetrics.core.domain.arch.ArchResult;
import com.example.softwaremetrics.core.domain.deadcode.DeadCodeResult;

import java.util.List;
import java.util.Map;

/**
 * Immutable outcome of {@link AnalysisService#analyze(AnalysisRequest)}. Bundles the self-describing
 * {@link MetricsExport} envelope (ready to serialize) together with the individual pieces a consumer
 * may want to use directly (e.g. the web UI binds {@code metrics}/{@code cycles}/… onto the view
 * model), plus a {@link #success()} helper that the CLI/CI maps to its exit code.
 *
 * @param export              the assembled JSON-export envelope
 * @param metrics             per-package metrics keyed by module package
 * @param cycles              circular-dependency groups (empty when acyclic)
 * @param gate                quality-gate outcome, or {@code null} when gates were not evaluated
 * @param architecture        architecture-conformance result, or {@code null} when not configured
 * @param bannedApiViolations banned-API breaches (never {@code null}; empty when none/not configured)
 * @param deadCode            dead-code report, or {@code null} when not enabled
 */
public record AnalysisResult(
        MetricsExport export,
        Map<String, PackageMetrics> metrics,
        List<List<String>> cycles,
        GateResult gate,
        ArchResult architecture,
        List<GateResult.Violation> bannedApiViolations,
        DeadCodeResult deadCode) {

    /**
     * True when nothing that should fail a build did: gates passed (or were not evaluated),
     * architecture is compliant (or not configured), and there are no banned-API violations.
     * Dead-code is report-only and never affects this.
     */
    public boolean success() {
        return (gate == null || gate.passed())
                && (architecture == null || architecture.compliant())
                && bannedApiViolations.isEmpty();
    }
}
