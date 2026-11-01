# BREAKING FREE

An Android app (Kotlin + Jetpack Compose) in red and black, with type that shifts between gothic script and oppressive, technical faces.

## Tabs

- **Countdown**: a spinning goat-skull-and-pentagram coin above years, months, days, hours, minutes and seconds left until 1 January 2030.
- **Chaos Phase**: recent news about war, military action, shootings, terror, riots, crime and political corruption, pulled from RSS feeds. Stories are grouped **Australia → the West → elsewhere**, and you can filter by region and category.
- **Roadmap**: a campaign map you scroll upward. Majors are epochs, milestones are measurable goals (income, a house, a car) and minor milestones are tasks. Every milestone and minor milestone lives inside a major. Nodes shade from red at the start to white at the **end goal**. Past the end goal comes the purple **Beyond**. The map opens where you currently are. To reset it, use ⋮ → Restore defaults.
- **The Lattice**: one question at a time, laid over a living lattice that bends into spacetime near the top and reacts as you write. You can add or delete questions (text-box or two-column table) with the gear icon. Defaults are in `app/src/main/assets/lattice_questions.json`.

## Getting the APK

Every push builds the app and publishes it on the repo's **Releases** page. Grab `BreakingFree.apk` from the latest release.

Each build is signed with the same key (`app/breakingfree.keystore`), so a new APK installs over the old one and keeps your data. Your roadmap, questions, drafts and reflections are stored in the app's private files.

## Building locally

Open the project in Android Studio (Ladybug or newer), or run:

```bash
./gradlew assembleDebug
```

Fonts (SIL Open Font License, from Google Fonts): UnifrakturCook, Pirata One, Anton, Share Tech Mono, Cormorant Garamond.
