# ModUpdater Self-Update System

This document describes the self-update system that allows ModUpdater to automatically update itself between Minecraft launches.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Setup](#setup)
- [Configuration](#configuration)
- [Release Process](#release-process)
- [Signing Process](#signing-process)
- [Troubleshooting](#troubleshooting)
- [Extensibility](#extensibility)
- [Security Considerations](#security-considerations)

## Overview

The ModUpdater self-update system allows the mod to automatically detect, download, and install newer versions of itself. This ensures users always have the latest features and bug fixes without manual intervention.

### Key Features

- **Automatic Update Detection**: Checks for new versions from GitHub Releases or static manifest
- **Secure Downloads**: SHA-256 hash verification (signature verification optional)
- **Safe Installation**: Atomic JAR replacement using bootstrap launcher
- **Rollback Support**: Automatic backup and recovery on failure
- **User Control**: Configurable update prompts and intervals
- **Minimal Impact**: Runs before mod updates, doesn't interfere with gameplay

### Update Workflow

1. **Detection**: ModUpdater checks for updates at startup (configurable interval)
2. **Download**: If newer version found, downloads and verifies JAR file
3. **Staging**: Creates bootstrap launcher and stages update files
4. **Installation**: On next Minecraft launch, bootstrap replaces old JAR with new one
5. **Cleanup**: Bootstrap removes temporary files and exits
6. **Continue**: Minecraft continues launching with updated ModUpdater

## Architecture

### Components

```
com.ArfGg57.modupdater.selfupdate/
├── SelfUpdateCoordinator     - Main orchestrator
├── SelfUpdateConfig          - Configuration management
├── UpdateManifest            - Update metadata model
├── ManifestFetcher          - Fetches update info from remote sources
├── SelfUpdateDownloader     - Downloads and verifies JARs
└── BootstrapInstaller       - Creates and manages bootstrap launcher
```

### Update Sources

#### 1. GitHub Releases (Default)

Fetches latest release information from GitHub Releases API:

```
GET https://api.github.com/repos/ArfGg57/ModUpdater-Source/releases/latest
```

Required assets in release:
- `!!!!!modupdater-X.Y.Z.jar` - The JAR file
- `!!!!!modupdater-X.Y.Z.jar.sha256` - SHA-256 hash file (optional but recommended)
- `!!!!!modupdater-X.Y.Z.jar.sig` - GPG signature (optional)

#### 2. Static Manifest

Fetches from a static JSON file hosted in the repository:

```json
{
  "modUpdater": {
    "latest": "2.2.0",
    "download": {
      "primary": "https://github.com/ArfGg57/ModUpdater-Source/releases/download/v2.2.0/!!!!!modupdater-2.2.0.jar",
      "sha256": "a1b2c3d4e5f6...",
      "signature": "https://github.com/.../modupdater-2.2.0.jar.sig",
      "size": 1234567,
      "required": false
    },
    "changelog": "## Changes\n- Feature 1\n- Bug fix 2"
  }
}
```

### Bootstrap Process

The bootstrap is a platform-specific script (`.bat` for Windows, `.sh` for Unix) that:

1. Waits for Minecraft/Java to exit (5 second delay)
2. Backs up current ModUpdater JAR
3. Copies new JAR to replace old one
4. Verifies installation success
5. Cleans up temporary files
6. Exits (allows Minecraft to continue)

If the replacement fails, the bootstrap automatically restores the backup.

## Setup

### For Users

Self-update is **enabled by default**. No configuration needed for basic usage.

To customize behavior:

1. Edit `config/ModUpdater/self_update.json`:

```json
{
  "enabled": true,
  "source_type": "github_releases",
  "github_repo": "ArfGg57/ModUpdater-Source",
  "manifest_url": "https://raw.githubusercontent.com/ArfGg57/ModUpdater-Source/main/manifest.json",
  "require_signature": false,
  "auto_install": false,
  "check_interval_hours": 24,
  "public_key_path": null
}
```

2. Launch Minecraft normally

### For Developers

To enable self-update in your ModUpdater fork:

1. **Update Configuration**: Edit the default values in `SelfUpdateConfig.java`:

```java
private static final String DEFAULT_GITHUB_REPO = "YourUsername/YourRepo";
private static final String DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/YourUsername/YourRepo/main/manifest.json";
```

2. **Create Releases**: Follow the [Release Process](#release-process) below

3. **Optional**: Implement [Signing Process](#signing-process) for enhanced security

## Configuration

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable self-update feature |
| `source_type` | string | `"github_releases"` | Update source: `"github_releases"` or `"static_manifest"` |
| `github_repo` | string | `"ArfGg57/ModUpdater-Source"` | GitHub repository in format `owner/repo` |
| `manifest_url` | string | (GitHub raw URL) | URL to static manifest JSON |
| `require_signature` | boolean | `false` | Require GPG signature verification |
| `auto_install` | boolean | `false` | Install updates without user prompt |
| `check_interval_hours` | integer | `24` | Hours between update checks |
| `public_key_path` | string | `null` | Path to public key for signature verification |

### Configuration Examples

#### Disable Self-Update

```json
{
  "enabled": false
}
```

#### Use Static Manifest

```json
{
  "enabled": true,
  "source_type": "static_manifest",
  "manifest_url": "https://example.com/modupdater-manifest.json"
}
```

#### Enable Signature Verification

```json
{
  "enabled": true,
  "require_signature": true,
  "public_key_path": "config/ModUpdater/public_key.asc"
}
```

#### Aggressive Update Policy

```json
{
  "enabled": true,
  "auto_install": true,
  "check_interval_hours": 6
}
```

## Release Process

### Step 1: Prepare Release

1. **Update Version**: Edit version in `modupdater-core/build.gradle`:

```gradle
version = '2.2.0'
```

2. **Update Version Constant** (if needed): Update default version in `SelfUpdateCoordinator.java`:

```java
return "2.2.0"; // Fallback version
```

3. **Build JAR**:

```bash
./gradlew clean build
```

4. **Locate JAR**: Find in `modupdater-launchwrapper/build/libs/!!!!!modupdater-2.20.jar`

### Step 2: Generate Hash

```bash
# On Linux/Mac
sha256sum !!!!!modupdater-2.2.0.jar > !!!!!modupdater-2.2.0.jar.sha256

# On Windows (PowerShell)
Get-FileHash -Algorithm SHA256 !!!!!modupdater-2.2.0.jar | Out-File !!!!!modupdater-2.2.0.jar.sha256
```

### Step 3: Create GitHub Release

1. Go to GitHub repository
2. Click "Releases" → "Draft a new release"
3. **Tag**: `v2.2.0` (must start with `v`)
4. **Title**: `ModUpdater v2.2.0`
5. **Description**: Write changelog
6. **Upload Assets**:
   - `!!!!!modupdater-2.2.0.jar`
   - `!!!!!modupdater-2.2.0.jar.sha256`
   - (Optional) `!!!!!modupdater-2.2.0.jar.sig`
7. Publish release

### Step 4: Update Static Manifest (Optional)

If using static manifest, update `manifest.json`:

```json
{
  "modUpdater": {
    "latest": "2.2.0",
    "download": {
      "primary": "https://github.com/ArfGg57/ModUpdater-Source/releases/download/v2.2.0/!!!!!modupdater-2.2.0.jar",
      "sha256": "abc123...",
      "size": 1234567,
      "required": false
    },
    "changelog": "## Version 2.2.0\n\n- New feature\n- Bug fix"
  }
}
```

Commit and push to repository.

## Signing Process

### Why Sign Releases?

GPG signatures provide cryptographic proof that:
- The release came from the legitimate maintainer
- The JAR hasn't been tampered with
- Man-in-the-middle attacks are prevented

### Setup GPG Signing

#### 1. Generate GPG Key (if needed)

```bash
gpg --full-generate-key
```

- Choose: RSA and RSA
- Key size: 4096 bits
- Expiration: 2 years (recommended)
- Enter name and email

#### 2. Export Public Key

```bash
gpg --armor --export your.email@example.com > public_key.asc
```

Commit this to your repository for users.

#### 3. Sign JAR

```bash
gpg --armor --detach-sign !!!!!modupdater-2.2.0.jar
```

This creates `!!!!!modupdater-2.2.0.jar.asc`

#### 4. Upload to Release

Include the `.asc` file as a release asset.

### Using Signed Releases

Users configure signature verification:

```json
{
  "require_signature": true,
  "public_key_path": "config/ModUpdater/public_key.asc"
}
```

Place your `public_key.asc` in the config directory.

**Note**: Signature verification is not yet fully implemented. This is a placeholder for future enhancement.

## Troubleshooting

### Update Check Fails

**Problem**: "Failed to check for updates"

**Solutions**:
1. Check internet connection
2. Verify GitHub repository is accessible
3. Check `config/ModUpdater/self_update.json` for correct URLs
4. Review logs in `.minecraft/logs/fml-client-latest.log`

### Hash Verification Fails

**Problem**: "SHA-256 hash mismatch"

**Solutions**:
1. Re-download the update (may be corrupted)
2. Verify the `.sha256` file in the release is correct
3. Check for network proxy issues
4. Temporarily disable hash verification (not recommended):
   - Manually edit staging files (advanced users only)

### Bootstrap Fails

**Problem**: Update downloaded but not applied

**Solutions**:
1. Check permissions on `mods/` directory
2. Ensure no antivirus blocking the bootstrap script
3. Manually run bootstrap:
   - Windows: `config/ModUpdater/staging/modupdater-bootstrap.bat`
   - Linux/Mac: `config/ModUpdater/staging/modupdater-bootstrap.sh`
4. Check logs in bootstrap output

### JAR Replacement Fails

**Problem**: Bootstrap runs but JAR not updated

**Solutions**:
1. Ensure Minecraft/Java fully closed
2. Check if current JAR is locked by another process
3. Verify write permissions on `mods/` directory
4. On Windows: Exit any file explorers viewing `mods/`
5. Restore from backup manually:
   ```bash
   cd mods
   copy !!!!!modupdater-2.20.jar.backup !!!!!modupdater-2.20.jar
   ```

### Update Loop

**Problem**: Same update keeps downloading

**Solutions**:
1. Check version comparison logic
2. Verify `config/ModUpdater/modupdater_version.txt` exists and is correct
3. Manually set current version:
   ```bash
   echo "2.2.0" > config/ModUpdater/modupdater_version.txt
   ```
4. Clear last check timestamp:
   ```bash
   rm config/ModUpdater/last_update_check.txt
   ```

### Disabling Self-Update

If you need to disable self-update:

**Method 1**: Configuration
```json
{
  "enabled": false
}
```

**Method 2**: Delete config
```bash
rm config/ModUpdater/self_update.json
```

**Method 3**: Environment variable (future feature)
```bash
-Dmodupdater.selfupdate.disabled=true
```

## Extensibility

### Custom Update Source

To implement a custom update source:

1. **Extend ManifestFetcher**:

```java
public class CustomManifestFetcher extends ManifestFetcher {
    @Override
    public UpdateManifest fetchLatest(SelfUpdateConfig config) throws Exception {
        // Your custom logic
        String customUrl = config.getManifestUrl();
        // Fetch and parse
        return new UpdateManifest(...);
    }
}
```

2. **Update Configuration**:

```json
{
  "source_type": "custom",
  "manifest_url": "https://your-server.com/api/updates"
}
```

3. **Modify SelfUpdateCoordinator**: Add case for `"custom"` source type

### Custom Verification

To add additional verification (e.g., code signing):

1. **Extend SelfUpdateDownloader**:

```java
@Override
public File downloadAndVerify(UpdateManifest manifest, File stagingDir) throws Exception {
    File jar = super.downloadAndVerify(manifest, stagingDir);
    
    // Your custom verification
    if (!verifyCustomSignature(jar)) {
        throw new Exception("Custom verification failed");
    }
    
    return jar;
}
```

### Custom Bootstrap

To customize the bootstrap process:

1. **Extend BootstrapInstaller**:

```java
@Override
protected void createBootstrapJar(File bootstrapFile, File currentJar, File newJar) throws IOException {
    // Your custom bootstrap logic
    // Could create a Java JAR instead of shell script
}
```

### Hooks and Events

To hook into update events:

```java
public interface SelfUpdateListener {
    void onUpdateCheckStarted();
    void onUpdateFound(UpdateManifest manifest);
    void onDownloadStarted(UpdateManifest manifest);
    void onDownloadComplete(File jar);
    void onUpdateStaged();
    void onUpdateFailed(Exception e);
}
```

Add listeners in `SelfUpdateCoordinator` to notify on events.

## Security Considerations

### Threat Model

**Threats Mitigated**:
- ✅ Network tampering (SHA-256 hash verification)
- ✅ Corrupted downloads (file size + hash check)
- ✅ Rollback on failure (automatic backup)

**Threats Partially Mitigated**:
- ⚠️ Compromised GitHub account (signature verification not yet implemented)
- ⚠️ DNS hijacking (relies on system DNS/HTTPS)

**Threats Not Mitigated**:
- ❌ Compromised local system (if system compromised, all bets off)
- ❌ Social engineering (user manually installs malicious JAR)

### Best Practices

1. **Always Use HTTPS**: Ensure all URLs use HTTPS
2. **Verify Hashes**: Always include `.sha256` files in releases
3. **Enable Signatures**: For production use, implement and require GPG signatures
4. **Pin Certificates**: Consider certificate pinning for GitHub API
5. **Regular Updates**: Keep ModUpdater itself updated
6. **Review Logs**: Monitor update logs for suspicious activity

### Security Checklist

Before releasing:

- [ ] Version number incremented correctly
- [ ] JAR built from clean source
- [ ] SHA-256 hash generated and verified
- [ ] Release tag matches version number
- [ ] Changelog includes security fixes (if any)
- [ ] All assets uploaded correctly
- [ ] Tested update process in clean environment
- [ ] Backup/rollback tested

### Reporting Security Issues

If you discover a security vulnerability:

1. **DO NOT** open a public issue
2. Email: (add security contact email)
3. Include:
   - Description of vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## Advanced Topics

### Version Comparison

ModUpdater uses semantic versioning (SemVer) comparison:

```
2.1.0 < 2.2.0 < 2.10.0 < 3.0.0
```

Custom version schemes can be implemented by extending `VersionComparator`.

### Proxy Support

To use with HTTP proxy:

```bash
# Set JVM proxy properties
-Dhttp.proxyHost=proxy.example.com
-Dhttp.proxyPort=8080
-Dhttps.proxyHost=proxy.example.com
-Dhttps.proxyPort=8080
```

### Offline Mode

Self-update automatically skips when offline. No special configuration needed.

### Enterprise Deployment

For managed environments:

1. **Host Internal Mirror**: Mirror releases on internal server
2. **Update Manifest URL**: Point to internal server
3. **Disable Signature Verification**: If using self-signed certificates
4. **Centralized Config**: Deploy `self_update.json` via config management

Example enterprise config:

```json
{
  "enabled": true,
  "source_type": "static_manifest",
  "manifest_url": "https://internal.company.com/minecraft/modupdater/manifest.json",
  "require_signature": false,
  "auto_install": true,
  "check_interval_hours": 168
}
```

## Future Enhancements

Planned features:

- [ ] Full GPG signature verification implementation
- [ ] Delta updates (download only changed parts)
- [ ] Update rollback command
- [ ] Scheduled update windows
- [ ] Update notifications in-game
- [ ] Multiple update channels (stable, beta, alpha)
- [ ] Bandwidth throttling
- [ ] Update statistics/telemetry (opt-in)

## License

Same as ModUpdater main project.

## Contributing

See main [README.md](README.md) for contribution guidelines.

## Support

- GitHub Issues: https://github.com/ArfGg57/ModUpdater-Source/issues
- Documentation: [docs/](docs/)
- Discord: (add if available)
