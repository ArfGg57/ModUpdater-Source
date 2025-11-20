# ModUpdater

A Forge 1.7.10 mod that automatically updates mods, configs, and files from a remote server.

## Features

- 🔄 Automatic mod updates from CurseForge, Modrinth, or direct URLs
- 📦 Config file synchronization
- 🔍 Smart rename detection using SHA-256 hashes
- 🔒 Robust file lock handling for Windows compatibility
- ✨ Clean, modern UI for update confirmations
- 📊 Comprehensive logging and error handling

## Quick Start

See [docs/QUICK_START.md](docs/QUICK_START.md) for setup instructions.

## Documentation

- **User Guides**
  - [Quick Start Guide](docs/QUICK_START.md) - Get started quickly
  - [Mods JSON Schema](docs/MODS_JSON_SCHEMA.md) - Configuration format reference

- **Testing & Validation**
  - [Testing Guide](docs/TESTING_GUIDE.md) - General testing procedures
  - [Manual Testing](docs/TESTING_MANUAL.md) - Step-by-step manual tests
  - [Refactoring Tests](docs/TESTING_REFACTORING.md) - Tests for recent refactoring
  - [Validation Guide](docs/VALIDATION.md) - Validation procedures

- **Technical Documentation**
  - [Refactoring Guide](docs/REFACTORING_GUIDE.md) - Architecture and design
  - [Security Summary](docs/SECURITY_SUMMARY.md) - Security analysis
  - [Implementation Summary](docs/IMPLEMENTATION_SUMMARY.md) - Implementation details
  - [PR Summaries](docs/PR_SUMMARY_REFACTORING.md) - Pull request details
  - [Fix Summaries](docs/FIX_SUMMARY_v2.md) - Bug fix details

## Project Structure

```
ModUpdater-Source/
├── modupdater-core/          # Core update logic
│   └── src/main/java/com/ArfGg57/modupdater/
│       ├── hash/             # Hash utilities and rename detection
│       ├── metadata/         # Metadata management
│       └── util/             # General utilities
├── modupdater-launchwrapper/ # LaunchWrapper integration
├── modupdater-standalone/    # Standalone launcher
└── docs/                     # Documentation
```

## Building

```bash
./gradlew build
```

Requires Java 8 for compatibility with Forge 1.7.10.

## Disclaimer

This mod is largely AI-generated. The entire plan and layout was created by the repository owner, with significant effort put into refining the code and ensuring quality.

