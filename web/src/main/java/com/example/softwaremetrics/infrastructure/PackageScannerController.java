package com.example.softwaremetrics.infrastructure;

import com.example.softwaremetrics.application.SpringBootPackageScanner;
import com.example.softwaremetrics.domain.CycleDetector;
import com.example.softwaremetrics.domain.JavaClassAnalyzer;
import com.example.softwaremetrics.domain.MetricsExport;
import com.example.softwaremetrics.domain.PackageLocator;
import com.example.softwaremetrics.domain.PackageMetrics;
import com.example.softwaremetrics.domain.arch.ArchChecker;
import com.example.softwaremetrics.domain.arch.ArchResult;
import com.example.softwaremetrics.domain.arch.ArchSpec;
import com.example.softwaremetrics.domain.arch.ArchSpecLoader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class PackageScannerController {

    private final SpringBootPackageScanner springBootPackageScanner;
    private final CycleDetector cycleDetector;
    private final PackageLocator packageLocator;
    private final JavaClassAnalyzer javaClassAnalyzer;
    private final ArchChecker archChecker;

    @Value("${app.tool-version:1.0-SNAPSHOT}")
    private String toolVersion;

    public PackageScannerController(SpringBootPackageScanner springBootPackageScanner, CycleDetector cycleDetector,
                                    PackageLocator packageLocator, JavaClassAnalyzer javaClassAnalyzer,
                                    ArchChecker archChecker) {
        this.springBootPackageScanner = springBootPackageScanner;
        this.cycleDetector = cycleDetector;
        this.packageLocator = packageLocator;
        this.javaClassAnalyzer = javaClassAnalyzer;
        this.archChecker = archChecker;
    }

    /** Runs the architecture check for the given template against the project, or null if no template. */
    private ArchResult checkArchitecture(String path, String arch) {
        if (arch == null || arch.isBlank()) {
            return null;
        }
        Path projectPath = Path.of(path);
        String mainPackage = packageLocator.findMainPackage(projectPath);
        if (mainPackage == null || mainPackage.isEmpty()) {
            return null;
        }
        ArchSpec spec = ArchSpecLoader.load(arch);
        Map<String, Set<String>> classDeps = javaClassAnalyzer.buildClassDependencyGraph(projectPath, mainPackage);
        return archChecker.check(spec, classDeps);
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @SuppressWarnings("SpringMVCViewInspection")
    @PostMapping("/scan")
    public String scan(@RequestParam String path, @RequestParam(defaultValue = "") String arch, Model model) {
        try {
            Map<String, PackageMetrics> metrics = springBootPackageScanner.scanProject(path);
            model.addAttribute("metrics", metrics);
            model.addAttribute("cycles", cycleDetector.findCycles(metrics));
            model.addAttribute("architecture", checkArchitecture(path, arch));
            model.addAttribute("arch", arch);
            return "graph :: graph";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", "Error scanning project: " + e.getMessage());
            return "graph :: error";
        }
    }

    /**
     * Machine-consumable export: scans the project at {@code path} and returns the metrics as a
     * self-describing JSON envelope so another system can fetch and verify the results.
     */
    @GetMapping(value = "/api/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> exportMetrics(@RequestParam String path, @RequestParam(defaultValue = "") String arch) {
        try {
            Map<String, PackageMetrics> metrics = springBootPackageScanner.scanProject(path);
            List<List<String>> cycles = cycleDetector.findCycles(metrics);
            MetricsExport export = MetricsExport.from(path, toolVersion, metrics)
                    .withCycles(cycles)
                    .withArchitecture(checkArchitecture(path, arch));
            return ResponseEntity.ok(export);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
