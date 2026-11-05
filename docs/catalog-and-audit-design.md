# Course filters, tag manager and security log: design

How the course menu, its admin side and the security log work, and why. The code is the reference; this page explains it.
Prototype of all three on mock data: `docs/prototype/` (one React + Tailwind file). SQL Server version of the tables:
`docs/sqlserver-ddl.sql`. Prop shapes: `frontend/resources/js/types/catalog.d.ts`.

## 1. Learner course menu (`/courses`, `Pages/Courses.js`)

**Progressive disclosure.** Two filters decide most choices and stay one click away: **Medium** (the pill row under the
heading, single choice; a parent medium also matches its children) and **Age group** (toggle pills beside the search, several
allowed). The finer ones sit behind **More filters** (a side drawer, full screen on phones): **Format** (live course /
workshop), **Skill level** and **Subject** (genres, shown as a tree). The button shows how many drawer filters are active.
Each option in the drawer shows how many courses it would give with the other filters kept, so nobody picks a dead end.

**Search with filters.** Filters narrow the list; the search narrows and ranks inside what is left. Matching forgives what
people actually type: case and accents are ignored, every typed word must appear (as a word, the start of one or inside one),
and words of 4+ letters may be one letter off (8+: two). Title, subtitle, medium, teacher, age group, level and genre names
are searched. With a search and the default sort, results are ordered by match quality, then popularity ("Best match").
Nothing conflicts: clearing the search keeps the filters and the other way round.

**Active state.** Every active value is a chip under the bar ("Age Junior - 12 to 15 yrs ✕"); one click removes it.
"Clear filters" removes all. The course count is announced to screen readers (`aria-live`). The whole state is in the URL
(`catalogQuery.js`, `history.replaceState`), so refresh, Back and shared links keep the view.

**Zero results.** Instead of a blank grid: "No courses match your filters", then up to three buttons, each the single change
that brings courses back and how many ("Remove age Junior · 2 courses"), and "Clear filters".

**Accessibility.** Pills are buttons with `aria-pressed`; drawer groups are `fieldset`/`legend`; the drawer is a modal dialog
that traps focus, closes on Escape and returns focus to its button; 3 px focus ring everywhere; reduced motion respected.

## 2. Admin: catalog and tag hierarchy (`Admin > Catalog`, `Pages/Admin/Taxonomies.js`)

Mediums, genres, skill levels and age groups are the filter categories. Each list is shown as a **tree** (children indented
under their parent, with the path "Landscape › Seascape"). An item's drawer has **Sits under** (parent; loops are refused
on the server). **Archive** hides an item from the learner filters and the editor's picklists but keeps it on the courses
that use it; its children move up a level; **Restore** brings it back. Delete is only possible while nothing uses the item.
Every change is in the security log with a before/after diff.

**Fast tagging** in the course editor: genres are a type-ahead picklist (`TagPicker`): type a few letters, arrow keys and
Enter add the match, Backspace removes the last chip; with an empty box it suggests the most used genres. Archived genres
stay visible (marked) on a course that already has them and can't be added again.

## 3. Admin security log (`Admin > Setup > Security log`, `Pages/Admin/SecurityAudit.js`)

**Who sees it.** Capability `security` (super admins). Export needs `security.export`. Instructors and moderators get
403 (itself logged).

**What is logged.** One stream (`audit_logs`): staff CREATE / UPDATE / DELETE (with before/after snapshots of the record,
secrets masked), AUTH (sign-ins, failures, sign-outs, revoked sessions, integrity checks), EXPORT (CSV downloads) and DENIED
(every 401/403 on the admin console). Each row: ISO timestamp (ms), user id / e-mail / role, action type and name, target
resource ("course #110"), summary, IP, country and city (CloudFront headers), device, request method and path.

**Table and filters.** Dense rows, newest first. Quick filters: free text, user (id or e-mail), role, action-type chips
(several), outcome, resource, time window (1 h / 24 h / 7 days / 30 days) or an exact from/to range. All in the URL.

**Detail drawer.** Full entry plus, for changes, a side-by-side **Field / Before / After** table; changed fields are
highlighted and "Only changed fields" hides the rest. "ID n: show all" filters the log to that user.

**Tamper evidence.** Rows are never updated or deleted by the app. Each row stores the SHA-256 of the row before it and of
its own fields; appends are serialised by a row lock on `audit_chain_head`. **Verify integrity** re-computes the chain and
names the first row that was changed, inserted or removed.

**Response to probing.** A staff session refused 5 times in 10 minutes is signed out and the staff are e-mailed; a student
session that calls the admin API is signed out at once. The admin API answers 401 JSON (`reauth`), never an HTML redirect.

## 4. Data model (MySQL, `database/schema.sql`)

| Table | Purpose |
|---|---|
| `mediums`, `genres`, `skills`, `age_groups` | filter categories; `parent_id` (nesting), `archived_at`, `status` |
| `courses` | `medium_id`, `skill_id`, `genre_id` (CSV of genre ids), `age_group` (group name), `seat_limit` |
| `audit_logs` | the security log; indices on `occurred_at`, `(actor_id, occurred_at)`, `(action_type, occurred_at)`, `(resource_type, resource_id)` |
| `audit_chain_head` | last row id and hash of the chain |

The SQL Server DDL uses a proper `course_genres` join table instead of the CSV column and adds filtered indices and an
append-only trigger; everything else maps one to one.
