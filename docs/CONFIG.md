# Deletion Configuration (deletes.json)

This document describes the new format for `deletes.json` used by ModUpdater to manage file and folder deletions during modpack updates.

## Overview

The `deletes.json` file allows you to specify files and folders that should be deleted at specific versions. The deletion system uses version range logic to determine when deletions apply, and includes a safety mode to restrict deletions to specific directories.

## Schema

```json
{
  "safetyMode": true,
  "deletions": [
    {
      "version": "1.5.0",
      "paths": [
        { "type": "file", "path": "config/old-config.txt" },
        { "type": "folder", "path": "config/deprecated_settings" }
      ]
    },
    {
      "version": "1.6.0",
      "paths": [
        { "type": "file", "path": "mods/LegacyMod.jar" }
      ]
    }
  ]
}
```

### Top-Level Fields

#### `safetyMode` (boolean, optional)

When `true`, only allows deletion of files and folders within the `config/` directory. Any paths outside `config/` will be skipped with a warning logged.

- **Default**: `false`
- **Recommended**: `true` for public modpacks to prevent accidental deletion of mods or other critical files

**Example:**
```json
{
  "safetyMode": true,
  ...
}
```

#### `deletions` (array, required)

An array of deletion entries, each specifying a version and the paths to delete.

### Deletion Entry Fields

Each entry in the `deletions` array has the following structure:

#### `version` (string, required)

The version at which the specified paths should be deleted. Uses semantic versioning format: `major.minor.patch`

- Patch version is optional and defaults to `0` if omitted
- Example: `"1.5.0"`, `"2.0"`, `"1.4.2"`

#### `paths` (array, required)

An array of path objects to delete at this version.

### Path Entry Fields

Each path entry has:

#### `type` (string, required)

The type of path to delete. Must be one of:
- `"file"` - Delete a single file
- `"folder"` - Delete a folder and all its contents recursively

#### `path` (string, required)

The relative path to the file or folder, from the game directory.

- Use forward slashes (`/`) as path separators
- Examples: `"config/old.txt"`, `"mods/deprecated"`, `"config/old_configs/"`

## Version Applicability Rules

A deletion entry applies if its version is **strictly between** (or equal to the target) the current version and the target version being updated to.

### Formula

Given:
- **C** = Current version (user's current modpack version)
- **T** = Target version (version being updated to)
- **D** = Delete version (version specified in deletion entry)

A deletion applies if: **D > C AND D ≤ T**

### Examples

#### Example 1: Deletion Applies
- Current version: `1.4.0`
- Target version: `1.6.0`
- Delete version: `1.5.0`

**Result**: ✅ Deletion applies (1.5.0 > 1.4.0 AND 1.5.0 ≤ 1.6.0)

#### Example 2: Deletion Does Not Apply (Before Current)
- Current version: `1.4.0`
- Target version: `1.6.0`
- Delete version: `1.2.0`

**Result**: ❌ Deletion does not apply (1.2.0 is not > 1.4.0)

#### Example 3: Deletion Applies (Equal to Target)
- Current version: `1.4.0`
- Target version: `1.6.0`
- Delete version: `1.6.0`

**Result**: ✅ Deletion applies (1.6.0 > 1.4.0 AND 1.6.0 ≤ 1.6.0)

#### Example 4: Deletion Does Not Apply (After Target)
- Current version: `1.4.0`
- Target version: `1.6.0`
- Delete version: `1.7.0`

**Result**: ❌ Deletion does not apply (1.7.0 is not ≤ 1.6.0)

## Complete Example

```json
{
  "safetyMode": true,
  "deletions": [
    {
      "version": "1.3.0",
      "paths": [
        { "type": "file", "path": "config/old-settings.cfg" },
        { "type": "folder", "path": "config/deprecated" }
      ]
    },
    {
      "version": "1.5.0",
      "paths": [
        { "type": "file", "path": "config/legacy.json" },
        { "type": "file", "path": "config/another-old-file.txt" }
      ]
    },
    {
      "version": "2.0.0",
      "paths": [
        { "type": "folder", "path": "config/v1-configs" }
      ]
    }
  ]
}
```

## Behavior Details

### Execution Timing

Deletions are executed:
1. After fetching the remote configuration
2. Before downloading new files or mods
3. Only once per update cycle (tracked in metadata)

### Deletion Tracking

Each deletion is tracked in the metadata file (`mod_metadata.json`). Once a path is successfully deleted, it is marked as completed and will not be attempted again in future updates, even if the version range still applies.

### Error Handling

- **Malformed versions**: Entries with invalid version strings are skipped with a warning
- **Non-existent paths**: If a path doesn't exist, it's marked as completed (no error)
- **Permission errors**: Logged and other deletions continue
- **Locked files**: Scheduled for deletion on next startup using `deleteOnExit`

### Safety Mode

When `safetyMode` is enabled:
- Only paths starting with `config/` are eligible for deletion
- Paths outside `config/` are skipped with a warning logged
- Helps prevent accidental deletion of mods or critical game files

**Warning logged when path is blocked:**
```
WARNING: Safety mode enabled - skipping deletion outside config/ directory: mods/SomeMod.jar
```

## Legacy Format Support

If ModUpdater detects the old deletes.json format (which used `"since"` field), it will:
1. Log a warning about the legacy format
2. Skip all deletions
3. Recommend migrating to the new format

**Legacy format example (no longer supported):**
```json
{
  "deletes": [
    {
      "since": "1.5.0",
      "paths": ["config/old.txt"]
    }
  ]
}
```

**Migration**: Convert to new format by:
1. Adding top-level `safetyMode` field
2. Renaming `since` to `version`
3. Converting path strings to path objects with `type` and `path` fields

## Best Practices

1. **Use Safety Mode**: Enable `safetyMode: true` for public modpacks to prevent accidental deletions

2. **Version Carefully**: Ensure deletion versions align with your modpack versioning scheme

3. **Test Deletions**: Test deletion configurations in a staging environment before deploying to users

4. **Document Deletions**: Add comments (outside JSON) explaining why specific paths are being deleted

5. **Minimize Deletions**: Only delete truly obsolete files. Consider if users might have customized configurations.

6. **Backup First**: The system automatically backs up deleted files, but users should be aware

## Troubleshooting

### Deletions Not Applying

- Check that the deletion version is in the correct range (> current AND ≤ target)
- Verify the JSON format is valid (use a JSON validator)
- Check logs for malformed version warnings
- Ensure paths use forward slashes

### Safety Mode Blocking Deletions

- Verify paths start with `config/` when safety mode is enabled
- Consider disabling safety mode if you need to delete files outside `config/`
- Check logs for safety mode warnings

### File Already Deleted but Listed Again

- This is normal - deletions are tracked and won't execute twice
- Check metadata file to see completion status

## See Also

- [Mods JSON Schema](MODS_JSON_SCHEMA.md)
- [Quick Start Guide](QUICK_START.md)
- [Main README](../README.md)
