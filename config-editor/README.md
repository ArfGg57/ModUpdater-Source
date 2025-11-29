# ModUpdater Advanced Config Editor

A modern GUI application for editing ModUpdater configuration files. Styled similar to Prism Launcher with a dark theme and sidebar navigation.

## Features

- **Mods Editor**: Add, edit, and remove mod entries with support for:
  - Direct URL downloads
  - CurseForge integration (projectId/fileId)
  - Modrinth integration (versionId)
  - Hash verification (SHA-256)
  - Version-based filtering (since)

- **Files Editor**: Configure additional files to download:
  - Config files
  - Resource files
  - ZIP archive extraction
  - Overwrite control

- **Deletes Editor**: Manage file/folder deletions:
  - Version range filtering (since/until)
  - File or folder deletion
  - Reason documentation

- **Main Config Editor**: Configure ModUpdater settings:
  - Remote configuration URLs
  - JSON file names
  - Version settings
  - Retry settings
  - Debug options

- **Raw JSON Editor**: Direct JSON editing with validation

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

## Usage

1. **Open Directory**: Click File → Open Directory or press Ctrl+O to select a directory containing ModUpdater config files (mods.json, files.json, deletes.json, config.json)

2. **Edit Configurations**: Use the sidebar to navigate between:
   - 📦 Mods - Edit mods.json
   - 📄 Files - Edit files.json
   - 🗑 Deletes - Edit deletes.json
   - ⚙️ Config - Edit config.json
   - 📝 Raw JSON - Direct JSON editing

3. **Save Changes**: Click the "💾 Save All" button or press Ctrl+S to save all changes

## Keyboard Shortcuts

- `Ctrl+O` - Open directory
- `Ctrl+S` - Save all files
- `Ctrl+Q` - Exit application

## Dark Theme

The editor uses a modern dark theme inspired by the Catppuccin color scheme, similar to Prism Launcher. The theme includes:

- Comfortable colors for reduced eye strain
- Clear visual hierarchy
- Consistent styling across all components

## File Structure

The config editor expects the following file structure in the selected directory:

```
config-directory/
├── mods.json      # Array of mod entries
├── files.json     # Object with "files" array
├── deletes.json   # Object with "deletes" array
└── config.json    # Main configuration object
```

## Screenshots

The editor features a clean, modern interface with:
- Sidebar navigation
- Form-based editing
- Table views for list data
- Validation feedback

## License

Part of the ModUpdater project. See the main repository for license information.
