package com.example.softwaremetrics.core.config;

import com.example.softwaremetrics.core.domain.GateConfig;
import com.example.softwaremetrics.core.domain.arch.ArchSpec;
import com.example.softwaremetrics.core.domain.banned.BannedApiRule;

import java.util.List;

/**
 * Effective check configuration for a run: the quality {@link GateConfig}, an optional architecture
 * spec ({@code null} when disabled), banned-API rules (empty when disabled), and whether the
 * (report-only) dead-code check is on. Produced by {@link CheckConfigLoader} by layering code
 * defaults &lt; project {@code aic-check.yaml} &lt; CLI flags.
 */
public record CheckConfig(GateConfig gate, ArchSpec architecture,
                          List<BannedApiRule> bannedApis, boolean deadCodeEnabled,
                          AnalyzeConfig analyze) {
}
