# Performance Optimization Plan

The goal is to eliminate UI lags and improve the overall responsiveness of the SPETify app by optimizing data processing and UI rendering.

## Proposed Changes

### 1. Data Layer & Repository Optimization
Replace slow `DocumentFile` objects with a lightweight `Folder` data class and optimize metadata lookups.

#### [NEW] [Folder.kt](file:///C:/Users/konst/Desktop/SPETify/app/src/main/java/com/example/spetify/Folder.kt)
- Create a lightweight data class to replace `DocumentFile` for directory listing.

#### [MusicRepository.kt](file:///C:/Users/konst/Desktop/SPETify/app/src/main/java/com/example/spetify/MusicRepository.kt)
- Update `DirectoryContent` to use `Folder` instead of `DocumentFile`.
- Optimize `fetchFromDocumentTreeFast` to avoid expensive `DocumentFile.fromTreeUri` calls.
- Improve `enrichTrack` to avoid redundant database writes.

---

### 2. ViewModel Optimization
Switch from $O(N*M)$ linear searches to $O(N+M)$ map-based lookups in StateFlow combinations.

#### [MusicViewModel.kt](file:///C:/Users/konst/Desktop/SPETify/app/src/main/java/com/example/spetify/MusicViewModel.kt)
- Convert `allTrackMetadata` flow into a `Map<Long, TrackMetadata>` for instant lookups.
- Optimize `tracks`, `currentTrack`, and `currentQueue` flows using the metadata map.
- Reduce unnecessary StateFlow emissions.

---

### 3. UI Optimization
Improve list rendering performance and reduce recompositions.

#### [MainActivity.kt](file:///C:/Users/konst/Desktop/SPETify/app/src/main/java/com/example/spetify/MainActivity.kt)
- Add `key` parameters to all `items()` calls in `LazyColumn` for efficient list updates.
- Use `remember` for complex UI state transitions.
- Ensure `DirectoryExplorer` and `PlaylistList` use the new `Folder` and optimized track items.

## Verification Plan

### Manual Verification
- **Scroll Smoothness**: Manually scroll through a large list of tracks (100+) in both the Directory Explorer and Library to ensure no stuttering.
- **Navigation Speed**: Verify that entering and exiting folders is near-instant.
- **Metadata Update**: Check that custom album art still displays correctly after optimization.
- **Playback**: Ensure that play/pause/skip remains responsive during data enrichment.
