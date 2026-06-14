package com.example.softwaremetrics.config;

import com.example.softwaremetrics.domain.GateConfig;
import com.example.softwaremetrics.domain.arch.ArchSpec;

/**
 * Effective check configuration for a run: the quality {@link GateConfig} and, optionally, the
 * architecture spec to enforce ({@code null} when the architecture check is disabled). Produced by
 * {@link CheckConfigLoader} by layering code defaults &lt; project {@code aic-check.yaml} &lt; CLI flags.
 */
public record CheckConfig(GateConfig gate, ArchSpec architecture) {
}
