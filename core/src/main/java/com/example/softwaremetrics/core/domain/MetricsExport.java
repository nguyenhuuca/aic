package com.example.softwaremetrics.core.domain;

import com.example.softwaremetrics.core.domain.arch.ArchResult;
import com.example.softwaremetrics.core.domain.deadcode.DeadCodeResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-describing envelope for exporting scan results as JSON. Wraps the raw per-package
 * metrics with metadata (when/where it was generated, tool version) and a quick summary so
 * an external system can consume and verify the results unambiguously. The optional {@code gate}
 * section is populated only by the CLI/CI mode and omitted from the web API response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricsExport(
        String generatedAt,
        String projectPath,
        String toolVersion,
        int packageCount,
        Summary summary,
        Map<String, PackageMetrics> packages,
        GateResult gate,
        List<List<String>> cycles,
        ArchResult architecture,
        List<GateResult.Violation> bannedApiViolations,
        DeadCodeResult deadCode) {

    /** Aggregate counts derived from the per-package metrics. */
    public record Summary(int wellDesigned, int needsAttention, double averageDistance) {
    }

    /**
     * Starts assembling an envelope for one scan. Metadata and the summary are computed from
     * {@code metrics} at {@link Builder#build()} time; optional report sections are attached fluently.
     */
    public static Builder builder(String projectPath, String toolVersion,
                                  Map<String, PackageMetrics> metrics) {
        return new Builder(projectPath, toolVersion, metrics);
    }

    /**
     * Collects the optional report sections and assembles the {@link MetricsExport} exactly once. Any
     * section left unset stays {@code null} and is therefore omitted from the JSON
     * ({@code @JsonInclude(NON_NULL)}). This is the single place the full record is constructed.
     */
    public static final class Builder {

        private final String projectPath;
        private final String toolVersion;
        private final Map<String, PackageMetrics> packages;
        private GateResult gate;
        private List<List<String>> cycles;
        private ArchResult architecture;
        private List<GateResult.Violation> bannedApiViolations;
        private DeadCodeResult deadCode;

        private Builder(String projectPath, String toolVersion, Map<String, PackageMetrics> metrics) {
            this.projectPath = projectPath;
            this.toolVersion = toolVersion;
            this.packages = metrics;
        }

        public Builder gate(GateResult gate) {
            this.gate = gate;
            return this;
        }

        public Builder cycles(List<List<String>> cycles) {
            this.cycles = cycles;
            return this;
        }

        public Builder architecture(ArchResult architecture) {
            this.architecture = architecture;
            return this;
        }

        public Builder bannedApis(List<GateResult.Violation> bannedApiViolations) {
            this.bannedApiViolations = bannedApiViolations;
            return this;
        }

        public Builder deadCode(DeadCodeResult deadCode) {
            this.deadCode = deadCode;
            return this;
        }

        /**
         * Assembles the envelope. A package counts as "well designed" when its distance from the main
         * sequence is {@code <= 0.5} (matching the UI's color threshold); the rest "need attention".
         */
        public MetricsExport build() {
            int wellDesigned = 0;
            double totalDistance = 0.0;
            for (PackageMetrics m : packages.values()) {
                totalDistance += m.getDistance();
                if (m.getDistance() <= 0.5) {
                    wellDesigned++;
                }
            }
            int packageCount = packages.size();
            int needsAttention = packageCount - wellDesigned;
            double averageDistance = (packageCount == 0)
                    ? 0.0
                    : Math.round((totalDistance / packageCount) * 100.0) / 100.0;

            return new MetricsExport(
                    Instant.now().toString(), projectPath, toolVersion, packageCount,
                    new Summary(wellDesigned, needsAttention, averageDistance), packages,
                    gate, cycles, architecture, bannedApiViolations, deadCode);
        }
    }
}
