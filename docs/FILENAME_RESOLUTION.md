# Filename Extension Resolution

## Overview

The ModUpdater now features intelligent filename extension resolution. When a `file_name` in the configuration lacks an extension, the system automatically infers the correct one based on multiple sources.

## Problem Solved

Previously, if you specified:
```json
{
  "file_name": "betterleaves",
  "source": { "url": "https://example.com/betterleaves.jar" }
}
```

The downloaded file would be saved as `betterleaves` (no extension), which could cause issues.

Now, the system automatically detects that the file is a JAR and saves it as `betterleaves.jar`.

## Resolution Strategy

The FilenameResolver attempts to infer extensions in this order:

### 1. Check if Extension Already Exists

First, it checks if the filename already has a valid extension:
- Single-part extensions: `.{1-8 alphanumeric chars}` (e.g., `.jar`, `.txt`, `.png`)
- Multi-part extensions: `.tar.gz`, `.tar.bz2`, `.tar.xz`

If found, the filename is used as-is.

### 2. Extract from URL Path

Parses the download URL to extract the extension from the filename:
- URL: `https://host/path/mod.jar?version=1.0` → Extension: `.jar`
- URL: `https://host/download/config.json` → Extension: `.json`

Query parameters and fragments are stripped before extraction.

### 3. HTTP Content-Type Header

Performs an HTTP HEAD request to get the `Content-Type` header:

| Content-Type | Extension |
|--------------|-----------|
| `application/java-archive` | `.jar` |
| `application/zip` | `.jar` |
| `text/plain` | `.txt` |
| `application/json` | `.json` |
| `image/png` | `.png` |
| `image/jpeg` | `.jpg` |
| `image/gif` | `.gif` |
| `application/pdf` | `.pdf` |

### 4. Magic Bytes Detection

Reads the first few bytes of the file to identify its type:

| Magic Bytes | Type | Extension |
|-------------|------|-----------|
| `50 4B 03 04` | ZIP/JAR | `.jar` |
| `89 50 4E 47` | PNG | `.png` |
| `47 49 46 38` | GIF | `.gif` |
| `FF D8 FF` | JPEG | `.jpg` |
| `25 50 44 46` | PDF | `.pdf` |

### 5. Fallback

If all methods fail:
- Mods: `.jar`
- Generic files: `.jar`

## Configuration

### Enable Debug Mode

Add to your remote config JSON:
```json
{
  "debugMode": true,
  "modsJson": "mods.json",
  ...
}
```

When enabled, you'll see detailed logs:
```
[FilenameResolver] Resolving extension for: betterleaves
[FilenameResolver] Extension from URL: .jar
```

### Examples

#### Example 1: URL with Extension
```json
{
  "file_name": "myconfig",
  "url": "https://server.com/files/myconfig.json"
}
```
Result: `myconfig.json` (extracted from URL)

#### Example 2: URL without Extension, Content-Type Detection
```json
{
  "file_name": "image",
  "url": "https://cdn.example.com/download?id=12345"
}
```
If server returns `Content-Type: image/png`, result: `image.png`

#### Example 3: Fallback for Mods
```json
{
  "numberId": "5",
  "file_name": "mymod",
  "source": { "type": "url", "url": "https://server.com/obscure-download-link" }
}
```
Result: `mymod.jar` (mod fallback)

## Usage in mods.json

Works automatically for all mods:

```json
{
  "since": "1.0.0",
  "numberId": "1",
  "file_name": "customname",
  "source": {
    "type": "curseforge",
    "projectId": 12345,
    "fileId": 67890
  }
}
```

If `file_name` lacks an extension, it will be inferred from the CurseForge download URL.

## Usage in files.json

Works automatically for all files:

```json
{
  "files": [
    {
      "since": "1.0.0",
      "url": "https://server.com/download/config",
      "file_name": "myconfig",
      "downloadPath": "config/",
      "overwrite": true
    }
  ]
}
```

Extension will be inferred using the full resolution strategy.

## Limitations

1. **HTTP HEAD Requests**: Some servers don't support HEAD requests or return incorrect Content-Type. In these cases, fallback methods are used.

2. **Magic Bytes**: Only works if the file is partially downloaded or a stream can be obtained. Not always feasible for all download methods.

3. **Multi-part Extensions**: Only `.tar.gz`, `.tar.bz2`, and `.tar.xz` are recognized. Other multi-part extensions will be treated as single-part.

4. **Custom Extensions**: Extensions longer than 8 characters (excluding the dot) are not recognized and will trigger inference.

## Best Practices

1. **Include Extensions**: When possible, include the extension in `file_name` to avoid inference overhead:
   ```json
   "file_name": "mymod.jar"
   ```

2. **Use Descriptive URLs**: Prefer URLs that include the filename with extension:
   ```json
   "url": "https://server.com/files/mymod-1.2.3.jar"
   ```

3. **Enable Debug Mode**: During development/testing, enable debug mode to verify extension resolution.

4. **Test Edge Cases**: Test with files that have unusual download URLs or missing metadata.

## Technical Details

- **Implementation**: `com.ArfGg57.modupdater.resolver.FilenameResolver`
- **Integration Points**: `UpdaterCore.runUpdate()` for both mods and files
- **Performance**: HEAD requests add ~1-2 seconds per file without extension (only if URL extraction fails)
- **Caching**: No caching currently implemented; each resolution performs fresh checks

## Future Enhancements

Potential future improvements:
- Cache Content-Type results to avoid repeated HEAD requests
- Support for additional magic byte signatures
- User-configurable extension mappings
- Batch HEAD requests for improved performance
