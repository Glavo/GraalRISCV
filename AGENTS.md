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

- When IDEA MCP is available, prefer IDEA MCP for reading project files, searching files or text, inspecting symbols, and checking file problems.
- Use command-line tools for Gradle tasks, Git status/diff, and other workflows that require exact shell commands or repository-local environment variables.
- After completing a task, update `PLANS.md` to remove or revise only the work that is actually done.
- Do not delete active or still-relevant plans from `PLANS.md`; preserve pending work and update it as needed.

## Commit Messages

- After each completed modification, generate a commit message for the user, but do not run git commands to create the commit.
- Leave one blank line after the commit message body, then add `Assisted-by: codex:gpt-5.5`.
