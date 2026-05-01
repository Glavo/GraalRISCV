# Java Code Style Requirements

These rules apply to all Java code written or modified in this repository.

## Nullability

- Annotate every class with JetBrains Annotations `@NotNullByDefault`.
- Any type, field, parameter, return value, local variable, or generic type argument that may be `null` must be explicitly annotated with `@Nullable`.
- Nullability must never be implicit.
- Do not use Java `Optional`.
- Represent optional or absent values with `@Nullable` instead.

## Java Types

- Use Java `record` types when they fit the data model.

## Immutability Annotations

- Annotate immutable collections and arrays with JetBrains Annotations `@Unmodifiable`.
- Annotate immutable collection views with JetBrains Annotations `@UnmodifiableView`.
- Annotate immutable NIO buffers such as `ByteBuffer`, `IntBuffer`, `LongBuffer`, and other `Buffer`
  subclasses with `@Unmodifiable`.
- Annotate read-only or immutable views of NIO buffers with `@UnmodifiableView`.
- For arrays, place the annotation on the array dimension, for example `String @Unmodifiable []`.
- For multidimensional immutable arrays, annotate every immutable dimension, for example
  `int @Unmodifiable [] @Unmodifiable []`.

## Documentation

- Every class, field, and method must have documentation.
- Documentation must use `///` Markdown-style Javadoc comments.
- Document record components with `@param` entries in the record's own documentation; do not place standalone `///`
  comments on individual record components.
- Keep documentation accurate and specific to the actual behavior, constraints, and side effects.
- Add concise implementation comments inside complex logic whenever they materially improve readability or explain non-obvious behavior.

## Gradle

- When invoking Gradle in this repository, always set `GRADLE_USER_HOME` to the workspace-local `.gradle-user-home` directory.
- When invoking Gradle in this repository, always set `GRADLE_OPTS` to include
  `--enable-native-access=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED`
  for the command.
- Prefer commands such as `./gradlew -g .gradle-user-home ...` or the equivalent environment-variable-based configuration.
- When running Gradle `test` tasks, use a higher timeout of ten minutes.

## Memory Implementation

- The `Memory` implementation should target Linux-like paged virtual memory.
- Do not add or keep a long-term `MemorySegment` backend.
- Use heap `long[]` pages as the default backing allocation so host-side page data is aligned.
- Store page backing as an Unsafe base object plus byte offset, not as a direct `long[]` field, so native and file-mapped backings can be added later.
- Access page backing through `jdk.internal.misc.Unsafe`.
- Support lazy page commit, a configurable committed-page limit, software ITLB and DTLB fast paths, configurable base page size, and an independent HugeTLB pool.
- `MAP_HUGETLB` must consume the configured huge-page pool.
- `madvise(MADV_HUGEPAGE)` and `madvise(MADV_NOHUGEPAGE)` should record VMA preference only unless a later plan explicitly expands that behavior.

## Project Workflow

- When IDEA MCP is available, use IDEA MCP first for repository inspection and IDE-backed checks.
  - For reading project files, use `mcp__idea__.read_file` for targeted slices or line ranges, and `mcp__idea__.get_file_text_by_path` when whole-file text is needed.
  - For searching project text, use `mcp__idea__.search_in_files_by_text` for literal searches and `mcp__idea__.search_in_files_by_regex` for regex searches.
  - For locating files, use `mcp__idea__.find_files_by_name_keyword` when searching by filename and `mcp__idea__.find_files_by_glob` when a glob pattern is needed.
  - For checking edited Java/Kotlin/project files, use `mcp__idea__.get_file_problems` on the modified files before or alongside Gradle verification.
  - For IDE compilation checks, use `mcp__idea__.build_project` when a normal IDE build or selected-file rebuild is sufficient.
  - For Gradle task behavior, generated artifacts, repository-local environment variables, test task selection, Git status, and Git diffs, use command-line tools because exact shell commands and outputs matter.
  - Do not default to `Get-Content`, `cat`, `type`, `rg`, `grep`, `find`, or similar shell commands for project file reads or searches when IDEA MCP can perform the same operation.
- Shell-based reads or searches are acceptable only when IDEA MCP is unavailable, returns truncated or insufficient results, cannot access the needed generated/non-project file, or exact shell output is itself required.
- If shell-based reads or searches are used while IDEA MCP is available, briefly state the reason before or with the command.
- After completing a task, update `PLANS.md` to remove or revise only the work that is actually done.
- Do not delete active or still-relevant plans from `PLANS.md`; preserve pending work and update it as needed.

## Commit Messages

- After each completed modification, generate a commit message for the user, but do not run git commands to create the commit.
- Leave one blank line after the commit message body, then add `Assisted-by: codex:gpt-5.5`.
