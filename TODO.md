# TODO (Tasks tab)

## Context-aware weighting for the Shuffle pick

Right now `Recommender.weight()` (app/src/main/java/com/breakingfree/app/todo/domain/Recommender.kt) only
looks at priority, how close the deadline is (scaled by size), whether the task is in progress and
how interested you are (all tunable in Tasks settings). Planned: let the user's situation change the odds.

### Time of day
- Tasks now have a **difficulty** and a **size**, which the Easy button already uses. Reuse them here.
- In the evening (say after 8pm) or very early, multiply the weight of quick/low-effort tasks up
  and deep-work tasks down, since the user is likely to have less energy.
- Optionally learn the pattern: record when tasks actually get completed (we already store
  `completedAt`) and bias towards the kinds of task the user tends to finish at that hour.
- Optional per-task "best time" windows (morning / afternoon / evening).

### Location
- Let tasks carry a **context**: home, work, out/errands, anywhere. Could reuse tags
  (e.g. `#home`, `#work`) or be a dedicated field.
- Let the user save a couple of places (home, work) with a radius.
- When the phone is at work, exclude or heavily down-weight `home` tasks (chores) and vice versa.
  "Anywhere" tasks are unaffected.
- Needs coarse location permission and a cheap way to check it (last known location when the
  widget refreshes / when Shuffle is pressed). Must keep working fully offline, with no location
  sent anywhere. If permission is denied, ignore location.

### Implementation notes
- Keep the weighting in `Recommender` pure (pass in a `Context(now, hourOfDay, place)` value),
  so it stays unit-testable on the JVM.
- These should only change Shuffle's odds. "Most important" should stay purely
  priority/deadline-based so it stays predictable. Possibly apply the location exclusion there too.

## Other ideas
- Recurring tasks.
- Reminders/notifications for deadlines (per-task reminders exist; nothing is tied to the due date yet).
- Export/import backup (the app is offline-only, so a local JSON backup file would help).
