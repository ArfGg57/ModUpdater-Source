# Quick Start Guide: Mod Synchronization

## For Server Administrators

### 1. Update Your mods.json

Use the new schema format:

```json
[
  {
    "since": "1.1.0",
    "numberId": "1",
    "installLocation": "mods",
    "file_name": "",
    "display_name": "JourneyMap",
    "hash": "",
    "source": {
      "type": "curseforge",
      "projectId": 32274,
      "fileId": 3867367
    }
  }
]
```

**Required Fields:**
- `since`: Version when this mod was added
- `numberId`: Unique identifier (must be unique across all mods)
- `installLocation`: Directory (usually "mods")
- `source`: Where to download the mod

**Optional Fields:**
- `file_name`: Override the saved filename
- `display_name`: Human-readable name
- `hash`: SHA-256 hash for verification (recommended)

### 2. Supported Source Types

#### CurseForge
```json
"source": {
  "type": "curseforge",
  "projectId": 32274,
  "fileId": 3867367
}
```

#### Modrinth
```json
"source": {
  "type": "modrinth",
  "projectSlug": "sodium",
  "versionId": "rAfhHfow"
}
```

#### Direct URL
```json
"source": {
  "type": "url",
  "url": "https://example.com/mod.jar"
}
```

### 3. Updating Mods

When you want to update a mod to a newer version:

**Before:**
```json
{
  "numberId": "1",
  "source": {
    "type": "curseforge",
    "projectId": 32274,
    "fileId": 3867367  // old version
  }
}
```

**After:**
```json
{
  "numberId": "1",
  "source": {
    "type": "curseforge",
    "projectId": 32274,
    "fileId": 4000000  // new version
  }
}
```

The updater will:
1. Detect the fileId change
2. Remove the old version
3. Download and install the new version
4. Update the metadata

### 4. Removing Mods

To remove a mod, simply delete it from mods.json. The next time clients run the updater, the mod file will be removed from their mods folder.

### 5. Adding Mods

Add a new entry to mods.json with:
- A unique `numberId`
- The current modpack version in `since`
- Correct source information

## For Players/Clients

### What Happens on Update?

When you run the mod loader, ModUpdater will:

1. **Check Configuration**: Fetch the latest mods.json from the server
2. **Scan Mods Folder**: Identify all installed mods
3. **Compare**: Match local mods against server configuration
4. **Clean Up**: Remove any outdated or untracked mods
5. **Download**: Install any missing or updated mods
6. **Verify**: Ensure all mods match the configuration

### Metadata Cache

ModUpdater creates a file at `config/ModUpdater/mod_metadata.json` that tracks installed mods. This file:
- Helps identify mods even if renamed
- Speeds up verification
- Allows detection of outdated versions

**Don't delete this file manually** - it's automatically managed.

### Early Coremod Loading

ModUpdater now includes an early-loading coremod that processes file operations **before** Forge loads mod JARs. This provides several benefits:

#### What It Does
- **Processes Pending Operations**: Any file deletions, moves, or replacements that were deferred from the previous run are completed before mods load
- **Prevents File Locks**: By running before Forge scans and locks mod JARs, operations that would otherwise fail can now succeed
- **Reliable Updates**: Particularly beneficial on Windows where file locks can prevent deletion of active JARs

#### How It Works
1. **During Launch**: The coremod initializes before regular mods
2. **Reads Operations**: Checks `config/ModUpdater/pending-ops.json` for any pending operations
3. **Executes**: Attempts to complete all pending operations (DELETE, MOVE, REPLACE)
4. **Updates Tracking**: Removes successfully completed operations from the pending list

#### Logging
You'll see messages like these in your log:
```
[ModUpdaterCoremod] Initializing early-load phase...
[ModUpdaterCoremod] Successfully processed 3 pending operation(s)
[ModUpdaterCoremod] Early-load phase complete
```

#### Activation
The coremod is automatically activated via manifest attributes in the JAR:
- `FMLCorePlugin: com.ArfGg57.modupdater.coremod.ModUpdaterCoremod`
- `FMLCorePluginContainsFMLMod: true`
- `ForceLoadAsMod: true`

No additional configuration needed - it works automatically!

### Manual Mod Changes

#### Renamed a Mod?
✅ No problem! The updater will recognize it via metadata.

#### Deleted a Mod?
✅ No problem! The updater will re-download it.

#### Added a Mod Manually?
⚠️ If it's not in mods.json, it will be removed on next sync.

### Backups

Before any file is deleted, it's backed up to `modupdater/backup/[timestamp]/`.

You can restore from these backups if needed.

## Troubleshooting

### Issue: Mod keeps re-downloading

**Cause**: Hash mismatch or source identifiers are incorrect.

**Solution**: 
1. Check that the hash in mods.json is correct
2. Verify projectId/fileId or versionId are accurate
3. Delete `config/ModUpdater/mod_metadata.json` to rebuild cache

### Issue: Old mod version not removed

**Cause**: Mod not tracked in metadata or numberId mismatch.

**Solution**:
1. Ensure the mod has the correct `numberId` prefix in filename
2. Or delete it manually - it won't be reinstalled if not in mods.json

### Issue: Mod installed with wrong filename

**Cause**: `file_name` not specified in mods.json.

**Solution**: Add `"file_name": "desired-name.jar"` to the mod entry.

## Migration from Old System

If you're upgrading from the old system:

1. **Existing mods** with `numberId-filename.jar` pattern will be detected automatically
2. They'll be added to metadata on first run
3. No manual migration needed

## Best Practices

### For Admins:
1. ✅ Always include hashes for security
2. ✅ Use unique numberIds across all mods
3. ✅ Test changes in a development environment first
4. ✅ Keep backups of your mods.json
5. ✅ Document mod changes in version notes

### For Players:
1. ✅ Run the updater before playing
2. ✅ Don't manually delete the metadata file
3. ✅ Let the updater manage the mods folder
4. ✅ Report issues to your server admin

## Example Workflows

### Updating a Modpack Version

```json
// Old mods.json (v1.0.0)
[
  {"numberId": "1", "since": "1.0.0", "source": {"type": "curseforge", "projectId": 123, "fileId": 1000}}
]

// New mods.json (v1.1.0)
[
  {"numberId": "1", "since": "1.0.0", "source": {"type": "curseforge", "projectId": 123, "fileId": 2000}},
  {"numberId": "2", "since": "1.1.0", "source": {"type": "curseforge", "projectId": 456, "fileId": 3000}}
]
```

**Result:**
- Mod 1: Updated from fileId 1000 to 2000
- Mod 2: Newly installed

### Removing a Problematic Mod

```json
// Remove mod 2 from mods.json
[
  {"numberId": "1", "since": "1.0.0", "source": {"type": "curseforge", "projectId": 123, "fileId": 2000}}
]
```

**Result:**
- Mod 2: Backed up and removed from client mods folders

## Advanced Configuration

### Custom Install Locations

```json
{
  "numberId": "1",
  "installLocation": "mods/1.7.10",
  "source": {...}
}
```

Mods can be installed to subdirectories if your mod loader supports it.

### Multiple Mod Sources

```json
[
  {"numberId": "1", "source": {"type": "curseforge", ...}},
  {"numberId": "2", "source": {"type": "modrinth", ...}},
  {"numberId": "3", "source": {"type": "url", ...}}
]
```

Mix and match source types as needed.

## Need Help?

- See `MODS_JSON_SCHEMA.md` for complete schema reference
- See `IMPLEMENTATION_SUMMARY.md` for technical details
- See `example_mods.json` for a working example
- Check GitHub issues for known problems
