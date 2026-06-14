# JSON Export & API

Scan results can be taken out of the tool as a self-describing JSON envelope — for archival or for
verification by another system.

## Ways to get it

- **REST endpoint:** `GET /api/metrics?path=<project-path>` (optionally `&arch=layered`).
- **Browser button:** after a scan, click **Export JSON** to download the same envelope.
- **CLI:** the headless CLI prints it to stdout, or to `--output=<file>`.

## Envelope shape

```json
{
  "generatedAt": "2026-06-14T15:04:05Z",
  "projectPath": "/path/to/project",
  "toolVersion": "1.0-SNAPSHOT",
  "packageCount": 14,
  "summary": { "wellDesigned": 8, "needsAttention": 6, "averageDistance": 0.48 },
  "packages": {
    "com.app.service": {
      "packageName": "com.app.service",
      "ce": 12, "ca": 3,
      "abstractClassCount": 1, "totalClassCount": 9,
      "abstractness": 0.11, "instability": 0.8, "distance": 0.09,
      "efferentDependencies": ["..."], "afferentDependencies": ["..."]
    }
  },
  "gate":   { "passed": false, "violations": [ { "type": "maxPackageDistance", "message": "..." } ] },
  "cycles": [ ["com.app.a", "com.app.b"] ],
  "architecture": { "specName": "Layered", "compliant": false,
                    "violations": [ { "type": "forbiddenDependency", "from": "Repository", "to": "Web", "message": "..." } ] }
}
```

| Section | When present |
|---------|--------------|
| `summary`, `packages` | always |
| `gate` | CLI runs (omitted from the web API) |
| `cycles` | always (empty array when acyclic) |
| `architecture` | only when `--arch` / the web dropdown is used |

!!! info "well-designed vs. needs-attention"
    `summary.wellDesigned` counts packages with `distance ≤ 0.5`; the rest "need attention".
