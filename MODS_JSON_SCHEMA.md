# Mods.json Schema Documentation

This document describes the schema for the `mods.json` file used by ModUpdater to manage mods synchronization.

## Overview

The `mods.json` file is an array of mod entries. Each entry specifies a mod that should be installed on the client. ModUpdater uses metadata-based identification to track mods, allowing it to handle renamed files, updates, and deletions automatically.

## Schema

Each mod entry in the array has the following structure:

```json
{
  "since": "1.1.0",
  "numberId": "1",
  "installLocation": "mods",
  "file_name": "",
  "display_name": "Woof1",
  "hash": "",
  "source": {
    "type": "curseforge",
    "projectId": 255308,
    "fileId": 5789438
  }
}
```

### Fields

#### Required Fields

- **`since`** (string): The modpack version in which this mod was introduced. Used for version-based filtering.
  - Example: `"1.1.0"`

- **`numberId`** (string): A unique identifier for this mod entry. Must be unique across all mods.
  - Example: `"1"`, `"2"`, `"3"`
  - This is used to track the mod across updates and renames

- **`installLocation`** (string): The directory where the mod should be installed, relative to the game directory.
  - Example: `"mods"`, `"mods/1.7.10"`

- **`source`** (object): Specifies where to download the mod from. See [Source Types](#source-types) below.

#### Optional Fields

- **`file_name`** (string): Override the filename to save the mod as. If empty, the system will use:
  1. The `display_name` if provided (formatted as `{numberId}-{display_name}.jar`)
  2. The filename from the source API (formatted as `{numberId}-{sourceFilename}`)
  - Example: `"custom-mod-name.jar"`

- **`display_name`** (string): A human-readable name for the mod. Used for filename generation if `file_name` is not specified.
  - Example: `"Woof1"`, `"Applied Energistics 2"`

- **`hash`** (string): SHA-256 hash of the mod file for verification. Can optionally include algorithm prefix.
  - Example: `"sha256:abc123..."` or just `"abc123..."`
  - If empty, no hash verification is performed (not recommended)

## Source Types

The `source` object specifies where to download the mod from. Three types are supported:

### CurseForge

```json
"source": {
  "type": "curseforge",
  "projectId": 255308,
  "fileId": 5789438
}
```

- **`type`**: Must be `"curseforge"`
- **`projectId`** (integer): The CurseForge project ID
- **`fileId`** (integer): The CurseForge file ID for the specific version

The system will fetch the download URL and filename from the CurseForge API.

### Modrinth

```json
"source": {
  "type": "modrinth",
  "projectSlug": "fabric-api",
  "versionId": "SLiFyIon"
}
```

- **`type`**: Must be `"modrinth"`
- **`projectSlug`** (string): The Modrinth project slug (identifier)
- **`versionId`** (string): The Modrinth version ID for the specific version

The system will fetch the download URL and filename from the Modrinth API.

### Direct URL

```json
"source": {
  "type": "url",
  "url": "https://example.com/mods/mymod-1.0.jar"
}
```

- **`type`**: Must be `"url"`
- **`url`** (string): Direct download URL for the mod file

## Complete Example

```json
[
  {
    "since": "1.1.0",
    "numberId": "1",
    "installLocation": "mods",
    "file_name": "",
    "display_name": "Woof1",
    "hash": "sha256:a1b2c3d4...",
    "source": {
      "type": "curseforge",
      "projectId": 255308,
      "fileId": 5789438
    }
  },
  {
    "since": "1.1.0",
    "numberId": "2",
    "installLocation": "mods",
    "file_name": "",
    "display_name": "",
    "hash": "",
    "source": {
      "type": "modrinth",
      "projectSlug": "fabric-api",
      "versionId": "SLiFyIon"
    }
  },
  {
    "since": "1.1.1",
    "numberId": "3",
    "installLocation": "mods",
    "file_name": "custom-name.jar",
    "display_name": "My Custom Mod",
    "hash": "",
    "source": {
      "type": "curseforge",
      "projectId": 243190,
      "fileId": 4635415
    }
  }
]
```

## How Synchronization Works

1. **Metadata Tracking**: ModUpdater maintains a metadata file (`config/ModUpdater/mod_metadata.json`) that records which mods are installed, their source identifiers, and file hashes.

2. **Smart Detection**: When checking mods, the system:
   - Compares installed files against the current `mods.json`
   - Uses source identifiers (projectId/fileId or versionId) to identify mods, not filenames
   - Handles renamed files by matching metadata instead of names

3. **Cleanup**: Any files in the mods folder that:
   - Don't have a corresponding entry in `mods.json`
   - Don't match the expected version (different fileId or versionId)
   - Are tracked in metadata but reference an outdated mod
   
   ...will be backed up and removed.

4. **Installation**: Missing or outdated mods are downloaded and installed. The metadata is updated to reflect the new state.

5. **Verification**: Every run verifies that installed mods match the configuration, regardless of local/remote version numbers (which are purely cosmetic).

## Best Practices

1. **Always provide hashes** when possible for security and integrity verification
2. **Use unique numberIds** across all mods
3. **Increment `since` version** when adding or updating mods
4. **Test changes** on a development environment before deploying to production
5. **Keep backups** enabled (ModUpdater creates automatic backups)

## Migration from Old Schema

If you're migrating from the old schema that used the `name` field instead of `file_name` and `display_name`:

- The system will automatically detect mods with the old `numberId-` prefix naming
- Old mods will be added to the metadata on first sync
- You can gradually update your `mods.json` to use the new fields

## Troubleshooting

- **Mod keeps re-downloading**: Check that the hash matches and the source identifiers are correct
- **Old version not removed**: Ensure the old mod file either has the `numberId-` prefix or is in the metadata
- **Wrong file downloaded**: Verify the projectId/fileId or versionId in the source object
