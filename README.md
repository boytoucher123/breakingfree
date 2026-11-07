# BREAKING FREE

An Android app (Kotlin + Jetpack Compose) in red and black, with type that shifts between gothic script and oppressive, technical faces.

## Tabs

- **Countdown**: a spinning, worn goat-skull-and-pentagram coin above years, months, days, hours, minutes and seconds left until 1 January 2030.
- **Chaos Phase**: recent news about war, military action, shootings, terror, riots, crime and political corruption, pulled from RSS feeds. Stories are grouped **Australia → the West → elsewhere**, and you can filter by region and category.
- **Tasks** (centre tab): an offline task list with a home-screen widget that tells you what to do next. Details below.
- **Roadmap**: a campaign map you scroll upward. Majors are epochs, milestones are measurable goals (income, a house, a car) and minor milestones are tasks. Every milestone and minor milestone lives inside a major. Nodes shade from red at the start to white at the **end goal**. Past the end goal comes the purple **Beyond**. The map opens where you currently are. To reset it, use ⋮ → Restore defaults.
- **The Lattice**: one question at a time, laid over a living lattice that bends into spacetime near the top and reacts as you write. It follows the clock: brightest, bluest and most restless around 2am ("The veil is thin..."), dim and nearly still from noon to 5pm ("There's nothing here, at least for now."), with the line under the title changing through the day. You can add or delete questions (text-box or two-column table) with the gear icon. Defaults are in `app/src/main/assets/lattice_questions.json`.

## Tasks

- **Tasks** have:
  - a title and description
  - a priority
  - an **interest** level, 0 to 10 (higher is drawn a little more often by Shuffle and Easy)
  - a **size**, tiny to huge (bigger makes its deadline count for more; no deadline, no effect)
  - a **difficulty**, trivial to brutal (used by Easy)
  - tags (spaces and hyphens become `_`)
  - a due date and time
  - an **available from** date
  - **only on** weekdays
  - **do after** prerequisites
  - a **reminder**
- **Reminders** fire every chosen day, either at a set time or once at a random moment inside a time window. Each reminder notification has a **Done** button.
- **Status** is **To do**, **In progress** or **Done**. An in-progress task shows as **Blocked** while it can't be worked on, for example on a day it isn't allowed or while a prerequisite is still open. It goes back to In progress by itself.
- **Unfinished**: a task saved without a priority or without tags goes to the **Unfinished** area and is never suggested.
  - While anything is unfinished, a system task "Finish N unfinished tasks" appears. Its page lists them so you can fill them in.
  - The system task resolves itself once the area is empty.
- **Next task widget**: long-press the home screen → Widgets → BREAKING FREE → *Next task*.
  - **Next most important** steps through tasks in order of importance.
  - **Shuffle** picks a weighted random task. Higher priority, sooner deadlines, in-progress and more interesting tasks come up more often.
  - **Easy** picks a small task (tiny or small by default), with smaller and easier ones likelier. Tasks with no size set are never picked.
  - Tap the task to open it. Mark it done from the app.
  - Tasks that can't be started, and unfinished tasks, are never shown.
- **Settings** (the gear on the Tasks tab):
  - adjust every weighting behind the ranking, the shuffle and Easy
  - delete tags you don't want

The Tasks code started as Google's [architecture-samples](https://github.com/android/architecture-samples) todo app (Apache-2.0), by way of the now-retired standalone TODO app (which is why its code still lives in a `todo` package); see [NOTICE](NOTICE). Planned context-aware weighting is in [TODO.md](TODO.md).

## Getting the APK

Every push builds the app and publishes it on the repo's **Releases** page. Grab `BreakingFree.apk` from the latest release.

Each build is signed with the same key (`app/breakingfree.keystore`), so a new APK installs over the old one and keeps your data. Your roadmap, questions, drafts, reflections and tasks are stored in the app's private files.

## Building locally

Open the project in Android Studio (Ladybug or newer), or run:

```bash
./gradlew assembleDebug
```

Fonts (SIL Open Font License, from Google Fonts): UnifrakturCook, Pirata One, Anton, Share Tech Mono, Cormorant Garamond.
