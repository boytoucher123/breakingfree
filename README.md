# Breaking Free

An Android app (Kotlin + Jetpack Compose) in red and black, with type that shifts between gothic script and oppressive, technical faces.

## Tabs

- **Countdown**: years, months, days, hours, minutes and seconds left until 1 January 2030, plus the total seconds remaining and how much of the decade has passed.
- **Chaos Phase**: recent news about war, military action, shootings, terror, riots, crime and political corruption, pulled from RSS feeds (Google News AU/US/UK, ABC, Guardian, BBC). Stories are grouped **Australia → the West → elsewhere**, and you can filter by region and category. Tap a story to open it.
- **Roadmap**: a campaign map you scroll upward, with linked nodes for **Major milestone → Milestone → Minor milestone**. Nodes shade from red at the start to white at the end goal. Higher tiers are brighter, and each top-level group forms a zone. The map opens at your current position, the first milestone you haven't cleared. Tap a node to clear it, edit it, add a step inside it, reorder it or delete it. The list icon switches to an outline view.
- **The Lattice**: a quiet blue tab of reflective questions, some of them two-column tables, in a new random order each time you open it. Drafts are kept until you choose "keep these thoughts". Saved reflections are listed by date.

## Getting the APK

Each push runs the **Build APK** GitHub Action. Open the run under the repo's *Actions* tab and download the `BreakingFree-apk` artifact. Install `app-release.apk` on your phone (allow installs from unknown sources).

## Building locally

Open the project in Android Studio (Ladybug or newer), or run:

```bash
./gradlew assembleDebug
```

Fonts (SIL Open Font License, from Google Fonts): UnifrakturCook, Pirata One, Anton, Share Tech Mono, Cormorant Garamond.
