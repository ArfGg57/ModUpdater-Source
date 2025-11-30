# ModUpdater Advanced Config Editor v2.0

A modern GUI application for editing ModUpdater configuration files with GitHub integration, version management, and a polished UI.

## Features

### GitHub Integration
- **First-Time Setup Wizard**: Configure your GitHub repository on first launch
- **Direct GitHub Sync**: Fetch and save configs directly to your GitHub repository
- **Branch Support**: Choose which branch to work with
- **API Token Support**: Optional authentication for private repositories

### Version Management
- **Version-Based Organization**: Each version has its own mods.json, files.json, and deletes.json
- **Visual Version Selection**: Grid of version cards with custom icons
- **Easy Version Creation**: Add new versions with a single click

### Mods Editor
- **Grid-Based UI**: Visual cards for each mod with icons
- **Source Support**: CurseForge, Modrinth, and direct URL downloads
- **Auto-Fill Hashes**: Automatically calculate SHA-256 hashes from URLs
- **Mod Icon Fetching**: Fetch icons from Modrinth for visual identification
- **ID Protection**: Mod IDs become read-only after creation to prevent conflicts
- **Custom Icons**: Set custom icons for each mod

### Files Editor
- **Config File Management**: Configure additional files to download
- **Auto-Hash Support**: Calculate hashes automatically
- **ZIP Extraction**: Support for extracting ZIP archives
- **Overwrite Control**: Toggle file overwriting behavior

### Delete Management
- **File/Folder Deletion**: Configure items to be deleted during updates
- **Reason Documentation**: Document why files are being deleted

### Theme Support
- **Multiple Themes**: Dark (Catppuccin), Light, Midnight Blue, Forest Green
- **Persistent Settings**: Theme choice is saved between sessions

### Conflict Prevention
- **Validation**: Check for duplicate IDs, missing fields, and other issues
- **Batch Saves**: All changes for a version are saved together to prevent rate-limiting
- **Unsaved Changes Warning**: Prompts before closing with unsaved work

## Requirements

- Python 3.8 or later
- PyQt6 (or PyQt5 as fallback)

## Installation

1. Install Python 3.8+ if not already installed

2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

   Or install PyQt6 directly:
   ```bash
   pip install PyQt6
   ```

3. Run the application:
   ```bash
   python config_editor.py
   ```

## First-Time Setup

On first launch, you'll be prompted to configure your GitHub repository:

1. **Repository URL**: Enter the full URL to your GitHub repository (e.g., `https://github.com/username/repo`)
2. **API Token**: Optional - needed for private repos or higher rate limits. Generate at GitHub → Settings → Developer settings → Personal access tokens
3. **Branch**: The branch to work with (default: `main`)
4. **Config Path**: Path within the repo where version folders are stored (e.g., `configs/`)

## Usage

### Version Selection
The main screen shows a grid of version cards. Click a version to edit it, or click the "+" button to create a new version.

### Editing a Version
Within a version, use the tabs to switch between:
- **Mods**: Add and configure mods to download
- **Files**: Add configuration files and resources
- **Delete**: Configure files/folders to remove
- **Settings**: Set custom version icon

### Adding a Mod
1. Click the "+" button in the Mods grid
2. Enter a display name and unique ID
3. Optionally select a custom icon
4. Configure the source (CurseForge, Modrinth, or URL)
5. Click "Auto-fill Hash" to calculate the file hash
6. Click "Save" to save changes

### Saving to GitHub
Click "💾 Save All" in the sidebar to push all changes to GitHub. Changes are batched per version to minimize API calls.

## Keyboard Shortcuts

- `Ctrl+R` - Refresh from GitHub
- `Ctrl+S` - Save all changes
- `Ctrl+Q` - Exit application

## File Structure

The editor expects version folders in your GitHub repository:

```
config-path/
├── 1.0.0/
│   ├── mods.json      # Array of mod entries
│   ├── files.json     # Object with "files" array
│   └── deletes.json   # Object with "deletes" array
├── 1.1.0/
│   ├── mods.json
│   ├── files.json
│   └── deletes.json
└── ...
```

## Configuration Storage

Editor settings are stored in `~/.modupdater/editor_config.json`:
- GitHub repository settings
- Selected theme
- Other preferences

## Screenshots

The editor features a clean, modern interface with:
- Sidebar navigation with connection status
- Visual grid layouts for versions, mods, and files
- Form-based editing panels
- Multiple theme options
- Validation feedback

## License

Part of the ModUpdater project. See the main repository for license information.
