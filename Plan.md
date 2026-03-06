# Pi Mobile Chat UX Improvement Plan

## Current State

The chat UI is functional but has a "first-pass" feel. The biggest gaps are in content
rendering and mobile-native interaction patterns. `ChatScreen.kt` is a 3,767-line monolith.

---

## Phase 1 — Quick Wins + Code Health

- [x] **1.1 Visual distinction between user and assistant messages**
  Both use the same `secondaryContainer` background. Fix:
  - User messages: keep right-aligned with current color
  - Assistant messages: use `surface` or `surfaceVariant`, left-aligned, subtle accent bar or avatar indicator

- [x] **1.2 Empty state**
  Currently plain text "No chat messages yet...". Add icon + clearer CTA.

- [x] **1.3 Break up ChatScreen.kt (3,767 lines)**
  Extract into focused files:
  - `ChatHeader.kt`
  - `ChatTimeline.kt` (message list + items)
  - `MessageCards.kt` (User/Assistant/Tool card composables)
  - `ChatInput.kt` (prompt row, image strip, streaming controls)
  - `ChatDialogs.kt` (bash, stats, model picker, tree nav)

## Phase 2 — Content Readability

- [x] **2.1 Markdown rendering**
  Only code fences are parsed today. Bold, italic, headers, lists, links, tables render as
  plain text. Options:
  - Integrate a Compose markdown library (e.g. `compose-markdown` or `multiplatform-markdown-renderer`)
  - Or build lightweight parser for the subset we need (bold, italic, code, links, lists, headers)

- [x] **2.2 Code block improvements**
  - Language label chip (from fence info string)
  - Syntax highlighting via Prism4j (already a dependency, used in DiffViewer but not code blocks)
  - Line numbers for longer blocks

## Phase 3 — Mobile-Native Patterns

- [x] **3.1 Replace AlertDialogs with ModalBottomSheets**
  Model picker, session stats, tree navigation, bash dialog — all are `AlertDialog` today.
  Bottom sheets give drag-to-dismiss, partial expansion, peek — feels more native on mobile.

- [x] **3.2 Improve the input area**
  - Command palette as inline expanding panel or bottom sheet (currently AlertDialog)
  - Haptic feedback on send
  - Swipe-to-quote/reply gesture for steering follow-ups

- [x] **3.3 Improve empty state with clearer onboarding CTA**
  Refine the Phase 1 empty state with contextual guidance (host setup, session resume hints).

## Phase 4 — Polish & Identity

- [x] **4.1 Typography and theming**
  No custom typography today. Add:
  - Monospace font for code
  - Tuned body text sizes and tighter line spacing for chat timeline
  - More distinct semantic color tokens for user/assistant/tool/thinking

- [x] **4.2 Thinking block UX**
  Currently a nested card with 280-char collapse threshold. Improve:
  - Animated expand/collapse
  - Duration or token count indicator
  - Visually distinct treatment (dashed border, muted palette)

- [x] **4.3 Tool card refinements**
  - Progress indicator while tool is running (not just streaming dot)
  - Better truncation UX for long outputs (first + last lines, expandable middle)
  - Consistent syntax highlighting (regex for tool output vs Prism4j for diffs)

---

## Execution Order

| Phase | Items | Rationale |
|-------|-------|-----------|
| **Phase 1** | 1.1, 1.2, 1.3 | Quick wins + codebase ready for larger changes |
| **Phase 2** | 2.1, 2.2 | Biggest UX impact, content readability |
| **Phase 3** | 3.1, 3.2, 3.3 | Mobile-native feel |
| **Phase 4** | 4.1, 4.2, 4.3 | Polish and identity |
