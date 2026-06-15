package com.example.softwaremetrics.application;

import com.example.softwaremetrics.config.CheckConfig;
import com.example.softwaremetrics.config.CheckConfigLoader;
import com.example.softwaremetrics.domain.ClassInfo;
import com.example.softwaremetrics.domain.CycleDetector;
import com.example.softwaremetrics.domain.GateResult;
import com.example.softwaremetrics.domain.InstabilityCalculatorProperties;
import com.example.softwaremetrics.domain.JavaClassAnalyzer;
import com.example.softwaremetrics.domain.MetricsExport;
import com.example.softwaremetrics.domain.PackageLocator;
import com.example.softwaremetrics.domain.PackageMetrics;
import com.example.softwaremetrics.domain.PackageMetricsCalculator;
import com.example.softwaremetrics.domain.ProjectPathTraverser;
import com.example.softwaremetrics.domain.ThresholdEvaluator;
import com.example.softwaremetrics.domain.arch.ArchChecker;
import com.example.softwaremetrics.domain.arch.ArchResult;
import com.example.softwaremetrics.domain.banned.BannedApiChecker;
import com.example.softwaremetrics.domain.deadcode.DeadCodeDetector;
import com.example.softwaremetrics.domain.deadcode.DeadCodeResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The single public entry point for running an analysis. Encapsulates the whole orchestration —
 * resolve config, scan metrics, detect cycles, evaluate gates, run the architecture / banned-API /
 * dead-code checks, and assemble the {@link MetricsExport} envelope — so a consumer just calls
 * {@link #analyze(AnalysisRequest)} instead of wiring (and sequencing) the engine internals by hand.
 *
 * <p>Build it either with the full constructor (the web module wires it as a Spring bean) or via
 * {@link #create(InstabilityCalculatorProperties)}, which hand-wires the entire object graph.
 */
public class AnalysisService {

    private final SpringBootPackageScanner scanner;
    private final PackageLocator packageLocator;
    private final JavaClassAnalyzer javaClassAnalyzer;
    private final CycleDetector cycleDetector;
    private final ThresholdEvaluator thresholdEvaluator;
    private final ArchChecker archChecker;
    private final BannedApiChecker bannedApiChecker;
    private final DeadCodeDetector deadCodeDetector;

    public AnalysisService(SpringBootPackageScanner scanner, PackageLocator packageLocator,
                           JavaClassAnalyzer javaClassAnalyzer, CycleDetector cycleDetector,
                           ThresholdEvaluator thresholdEvaluator, ArchChecker archChecker,
                           BannedApiChecker bannedApiChecker, DeadCodeDetector deadCodeDetector) {
        this.scanner = scanner;
        this.packageLocator = packageLocator;
        this.javaClassAnalyzer = javaClassAnalyzer;
        this.cycleDetector = cycleDetector;
        this.thresholdEvaluator = thresholdEvaluator;
        this.archChecker = archChecker;
        this.bannedApiChecker = bannedApiChecker;
        this.deadCodeDetector = deadCodeDetector;
    }

    /**
     * Hand-wires the full analysis object graph from a single configuration object — the wiring a
     * headless consumer (e.g. the CLI) would otherwise have to replicate.
     */
    public static AnalysisService create(InstabilityCalculatorProperties exclusions) {
        JavaClassAnalyzer analyzer = new JavaClassAnalyzer(exclusions);
        PackageLocator locator = new PackageLocator(analyzer, new ProjectPathTraverser());
        SpringBootPackageScanner scanner =
                new SpringBootPackageScanner(locator, new PackageMetricsCalculator(analyzer));
        return new AnalysisService(scanner, locator, analyzer, new CycleDetector(),
                new ThresholdEvaluator(), new ArchChecker(), new BannedApiChecker(), new DeadCodeDetector());
    }

    /**
     * Runs the full analysis for one request.
     *
     * @throws IllegalArgumentException if the project has no {@code @SpringBootApplication} or no
     *                                  module sub-packages (propagated from the scanner)
     * @throws IllegalStateException    on an I/O error while reading the project
     */
    public AnalysisResult analyze(AnalysisRequest request) {
        Path projectPath = Path.of(request.projectPath());
        CheckConfig config = CheckConfigLoader.resolve(projectPath, request.overrides());

        Map<String, PackageMetrics> metrics = scanner.scanProject(request.projectPath(), config.analyze());
        List<List<String>> cycles = cycleDetector.findCycles(metrics);

        GateResult gate = request.evaluateGates()
                ? thresholdEvaluator.evaluate(metrics, cycles, config.gate())
                : null;

        Checks checks = runChecks(projectPath, config);

        MetricsExport export = MetricsExport.from(request.projectPath(), request.toolVersion(), metrics)
                .withCycles(cycles);
        if (gate != null) {
            export = export.withGate(gate);
        }
        if (checks.architecture() != null) {
            export = export.withArchitecture(checks.architecture());
        }
        if (!config.bannedApis().isEmpty()) {
            export = export.withBannedApis(checks.bannedApis());
        }
        if (checks.deadCode() != null) {
            export = export.withDeadCode(checks.deadCode());
        }

        return new AnalysisResult(export, metrics, cycles, gate,
                checks.architecture(), checks.bannedApis(), checks.deadCode());
    }

    /** Result of the optional checks driven by aic-check.yaml / the {@code --arch} override. */
    private record Checks(ArchResult architecture, List<GateResult.Violation> bannedApis, DeadCodeResult deadCode) {
    }

    /**
     * Runs architecture, banned-API and dead-code checks as configured. The banned-API and dead-code
     * checks share a single project-model pass; each result is null/empty when not configured.
     */
    private Checks runChecks(Path projectPath, CheckConfig config) {
        boolean needsModel = !config.bannedApis().isEmpty() || config.deadCodeEnabled();
        boolean needsArch = config.architecture() != null;
        if (!needsModel && !needsArch) {
            return new Checks(null, List.of(), null);
        }
        String mainPackage = packageLocator.findMainPackage(projectPath);
        if (mainPackage == null || mainPackage.isEmpty()) {
            return new Checks(null, List.of(), null);
        }

        ArchResult architecture = null;
        if (needsArch) {
            architecture = archChecker.check(config.architecture(),
                    javaClassAnalyzer.buildClassDependencyGraph(projectPath, mainPackage));
        }
        List<GateResult.Violation> bannedApis = List.of();
        DeadCodeResult deadCode = null;
        if (needsModel) {
            List<ClassInfo> projectModel = javaClassAnalyzer.analyzeProject(projectPath, mainPackage);
            if (!config.bannedApis().isEmpty()) {
                bannedApis = bannedApiChecker.check(projectModel, config.bannedApis());
            }
            if (config.deadCodeEnabled()) {
                deadCode = deadCodeDetector.detect(projectModel);
            }
        }
        return new Checks(architecture, bannedApis, deadCode);
    }
}
