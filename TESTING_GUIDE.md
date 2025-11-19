# Testing Guide for Mod Rename and Update Fixes

This guide helps verify that the fixes for mod renaming and updating work correctly.

## Prerequisites
- All mods in `mods.json` must have `hash` values (SHA-256)
- ModUpdater has been run at least once to populate metadata

## Test Scenarios

### Test 1: Manual File Rename Detection

**Setup:**
1. Run ModUpdater to install a mod (e.g., JourneyMap)
2. Check that `config/ModUpdater/mod_metadata.json` has an entry for it
3. Note the installed filename (e.g., `journeymap-1.7.10.jar`)

**Test:**
1. Manually rename the file in `/mods` folder (e.g., to `journeymap-renamed.jar`)
2. Run ModUpdater again

**Expected Result:**
- ModUpdater logs: "Mod in metadata but file missing: journeymap-1.7.10.jar; scanning for renamed file..."
- ModUpdater logs: "Found renamed mod by hash: journeymap-renamed.jar"
- ModUpdater logs: "Renamed mod to: <expected name>"
- File is renamed back to standard name
- NO re-download occurs
- Metadata is updated with the correct filename

**Failure (old behavior):**
- ModUpdater would download the mod again as if it's missing

---

### Test 2: Mod Update (Version Change)

**Setup:**
1. Install a mod with fileId=1000 (for example)
2. Check `config/ModUpdater/mod_metadata.json` has the entry with fileId=1000

**Test:**
1. Update `mods.json` to change the mod's `fileId` to 2000 (different version)
2. Also update the `hash` value to match the new version
3. Run ModUpdater

**Expected Result:**
- ModUpdater detects the source doesn't match metadata
- ModUpdater downloads the new version
- ModUpdater logs: "Removing old version: <old file>"
- Old file is backed up and deleted
- New file is installed
- Metadata is updated with new fileId and hash

**Failure (old behavior):**
- New version would be downloaded
- Old version would remain in the mods folder (duplicate)

---

### Test 3: Combined Test (Rename + Update)

**Setup:**
1. Install a mod with fileId=1000
2. Manually rename the file in `/mods`

**Test:**
1. Update `mods.json` to fileId=2000 with new hash
2. Run ModUpdater

**Expected Result:**
- ModUpdater finds the renamed file by hash
- Detects it's the wrong version (hash mismatch)
- Downloads new version
- Removes the renamed old version
- Installs new version correctly

---

## Verification Checklist

After running tests, verify:

- [ ] No duplicate mods in `/mods` folder
- [ ] `config/ModUpdater/mod_metadata.json` is accurate
- [ ] Backup in `modupdater/backup/` contains old versions
- [ ] ModUpdater logs show correct detection and operations
- [ ] All mods match the current `mods.json` configuration

## Troubleshooting

**Problem:** Mod keeps re-downloading
- Check that the `hash` in mods.json matches the actual file hash
- Verify metadata file is not corrupted

**Problem:** Old version not deleted
- Check ModUpdater logs for "Removing old version" message
- Verify the old file is tracked in metadata
- Check that the numberId matches between old and new entries

**Problem:** "File missing" even though it exists
- Ensure the file isn't tracked by a different numberId in metadata
- Check file permissions
- Verify the installLocation path is correct

## Debug Commands

Get hash of a file:
```bash
sha256sum /path/to/mod.jar
```

View metadata:
```bash
cat config/ModUpdater/mod_metadata.json | python -m json.tool
```

Check ModUpdater logs (if saved to file):
```bash
tail -f modupdater.log
```
