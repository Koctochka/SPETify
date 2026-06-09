# Final Directory Navigation Optimization

Despite previous optimizations, directory browsing remains slow because the app still scans and processes every file in a folder before showing any results. This plan implements a "Fast First, Rich Later" approach.

## Proposed Changes

### [Repository]

#### [MusicRepository.kt](file:///C:/Users/konst/Desktop/SPETify/app/src/main/java/com/example/spetify/MusicRepository.kt)

- **Parallel Processing:** Use `async` and `awaitAll` to scan files and extract metadata in parallel, utilizing multiple CPU cores.
- **Batched Database Access:** (Optional optimization if needed) Fetch Room metadata for the entire directory in one query instead of per-track.

### [ViewModel]

#### [MusicViewModel.kt](file:///C:/Users/konst/Desktop/SPETify/app/src/main/java/com/example/spetify/MusicViewModel.kt)

- **Two-Phase Loading:**
    1. Immediately emit a version of `DirectoryContent` containing files with basic names (from the fast SAF query).
    2. Start a background job to enrich these tracks with metadata (Room cache or file scan) and emit updates as they are ready.
- **Cancellation:** Ensure that if a user navigates into a new folder, any pending metadata extraction for the previous folder is immediately cancelled.

## Verification Plan

### Manual Verification
- Navigate into a folder with a large number of tracks (100+).
- Verify that the list of files appears almost instantly with default icons.
- Observe as metadata (artist, duration) and album art fill in shortly after.
- Verify that navigating back and forth between folders is responsive and doesn't "hang" the app.
