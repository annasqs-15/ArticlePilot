# ArticlePilot Article Format v1.0 fixtures

These fixtures are versioned specification artifacts consumed by the ArticlePilot Format v1.0 production parser. They remain plain UTF-8 `.articlepilot` files so they can be copied into parser tests, documentation, and future tooling without conversion. Structured `Article` expectations are asserted by the parser test suite, while invalid cases and diagnostic codes are declared in `manifest.json`.

The directory is versioned because fixture meaning belongs to the external format version, not to the current parser implementation.

## Layout

| Directory | Meaning |
| --- | --- |
| `valid/` | Documents that must parse successfully under v1.0 |
| `invalid/` | Documents that must fail with one or more deterministic diagnostics |
| `manifest.json` | Machine-readable case names, expected outcome, and diagnostic codes |

A valid fixture must preserve article metadata, section order, block order, multiline text, and image URL separation. An invalid fixture must not be silently repaired or accepted as v1.0.

## Naming and parser test convention

The parser test suite loads each file as UTF-8 through the Gradle test-resource path, normalizes line endings according to the specification, and compares the result with `manifest.json`. Valid cases additionally assert structured metadata, cover/image fields, block ordering, multiline text, Unicode, escaping, and deterministic IDs. Invalid cases assert diagnostic code order and source locations where applicable. Tests must not rely on filesystem enumeration order; manifest order is the deterministic case order.

The `invalid/invalid-escaping.articlepilot` fixture contains a leading `\\q` text line. The `invalid/unterminated-text.articlepilot` fixture intentionally reaches EOF without `@end-text`. The `invalid/empty-input.articlepilot` fixture contains only blank lines.
