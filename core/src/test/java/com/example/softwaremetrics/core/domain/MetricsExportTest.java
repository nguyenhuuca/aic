package com.example.softwaremetrics.core.domain;

import com.example.softwaremetrics.core.domain.arch.ArchResult;
import com.example.softwaremetrics.core.domain.deadcode.DeadCodeResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsExportTest {

    private PackageMetrics pkg(String name, double distance) {
        PackageMetrics m = new PackageMetrics();
        m.setPackageName(name);
        m.setDistance(distance);
        return m;
    }

    @Test
    void computesSummaryFromMetrics() {
        Map<String, PackageMetrics> metrics = new LinkedHashMap<>();
        metrics.put("a", pkg("a", 0.10)); // well designed (<= 0.5)
        metrics.put("b", pkg("b", 0.50)); // well designed (boundary)
        metrics.put("c", pkg("c", 0.90)); // needs attention

        MetricsExport export = MetricsExport.builder("/some/project", "1.0-TEST", metrics).build();

        assertThat(export.projectPath()).isEqualTo("/some/project");
        assertThat(export.toolVersion()).isEqualTo("1.0-TEST");
        assertThat(export.generatedAt()).isNotBlank();
        assertThat(export.packageCount()).isEqualTo(3);
        assertThat(export.packages()).isSameAs(metrics);

        MetricsExport.Summary summary = export.summary();
        assertThat(summary.wellDesigned()).isEqualTo(2);
        assertThat(summary.needsAttention()).isEqualTo(1);
        // (0.10 + 0.50 + 0.90) / 3 = 0.50
        assertThat(summary.averageDistance()).isEqualTo(0.50);
    }

    @Test
    void handlesEmptyMetrics() {
        MetricsExport export = MetricsExport.builder("/empty", "1.0-TEST", Map.of()).build();

        assertThat(export.packageCount()).isZero();
        assertThat(export.summary().wellDesigned()).isZero();
        assertThat(export.summary().needsAttention()).isZero();
        assertThat(export.summary().averageDistance()).isZero();
    }

    @Test
    void optionalSectionsAreNullWhenLeftUnset() {
        MetricsExport export = MetricsExport.builder("/p", "1.0-TEST", Map.of()).build();

        assertThat(export.gate()).isNull();
        assertThat(export.cycles()).isNull();
        assertThat(export.architecture()).isNull();
        assertThat(export.bannedApiViolations()).isNull();
        assertThat(export.deadCode()).isNull();
    }

    @Test
    void builderAttachesEachOptionalSection() {
        GateResult gate = new GateResult(false, List.of(
                new GateResult.Violation("maxPackageDistance", "a", 0.8, 0.7, "msg")));
        List<List<String>> cycles = List.of(List.of("a", "b"));
        ArchResult arch = new ArchResult("layered", true, List.of());
        List<GateResult.Violation> banned = List.of(
                new GateResult.Violation("bannedApi", "com.example.Foo", 1.0, 0.0, "uses banned API"));
        DeadCodeResult deadCode = new DeadCodeResult(List.of("com.example.Unused"));

        MetricsExport export = MetricsExport.builder("/p", "1.0-TEST", Map.of())
                .gate(gate)
                .cycles(cycles)
                .architecture(arch)
                .bannedApis(banned)
                .deadCode(deadCode)
                .build();

        assertThat(export.gate()).isSameAs(gate);
        assertThat(export.cycles()).isSameAs(cycles);
        assertThat(export.architecture()).isSameAs(arch);
        assertThat(export.bannedApiViolations()).isSameAs(banned);
        assertThat(export.deadCode()).isSameAs(deadCode);
    }
}
