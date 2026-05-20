# Document Chunking Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve RAG document chunking with heading-path context, semantic paragraph/sentence boundaries, fallback fixed splitting, and richer metadata.

**Architecture:** Keep the existing `DocumentChunkService` entrypoint so upload/index flow remains unchanged. Extend `DocumentChunk` with structural metadata and have `VectorIndexService` persist those fields into Milvus metadata.

**Tech Stack:** Spring Boot, JUnit 5, AssertJ, Milvus metadata JSON, Java regex-based Markdown parsing.

---

### Task 1: Add Tests For Structural Chunking

**Files:**
- Modify: `src/test/java/org/example/service/DocumentChunkServiceTest.java`

- [ ] Add tests proving heading paths are preserved, context is prepended to chunk content, long paragraphs are split below max size, and chunk strategies are visible.
- [ ] Run `mvn -q -Dtest=DocumentChunkServiceTest test` and verify it fails before implementation.

### Task 2: Implement Chunk Metadata

**Files:**
- Modify: `src/main/java/org/example/dto/DocumentChunk.java`

- [ ] Add `headingPath`, `headingLevel`, and `chunkStrategy` fields with Lombok accessors.
- [ ] Keep existing constructor and fields compatible.

### Task 3: Replace Chunking Logic

**Files:**
- Modify: `src/main/java/org/example/service/DocumentChunkService.java`

- [ ] Parse Markdown heading hierarchy with a stack.
- [ ] Split large sections by paragraph first, sentence second, fixed length last.
- [ ] Prepend heading path to embedded chunk text.
- [ ] Generate sentence-aware overlap.
- [ ] Use readable UTF-8 comments and logs.

### Task 4: Persist Metadata

**Files:**
- Modify: `src/main/java/org/example/service/VectorIndexService.java`

- [ ] Add `headingPath`, `headingLevel`, and `chunkStrategy` to Milvus metadata when present.

### Task 5: Verify

- [ ] Run `mvn -q -Dtest=DocumentChunkServiceTest test`.
- [ ] Run `mvn -q -DskipTests compile`.
