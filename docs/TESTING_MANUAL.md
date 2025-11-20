# Manual Testing Guide for Mod Rename Fix

This guide provides step-by-step instructions to manually test the mod rename and update fixes.

## Prerequisites

1. Java development environment set up
2. Build the project: `./gradlew :modupdater-core:build`
3. A test Minecraft instance with mods folder
4. Sample `mods.json` with at least one mod entry that includes a SHA-256 hash

## Test 1: Rename Detection (No Re-download)

### Objective
Verify that renamed mods are detected by hash and not re-downloaded.

### Steps

1. **Setup:**
   ```bash
   # Ensure you have a mod in your mods.json with a hash
   # Example entry:
   {
     "numberId": "1",
     "installLocation": "mods",
     "hash": "<actual-sha256-hash>",
     "source": {
       "type": "curseforge",
       "projectId": 32274,
       "fileId": 3867367
     }
   }
   ```

2. **Initial Run:**
   - Run ModUpdater
   - Verify mod is downloaded to `/mods` folder
   - Note the filename (e.g., `JourneyMap-1.7.10.jar`)
   - Check `config/ModUpdater/mod_metadata.json` to see the entry

3. **Rename the Mod:**
   ```bash
   cd mods
   mv JourneyMap-1.7.10.jar my-custom-journeymap.jar
   ```

4. **Run ModUpdater Again:**
   - Run ModUpdater
   - Watch the console output

5. **Expected Results:**
   - ✅ Log message: "Mod not found by name; scanning directory for matching hash..."
   - ✅ Log message: "Found renamed mod by hash: my-custom-journeymap.jar"
   - ✅ File is renamed back to standard name
   - ✅ NO download occurs
   - ✅ Only ONE file exists in `/mods` folder
   - ✅ Metadata is updated with correct filename

6. **Failure Indicators:**
   - ❌ Mod is re-downloaded
   - ❌ Both renamed file and new download exist
   - ❌ No "Found renamed mod by hash" message in logs

## Test 2: Update with Renamed Old Version

### Objective
Verify that renamed old versions are properly deleted when updating to a new version.

### Steps

1. **Setup:**
   - Have a mod installed (e.g., JourneyMap v1.0)
   - Note its hash in metadata

2. **Rename the Old Version:**
   ```bash
   cd mods
   mv JourneyMap-1.0.jar keep-old-version.jar
   ```

3. **Update mods.json:**
   - Edit your `mods.json`
   - Change the `fileId` (CurseForge) or `versionId` (Modrinth) to a newer version
   - Update the `hash` to match the new version
   - Example:
   ```json
   {
     "numberId": "1",
     "hash": "<new-version-hash>",
     "source": {
       "type": "curseforge",
       "projectId": 32274,
       "fileId": 4000000  // New version ID
     }
   }
   ```

4. **Run ModUpdater:**
   - Run ModUpdater
   - Watch the console output and file system

5. **Expected Results:**
   - ✅ Log message: "Downloading mod: ..."
   - ✅ Log message: "Removing old version: keep-old-version.jar"
   - ✅ Old renamed file is deleted
   - ✅ New version is installed
   - ✅ Only ONE file exists in `/mods` folder (the new version)
   - ✅ Backup of old file exists in `modupdater/backup/`

6. **Failure Indicators:**
   - ❌ Old renamed file still exists
   - ❌ Both old and new versions are in `/mods` folder
   - ❌ No "Removing old version" message for the renamed file

## Test 3: Mod Removed, File Renamed

### Objective
Verify that user-renamed files are preserved when a mod is removed from mods.json.

### Steps

1. **Setup:**
   - Have a mod installed (e.g., JourneyMap)

2. **Rename the File:**
   ```bash
   cd mods
   mv JourneyMap-1.7.10.jar i-want-to-keep-this.jar
   ```

3. **Remove from mods.json:**
   - Edit your `mods.json`
   - Remove the mod entry entirely

4. **Run ModUpdater:**
   - Run ModUpdater
   - Watch the console output

5. **Expected Results:**
   - ✅ Log message: "Skipping unmanaged file (not installed by ModUpdater): i-want-to-keep-this.jar"
   - ✅ Renamed file is NOT deleted
   - ✅ File remains in `/mods` folder
   - ✅ User's choice to keep the file is respected

6. **Failure Indicators:**
   - ❌ Renamed file is deleted
   - ❌ No log message about skipping unmanaged file

## Debugging Tips

### Check Logs
Look for these key messages in the console output:

```
"Mod not found by name; scanning directory for matching hash..."
"Found renamed mod by hash: <filename>"
"Renamed mod to: <path>"
"Removing old version: <filename>"
"Skipping unmanaged file: <filename>"
```

### Check Metadata
View the metadata file to see what ModUpdater tracks:

```bash
cat config/ModUpdater/mod_metadata.json
# Or with formatting:
python -m json.tool < config/ModUpdater/mod_metadata.json
```

### Check Backups
All deleted files are backed up:

```bash
ls -la modupdater/backup/*/
```

### Calculate File Hash
To verify a file's hash matches what's in mods.json:

```bash
# On Linux/Mac:
sha256sum mods/filename.jar

# On Windows (PowerShell):
Get-FileHash -Algorithm SHA256 mods\filename.jar
```

## Troubleshooting

### Issue: "Hash mismatch" even though file wasn't modified

**Cause:** The hash in `mods.json` doesn't match the actual file.

**Solution:** Recalculate the hash and update `mods.json`:
```bash
sha256sum mods/filename.jar
# Copy the hash output to mods.json
```

### Issue: Mod still being re-downloaded after rename

**Possible Causes:**
1. Hash not provided in `mods.json` - add the `hash` field
2. Hash mismatch - verify hash is correct
3. Metadata corrupted - delete `config/ModUpdater/mod_metadata.json` and re-run

### Issue: Old version not being deleted

**Possible Causes:**
1. Hash not in metadata - first run may not have stored hash
2. File permissions - check that ModUpdater can delete files
3. Metadata points to wrong filename - check metadata file

## Success Criteria

All tests should show:
- ✅ No duplicate files in mods folder
- ✅ Correct log messages
- ✅ Metadata accurately reflects installed mods
- ✅ Backups created for deleted files
- ✅ User intentions respected (renamed files preserved when appropriate)
