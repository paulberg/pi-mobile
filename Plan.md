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
| **Phase 5** | 5.1, 5.2 | New session in custom directory |
| **Phase 6** | 6.1, 6.2 | Chat header compaction + scroll-aware collapse |

---

## Phase 5 — Custom Working Directory for New Sessions

### Problem

When tapping "New" on the Sessions screen, the app creates a session in a cwd resolved
from existing session groups (`resolveConnectionCwd` in `SessionsViewModel.kt:699`). There
is no way to start a session in a directory that doesn't already have sessions. If the
resolved cwd is locked by another client, the user gets a `control_lock_denied` error with
no recourse.

### Background

- The bridge protocol fully supports arbitrary cwds — `bridge_set_cwd` accepts any valid
  path on the host machine. No bridge changes needed.
- The `CwdChipSelector` (`SessionsScreen.kt:435`) already lets users pick among existing
  session-group cwds, but there's no option to enter a new path.
- `SessionsViewModel.newSession()` (`SessionsViewModel.kt:138`) calls
  `resolveConnectionCwdForHost()` which falls back through: selected chip cwd → warm
  connection cwd → first group cwd → `"/home/user"`.

### Plan

- [x] **5.1 "Custom directory" chip + input sheet**

  Add an affordance to enter an arbitrary directory path when creating a new session:

  1. **Add a "+" chip** at the end of the `CwdChipSelector` `LazyRow` (after the existing
     group chips). Tapping it opens a `ModalBottomSheet` with a text field for entering a
     directory path.

  2. **`CustomCwdSheet` composable** in `SessionsScreen.kt`:
     - Text field with placeholder "e.g. /home/rogue/git/my-project"
     - "Start Session" button that calls a new callback `onNewSessionWithCwd(cwd: String)`
     - Basic validation: non-blank, starts with `/`
     - The sheet dismisses on submit

  3. **Wire the callback** through `SessionsScreenCallbacks` →
     `SessionsViewModel.newSessionWithCwd(cwd: String)`:
     - Same logic as `newSession()` but uses the provided `cwd` directly instead of
       `resolveConnectionCwdForHost()`
     - On success, persist the cwd via `SessionCwdPreferenceStore` so it becomes the
       selected cwd for subsequent operations

  **Files changed:**
  - `SessionsScreen.kt` — add "+" chip, `CustomCwdSheet`, new callback
  - `SessionsViewModel.kt` — add `newSessionWithCwd(cwd)` method

- [x] **5.2 Recent directories + autocomplete**

  After 5.1 is functional, improve discoverability:

  1. **Persist recent custom cwds** in `SessionCwdPreferenceStore` (or a new
     `RecentCwdStore`). Cap at ~10 entries, most-recent-first.

  2. **Show recent cwds as suggestions** in the `CustomCwdSheet` below the text field —
     tappable chips or a short list. Tapping one fills the text field.

  3. **Show cwds from other hosts** as suggestions (the user may have sessions on the same
     machine via a different host profile pointing to the same bridge).

  **Files changed:**
  - `SessionCwdPreferenceStore.kt` (or new `RecentCwdStore.kt`) — persist recent cwds
  - `SessionsScreen.kt` — suggestion chips in `CustomCwdSheet`
  - `SessionsViewModel.kt` — expose recent cwds in UI state

---

## Phase 6 — Chat Header Compaction + Scroll-Aware Collapse

### Problem

The chat header currently occupies ~140-160dp of fixed vertical space across 3 rows:

1. **Row 1:** "Chat" title + sync/refresh icon + overflow menu (⋮)
2. **Row 2:** Model button (`OutlinedButton`, full-width) + Thinking level button
3. **Row 3:** Context usage chip (`AssistChip`) + "Refresh" `TextButton`

This is ~25-30% of the visible screen on a typical phone. The header never scrolls away,
so the message area is permanently reduced. On shorter devices or with the keyboard open
the situation is worse.

### Background

- `ChatHeader` (`ChatHeader.kt:82`) is a fixed element in the outer `Column` of
  `ChatScreenContent` (`ChatScreen.kt:313`). It is **not** inside the `LazyColumn`.
- The scrollable message area is a `Box(weight=1f)` between the header and the input bar.
- The `LazyListState` currently lives inside `ChatTimeline` (`ChatTimeline.kt:420`),
  inaccessible to the header or the parent `ChatScreenContent`.
- `ModelThinkingControls` (`ChatHeader.kt:242`) renders the model and thinking level as
  two `OutlinedButton`s in a `Row`. The context row is a separate `Row` below it with an
  `AssistChip` and a `TextButton("Refresh")`.

### Plan

- [x] **6.1 Compact single-row controls**

  Collapse the model, thinking level, context usage, and refresh into a **single dense
  row** of compact chips, eliminating one full row (~44dp):

  | Current | Proposed |
  |---|---|
  | `OutlinedButton` "⟳ GPT-5.3 Codex" (weight=1f, tall) | Compact `AssistChip` — model name only, no refresh icon |
  | `OutlinedButton` "☰ MEDIUM ▾" | Compact `AssistChip` — abbreviated label (e.g. "MED") with dropdown arrow |
  | `AssistChip` "Ctx ~198.9K/272.0K · $0.665" | Compact `AssistChip` — shortened to "Ctx 73% · $0.67" |
  | `TextButton` "Refresh" | `IconButton` with refresh icon (no text), 32dp |

  All 4 elements in a single `Row(Arrangement.spacedBy(6.dp))`. The first 3 chips use
  `weight(1f)` to share horizontal space evenly; the refresh icon button is fixed-width.

  Keep the existing tap behaviors:
  - Model chip → opens model picker sheet
  - Thinking chip → opens thinking level dropdown
  - Context chip → opens stats sheet
  - Refresh icon → `callbacks.onRefreshStats`

  **Files changed:**
  - `ChatHeader.kt` — rewrite `ModelThinkingControls`, remove separate context/refresh
    row, merge into single `CompactControlsRow` composable

- [x] **6.2 Scroll-aware header collapse/expand**

  Make the header hide when scrolling forward (reading newer messages) and reappear when
  scrolling backward, using the classic "collapsing toolbar" pattern.

  **Architecture changes:**

  1. **Hoist `LazyListState`** — move `rememberLazyListState()` from `ChatTimeline` up to
     `ChatScreenContent` and pass it down through `ChatBody` → `ChatTimeline` →
     `ChatTimelineList`. This makes the scroll position accessible at the level where the
     header lives.

  2. **Track scroll direction** — add a `rememberScrollDirection(listState)` composable
     that uses `derivedStateOf` on `firstVisibleItemIndex` + `firstVisibleItemScrollOffset`
     to emit `UP` / `DOWN` / `IDLE`. Compare against previous frame values.

  3. **Animate header visibility** — wrap the header (below the title row) in
     `AnimatedVisibility` with `slideInVertically` / `slideOutVertically` + fade,
     ~200ms duration. The title row ("Chat" + sync + overflow) always remains visible;
     only the controls row collapses.

  4. **Behavior rules:**
     - Scroll **down** in the list (finger drags up, reading forward) → **hide** controls
     - Scroll **up** in the list (finger drags down, going backward) → **show** controls
     - At the very top of the list (firstVisibleItemIndex == 0) → always show
     - During active run (`isRunActive`) → always show (user needs model/context info)
     - When list is empty → always show

  **Files changed:**
  - `ChatScreen.kt` (`ChatScreenContent`) — hoist `LazyListState`, add scroll-direction
    tracking, wrap header controls in `AnimatedVisibility`
  - `ChatTimeline.kt` — accept `LazyListState` as parameter instead of creating internally
  - `ChatHeader.kt` — split into always-visible title row and collapsible controls row,
    expose a parameter like `showControls: Boolean`

  **Caveats:**
  - `ExtensionWidgets` above/below the editor also sit outside the scroll area. If the
    controls collapse, the extension widgets above the editor should collapse too.
  - The "Jump to latest" button positioning is inside the `Box(weight=1f)` so it will
    naturally adjust when the box grows.
  - All chips must maintain 48dp minimum touch target per Material guidelines.
