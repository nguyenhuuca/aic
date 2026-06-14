# Contributing

Conventions for changing the codebase (see `CLAUDE.md` for the full version).

## Workflow

- **Plan before coding** for any non-trivial change: state the goal, the layers/files affected, the
  approach, and how it will be tested; get sign-off before writing code.
- **Branch off `main`**, keep commits scoped, and make sure CI (`mvn -B package` on JDK 22) passes.

## Code

- **Respect the layering.** Analysis/metric logic → `core` `domain`; scan orchestration → `core`
  `application`; HTTP/views → `web` `infrastructure`. `core` stays Spring-free.
- **Constructor injection**, not field `@Autowired`. Web wires the core POJOs in
  `web/.../config/AnalysisConfig`.
- **Don't break the bytecode contract** — metrics read compiled `.class` files, never source.
- **Preserve the formulas** unless the change is explicitly about them: `I = Ce/(Ce+Ca)`,
  `A = abstract/total`, `D = |A + I − 1|`.
- **UI changes** live in `web/src/main/resources/templates/`; keep the dark-theme palette and the
  element IDs the htmx/Chart.js/D3 scripts depend on.

## Tests

Add a test with every behavioural change:

- Domain logic → focused unit tests in `core/src/test` (e.g. `ArchCheckerTest`, `ThresholdEvaluatorTest`).
- Endpoint/flow changes → extend `web` `PackageScannerControllerIT` (`@SpringBootTest` + MockMvc).

```bash
mvn clean package                         # build + run all tests (both modules)
mvn -pl core test -Dtest=ArchCheckerTest  # a single core test
```

## Verifying end-to-end

- `java -jar core/target/aic-cli.jar --scan=<compiled-project> --arch=layered` — exercise gates + arch.
- `java -jar web/target/aic-web.jar` then open <http://localhost:8081>.
