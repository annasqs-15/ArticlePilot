# ArticlePilot Article Format v1.0 fixtures

These fixtures are specification artifacts for the future production parser. They are intentionally plain UTF-8 `.articlepilot` files and do not contain expected serialized `Article` objects yet. The parser task should add structured expected outputs and exact diagnostic locations without changing the source documents.

The directory is versioned because fixture meaning belongs to the external format version, not to the current parser implementation.

## Layout

| Directory | Meaning |
| --- | --- |
| `valid/` | Documents that must parse successfully under v1.0 |
| `invalid/` | Documents that must fail with one or more deterministic diagnostics |
| `manifest.json` | Machine-readable case names, expected outcome, and diagnostic codes |

A valid fixture must preserve article metadata, section order, block order, multiline text, and image URL separation. An invalid fixture must not be silently repaired or accepted as v1.0.

## Naming and parser test convention

A future parser test should load each file as UTF-8, normalize line endings according to the specification, and compare the result with the manifest. Diagnostic comparisons should assert code and line at minimum, and column/path whenever the parser reports them. Tests must not rely on filesystem enumeration order; sort fixture paths by relative path before execution.

The `invalid/invalid-escaping.articlepilot` fixture contains a leading `\\q` text line. The `invalid/unterminated-text.articlepilot` fixture intentionally reaches EOF without `@end-text`. The `invalid/empty-input.articlepilot` fixture contains only blank lines.
