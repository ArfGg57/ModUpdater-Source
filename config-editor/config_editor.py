#!/usr/bin/env python3
"""
ModUpdater Advanced Config Editor v2.0

A modern GUI application for editing ModUpdater configuration files.
Features GitHub integration, version management, and a polished UI.

Features:
- GitHub Repository Integration for storing configs
- First-time setup wizard
- Version-based configuration management
- Edit mods.json - Configure mods with CurseForge/Modrinth icons
- Edit files.json - Configure files to download
- Edit deletes.json - Configure files to delete
- Settings management with themes
- Batch GitHub saves to prevent rate-limiting
- Auto-fill hashes from URLs
- Validation and conflict prevention
"""

import sys
import json
import os
import re
import html
import base64
import hashlib
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
import threading

try:
    from PyQt6.QtWidgets import (
        QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
        QListWidget, QListWidgetItem, QStackedWidget, QLabel, QPushButton,
        QLineEdit, QTextEdit, QTextBrowser, QSpinBox, QCheckBox, QComboBox, QGroupBox,
        QFormLayout, QFileDialog, QMessageBox, QScrollArea, QFrame,
        QSplitter, QTabWidget, QTableWidget, QTableWidgetItem, QHeaderView,
        QDialog, QDialogButtonBox, QTreeWidget, QTreeWidgetItem, QToolButton,
        QStyle, QSizePolicy, QGridLayout, QProgressDialog, QInputDialog,
        QMenu, QWidgetAction, QProgressBar
    )
    from PyQt6.QtCore import Qt, QSize, pyqtSignal, QThread, QTimer, QByteArray, QUrl
    from PyQt6.QtGui import QFont, QColor, QPalette, QIcon, QAction, QPixmap, QPainter, QImage, QTextDocument
    PYQT_VERSION = 6
except ImportError:
    try:
        from PyQt5.QtWidgets import (
            QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
            QListWidget, QListWidgetItem, QStackedWidget, QLabel, QPushButton,
            QLineEdit, QTextEdit, QTextBrowser, QSpinBox, QCheckBox, QComboBox, QGroupBox,
            QFormLayout, QFileDialog, QMessageBox, QScrollArea, QFrame,
            QSplitter, QTabWidget, QTableWidget, QTableWidgetItem, QHeaderView,
            QDialog, QDialogButtonBox, QTreeWidget, QTreeWidgetItem, QToolButton,
            QStyle, QSizePolicy, QGridLayout, QProgressDialog, QInputDialog,
            QMenu, QWidgetAction, QProgressBar
        )
        from PyQt5.QtCore import Qt, QSize, pyqtSignal, QThread, QTimer, QByteArray, QUrl
        from PyQt5.QtGui import QFont, QColor, QPalette, QIcon, QPixmap, QPainter, QImage, QTextDocument
        from PyQt5.QtWidgets import QAction
        PYQT_VERSION = 5
    except ImportError:
        print("Error: PyQt5 or PyQt6 is required. Install with: pip install PyQt6")
        sys.exit(1)


# === Configuration ===
APP_NAME = "ModUpdater Config Editor"
APP_VERSION = "2.0.0"
CONFIG_FILE = "editor_config.json"
CACHE_DIR = ".cache"
USER_AGENT = "ModUpdater-ConfigEditor"
DEFAULT_VERSION = "1.0.0"  # Default version for new mods/files

# Search/pagination settings
SEARCH_PAGE_SIZE = 50  # Number of mods to load per page
CURSEFORGE_MAX_PAGES = 200  # CurseForge API limit

# Minecraft version dropdown options (all official releases)
MC_VERSION_OPTIONS = [
    "",  # Empty for no filter
    # 1.21.x
    "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
    # 1.20.x
    "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
    # 1.19.x
    "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
    # 1.18.x
    "1.18.2", "1.18.1", "1.18",
    # 1.17.x
    "1.17.1", "1.17",
    # 1.16.x
    "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
    # 1.15.x
    "1.15.2", "1.15.1", "1.15",
    # 1.14.x
    "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
    # 1.13.x
    "1.13.2", "1.13.1", "1.13",
    # 1.12.x
    "1.12.2", "1.12.1", "1.12",
    # 1.11.x
    "1.11.2", "1.11.1", "1.11",
    # 1.10.x
    "1.10.2", "1.10.1", "1.10",
    # 1.9.x
    "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
    # 1.8.x
    "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
    # 1.7.x
    "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.3", "1.7.2",
    # 1.6.x
    "1.6.4", "1.6.2", "1.6.1",
    # 1.5.x
    "1.5.2", "1.5.1", "1.5",
    # 1.4.x
    "1.4.7", "1.4.6", "1.4.5", "1.4.4", "1.4.2",
    # 1.3.x
    "1.3.2", "1.3.1",
    # 1.2.x
    "1.2.5", "1.2.4", "1.2.3", "1.2.2", "1.2.1",
    # 1.1
    "1.1",
    # 1.0
    "1.0",
]

# Sort options for mod sources
CURSEFORGE_SORT_OPTIONS = {
    "Downloads": "6",
    "Popularity": "2",
    "Creation Date": "11",
    "Latest Update": "3",
    "Name (A-Z)": "4",
}

MODRINTH_SORT_OPTIONS = {
    "Downloads": "downloads",
    "Followers": "follows",
    "Date Published": "newest",
    "Date Updated": "updated",
}

# Mod loader options
MOD_LOADER_OPTIONS = {
    "Any": "",
    "Forge": "forge",
    "Fabric": "fabric",
    "NeoForge": "neoforge",
    "Quilt": "quilt",
}

# Image scaling settings
MAX_DESCRIPTION_IMAGE_WIDTH = 400  # Maximum width for images in mod descriptions

# Icon loading settings (simplified)
ICON_MAX_CONCURRENT_LOADS = 4  # Maximum number of concurrent icon downloads
ICON_LOAD_DEBOUNCE_MS = 100  # Debounce delay for scroll events (ms)

# Preloading settings
STARTUP_PRELOAD_PAGES = 1  # Number of pages to preload for each source on startup
NEXT_PAGE_PRELOAD_ICONS = 8  # Number of icons to preload from the next page

# CurseForge API configuration
# Using the curse.tools proxy for CurseForge API (doesn't require API key)
CF_PROXY_BASE_URL = "https://api.curse.tools/v1/cf"
# Direct CurseForge API key (public partner key for mod managers)
CF_API_KEY = "$2a$10$bL4bIL5pUWqfcO7KQtnMReakwtfHbNKh6v1uTpKlzhwoueEJQnPnm"



# === Custom Exceptions ===
class GitHubAPIError(Exception):
    """Custom exception for GitHub API errors."""
    def __init__(self, status_code: int, message: str):
        self.status_code = status_code
        self.message = message
        super().__init__(f"GitHub API error {status_code}: {message}")


# === Themes ===
# Built-in themes (these cannot be deleted)
BUILTIN_THEMES = {
    "dark": {
        "name": "Dark (Catppuccin)",
        "bg_primary": "#1e1e2e",
        "bg_secondary": "#313244",
        "bg_tertiary": "#45475a",
        "bg_sidebar": "#181825",
        "text_primary": "#cdd6f4",
        "text_secondary": "#a6adc8",
        "accent": "#89b4fa",
        "accent_hover": "#a6c9ff",
        "success": "#a6e3a1",
        "danger": "#f38ba8",
        "warning": "#f9e2af",
        "border": "#45475a",
    },
    "light": {
        "name": "Light",
        "bg_primary": "#eff1f5",
        "bg_secondary": "#e6e9ef",
        "bg_tertiary": "#ccd0da",
        "bg_sidebar": "#dce0e8",
        "text_primary": "#4c4f69",
        "text_secondary": "#6c6f85",
        "accent": "#1e66f5",
        "accent_hover": "#2a6ef5",
        "success": "#40a02b",
        "danger": "#d20f39",
        "warning": "#df8e1d",
        "border": "#bcc0cc",
    },
    "midnight": {
        "name": "Midnight Blue",
        "bg_primary": "#0d1117",
        "bg_secondary": "#161b22",
        "bg_tertiary": "#21262d",
        "bg_sidebar": "#010409",
        "text_primary": "#c9d1d9",
        "text_secondary": "#8b949e",
        "accent": "#58a6ff",
        "accent_hover": "#79b8ff",
        "success": "#3fb950",
        "danger": "#f85149",
        "warning": "#d29922",
        "border": "#30363d",
    },
    "forest": {
        "name": "Forest Green",
        "bg_primary": "#1a1f16",
        "bg_secondary": "#252b1f",
        "bg_tertiary": "#333b2a",
        "bg_sidebar": "#141810",
        "text_primary": "#d4dbc8",
        "text_secondary": "#a8b096",
        "accent": "#7cb342",
        "accent_hover": "#9ccc65",
        "success": "#66bb6a",
        "danger": "#ef5350",
        "warning": "#ffb74d",
        "border": "#3d4731",
    },
    "ocean": {
        "name": "Ocean Blue",
        "bg_primary": "#0a1929",
        "bg_secondary": "#132f4c",
        "bg_tertiary": "#1e4976",
        "bg_sidebar": "#051220",
        "text_primary": "#b2bac2",
        "text_secondary": "#8c959f",
        "accent": "#5090d3",
        "accent_hover": "#66a3e0",
        "success": "#4caf50",
        "danger": "#f44336",
        "warning": "#ffb300",
        "border": "#1e4976",
    },
    "sunset": {
        "name": "Sunset Orange",
        "bg_primary": "#1a1a2e",
        "bg_secondary": "#16213e",
        "bg_tertiary": "#0f3460",
        "bg_sidebar": "#0f0f1e",
        "text_primary": "#eaeaea",
        "text_secondary": "#b8b8b8",
        "accent": "#e94560",
        "accent_hover": "#ff6b6b",
        "success": "#4ecca3",
        "danger": "#ff4757",
        "warning": "#ffa502",
        "border": "#0f3460",
    },
    "purple": {
        "name": "Purple Night",
        "bg_primary": "#1a1a2e",
        "bg_secondary": "#25274d",
        "bg_tertiary": "#464866",
        "bg_sidebar": "#111122",
        "text_primary": "#aaabb8",
        "text_secondary": "#8889a0",
        "accent": "#9b59b6",
        "accent_hover": "#b975d4",
        "success": "#2ecc71",
        "danger": "#e74c3c",
        "warning": "#f1c40f",
        "border": "#464866",
    },
    "rose": {
        "name": "Rose Pink",
        "bg_primary": "#2d132c",
        "bg_secondary": "#3d1a3c",
        "bg_tertiary": "#4e234d",
        "bg_sidebar": "#1e0d1d",
        "text_primary": "#f5e6e8",
        "text_secondary": "#c9b1c4",
        "accent": "#ee6c4d",
        "accent_hover": "#f58d7a",
        "success": "#52b788",
        "danger": "#f07167",
        "warning": "#ffd166",
        "border": "#4e234d",
    },
    "nord": {
        "name": "Nord",
        "bg_primary": "#2e3440",
        "bg_secondary": "#3b4252",
        "bg_tertiary": "#434c5e",
        "bg_sidebar": "#242933",
        "text_primary": "#eceff4",
        "text_secondary": "#d8dee9",
        "accent": "#88c0d0",
        "accent_hover": "#8fbcbb",
        "success": "#a3be8c",
        "danger": "#bf616a",
        "warning": "#ebcb8b",
        "border": "#4c566a",
    },
    "dracula": {
        "name": "Dracula",
        "bg_primary": "#282a36",
        "bg_secondary": "#44475a",
        "bg_tertiary": "#6272a4",
        "bg_sidebar": "#1e1f29",
        "text_primary": "#f8f8f2",
        "text_secondary": "#6272a4",
        "accent": "#bd93f9",
        "accent_hover": "#d4b6ff",
        "success": "#50fa7b",
        "danger": "#ff5555",
        "warning": "#f1fa8c",
        "border": "#6272a4",
    },
    # New themes
    "solarized_dark": {
        "name": "Solarized Dark",
        "bg_primary": "#002b36",
        "bg_secondary": "#073642",
        "bg_tertiary": "#586e75",
        "bg_sidebar": "#001e26",
        "text_primary": "#839496",
        "text_secondary": "#657b83",
        "accent": "#268bd2",
        "accent_hover": "#2aa198",
        "success": "#859900",
        "danger": "#dc322f",
        "warning": "#b58900",
        "border": "#073642",
    },
    "solarized_light": {
        "name": "Solarized Light",
        "bg_primary": "#fdf6e3",
        "bg_secondary": "#eee8d5",
        "bg_tertiary": "#93a1a1",
        "bg_sidebar": "#eee8d5",
        "text_primary": "#657b83",
        "text_secondary": "#839496",
        "accent": "#268bd2",
        "accent_hover": "#2aa198",
        "success": "#859900",
        "danger": "#dc322f",
        "warning": "#b58900",
        "border": "#93a1a1",
    },
    "monokai": {
        "name": "Monokai",
        "bg_primary": "#272822",
        "bg_secondary": "#3e3d32",
        "bg_tertiary": "#49483e",
        "bg_sidebar": "#1e1f1c",
        "text_primary": "#f8f8f2",
        "text_secondary": "#75715e",
        "accent": "#66d9ef",
        "accent_hover": "#a6e22e",
        "success": "#a6e22e",
        "danger": "#f92672",
        "warning": "#e6db74",
        "border": "#49483e",
    },
    "gruvbox_dark": {
        "name": "Gruvbox Dark",
        "bg_primary": "#282828",
        "bg_secondary": "#3c3836",
        "bg_tertiary": "#504945",
        "bg_sidebar": "#1d2021",
        "text_primary": "#ebdbb2",
        "text_secondary": "#a89984",
        "accent": "#83a598",
        "accent_hover": "#8ec07c",
        "success": "#b8bb26",
        "danger": "#fb4934",
        "warning": "#fabd2f",
        "border": "#504945",
    },
    "high_contrast": {
        "name": "High Contrast",
        "bg_primary": "#000000",
        "bg_secondary": "#1a1a1a",
        "bg_tertiary": "#333333",
        "bg_sidebar": "#0a0a0a",
        "text_primary": "#ffffff",
        "text_secondary": "#cccccc",
        "accent": "#00ff00",
        "accent_hover": "#00cc00",
        "success": "#00ff00",
        "danger": "#ff0000",
        "warning": "#ffff00",
        "border": "#444444",
    },
    "cyberpunk": {
        "name": "Cyberpunk",
        "bg_primary": "#0d0221",
        "bg_secondary": "#190535",
        "bg_tertiary": "#2d0a4e",
        "bg_sidebar": "#060114",
        "text_primary": "#ff00ff",
        "text_secondary": "#00ffff",
        "accent": "#ff00ff",
        "accent_hover": "#ff66ff",
        "success": "#00ff00",
        "danger": "#ff0000",
        "warning": "#ffff00",
        "border": "#2d0a4e",
    },
}

# Custom themes loaded from config file (can be created/deleted by user)
_custom_themes = {}

# Combined themes dict (built-in + custom)
THEMES = dict(BUILTIN_THEMES)

# Store current theme globally for access by widgets
_current_theme = THEMES["light"]

def get_current_theme() -> dict:
    """Get the currently active theme."""
    return _current_theme

def set_current_theme(theme_key: str):
    """Set the current theme."""
    global _current_theme
    if theme_key in THEMES:
        _current_theme = THEMES[theme_key]

def load_custom_themes():
    """Load custom themes from config file."""
    global _custom_themes, THEMES
    config_path = Path.home() / ".modupdater" / "custom_themes.json"
    if config_path.exists():
        try:
            with open(config_path, 'r') as f:
                _custom_themes = json.load(f)
                # Merge with built-in themes
                THEMES = dict(BUILTIN_THEMES)
                THEMES.update(_custom_themes)
        except Exception:
            pass

def save_custom_themes():
    """Save custom themes to config file."""
    config_dir = Path.home() / ".modupdater"
    config_dir.mkdir(parents=True, exist_ok=True)
    config_path = config_dir / "custom_themes.json"
    try:
        with open(config_path, 'w') as f:
            json.dump(_custom_themes, f, indent=2)
    except Exception as e:
        print(f"Failed to save custom themes: {e}")

def add_custom_theme(key: str, theme_data: dict):
    """Add a custom theme."""
    global _custom_themes, THEMES
    _custom_themes[key] = theme_data
    THEMES[key] = theme_data
    save_custom_themes()

def delete_custom_theme(key: str) -> bool:
    """Delete a custom theme. Returns True if successful."""
    global _custom_themes, THEMES
    if key in BUILTIN_THEMES:
        return False  # Cannot delete built-in themes
    if key in _custom_themes:
        del _custom_themes[key]
        if key in THEMES:
            del THEMES[key]
        save_custom_themes()
        return True
    return False

def is_builtin_theme(key: str) -> bool:
    """Check if a theme is built-in (cannot be deleted)."""
    return key in BUILTIN_THEMES


def generate_stylesheet(theme: dict) -> str:
    """Generate a stylesheet from theme colors."""
    return f"""
QMainWindow {{
    background-color: {theme['bg_primary']};
}}

QWidget {{
    background-color: {theme['bg_primary']};
    color: {theme['text_primary']};
    font-family: "Segoe UI", "Inter", sans-serif;
    font-size: 13px;
}}

QListWidget {{
    background-color: {theme['bg_secondary']};
    border: none;
    border-radius: 8px;
    padding: 5px;
    outline: none;
    alternate-background-color: {theme['bg_primary']};
}}

QListWidget::item {{
    background-color: {theme['bg_secondary']};
    border-radius: 6px;
    padding: 12px 16px;
    margin: 2px 4px;
    color: {theme['text_primary']};
}}

QListWidget::item:alternate {{
    background-color: {theme['bg_primary']};
}}

QListWidget::item:selected {{
    background-color: {theme['accent']};
    color: {theme['bg_primary']};
}}

QListWidget::item:selected:alternate {{
    background-color: {theme['accent']};
    color: {theme['bg_primary']};
}}

QListWidget::item:hover:!selected {{
    background-color: {theme['bg_tertiary']};
}}

QPushButton {{
    background-color: {theme['bg_tertiary']};
    border: none;
    border-radius: 6px;
    padding: 10px 20px;
    color: {theme['text_primary']};
    font-weight: 600;
}}

QPushButton:hover {{
    background-color: {theme['border']};
}}

QPushButton:pressed {{
    background-color: {theme['bg_secondary']};
}}

QPushButton#primaryButton {{
    background-color: {theme['accent']};
    color: {theme['bg_primary']};
}}

QPushButton#primaryButton:hover {{
    background-color: {theme['accent_hover']};
}}

QPushButton#dangerButton {{
    background-color: {theme['danger']};
    color: {theme['bg_primary']};
}}

QPushButton#successButton {{
    background-color: {theme['success']};
    color: {theme['bg_primary']};
}}

QLineEdit, QSpinBox, QComboBox {{
    background-color: {theme['bg_secondary']};
    border: 2px solid {theme['bg_tertiary']};
    border-radius: 6px;
    padding: 6px 10px;
    color: {theme['text_primary']};
}}

QLineEdit:focus, QSpinBox:focus, QComboBox:focus {{
    border-color: {theme['accent']};
}}

QComboBox QAbstractItemView {{
    background-color: {theme['bg_secondary']};
    border: 1px solid {theme['border']};
    border-radius: 6px;
    selection-background-color: {theme['accent']};
    selection-color: {theme['bg_primary']};
    padding: 2px;
    margin: 0px;
    max-height: 300px;
}}

QComboBox QAbstractItemView::item {{
    padding: 4px 8px;
    min-height: 20px;
    margin: 0px;
}}

QComboBox QAbstractItemView::item:hover {{
    background-color: {theme['bg_tertiary']};
}}

QComboBox QAbstractItemView QScrollBar:vertical {{
    background-color: {theme['bg_secondary']};
    width: 10px;
    border-radius: 5px;
}}

QComboBox QAbstractItemView QScrollBar::handle:vertical {{
    background-color: {theme['bg_tertiary']};
    border-radius: 4px;
    min-height: 20px;
}}

QComboBox QAbstractItemView QScrollBar::handle:vertical:hover {{
    background-color: {theme['accent']};
}}

QComboBox::drop-down {{
    border: none;
    width: 24px;
}}

QLineEdit:disabled {{
    background-color: {theme['bg_tertiary']};
    color: {theme['text_secondary']};
}}

QTextEdit {{
    background-color: {theme['bg_secondary']};
    border: 2px solid {theme['bg_tertiary']};
    border-radius: 8px;
    padding: 10px;
    color: {theme['text_primary']};
    font-family: "Consolas", "Monaco", monospace;
    font-size: 12px;
}}

QTextEdit:focus {{
    border-color: {theme['accent']};
}}

QGroupBox {{
    background-color: {theme['bg_secondary']};
    border: 1px solid {theme['bg_tertiary']};
    border-radius: 8px;
    margin-top: 8px;
    padding: 8px;
    padding-top: 20px;
    font-weight: 600;
}}

QGroupBox::title {{
    subcontrol-origin: margin;
    left: 16px;
    padding: 0 8px;
    color: {theme['accent']};
}}

QLabel {{
    color: {theme['text_primary']};
    background-color: transparent;
}}

QLabel#headerLabel {{
    font-size: 20px;
    font-weight: 700;
    color: {theme['text_primary']};
    padding: 16px 0;
}}

QScrollArea {{
    border: none;
    background-color: transparent;
}}

QScrollBar:vertical {{
    background-color: {theme['bg_secondary']};
    width: 12px;
    border-radius: 6px;
}}

QScrollBar::handle:vertical {{
    background-color: {theme['bg_tertiary']};
    border-radius: 4px;
    min-height: 30px;
}}

QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0;
}}

QTabWidget::pane {{
    background-color: {theme['bg_secondary']};
    border: 1px solid {theme['bg_tertiary']};
    border-radius: 8px;
    padding: 8px;
}}

QTabBar::tab {{
    background-color: {theme['bg_primary']};
    border: none;
    padding: 10px 20px;
    margin-right: 4px;
    border-top-left-radius: 6px;
    border-top-right-radius: 6px;
}}

QTabBar::tab:selected {{
    background-color: {theme['bg_secondary']};
    color: {theme['accent']};
}}

QTabBar::tab:hover:!selected {{
    background-color: {theme['bg_tertiary']};
}}

QTableWidget {{
    background-color: {theme['bg_secondary']};
    border: 1px solid {theme['bg_tertiary']};
    border-radius: 8px;
    gridline-color: {theme['bg_tertiary']};
}}

QTableWidget::item {{
    padding: 8px;
}}

QTableWidget::item:selected {{
    background-color: {theme['bg_tertiary']};
}}

QHeaderView::section {{
    background-color: {theme['bg_primary']};
    color: {theme['accent']};
    padding: 10px;
    border: none;
    border-bottom: 2px solid {theme['bg_tertiary']};
    font-weight: 600;
}}

QCheckBox::indicator {{
    width: 20px;
    height: 20px;
    border-radius: 4px;
    border: 2px solid {theme['bg_tertiary']};
    background-color: {theme['bg_secondary']};
}}

QCheckBox::indicator:checked {{
    background-color: {theme['accent']};
    border-color: {theme['accent']};
}}

QCheckBox {{
    background-color: transparent;
    spacing: 8px;
}}

QToolTip {{
    background-color: {theme['bg_secondary']};
    color: {theme['text_primary']};
    border: 1px solid {theme['bg_tertiary']};
    border-radius: 6px;
    padding: 8px;
}}

QProgressBar {{
    background-color: {theme['bg_secondary']};
    border: none;
    border-radius: 4px;
    text-align: center;
}}

QProgressBar::chunk {{
    background-color: {theme['accent']};
    border-radius: 4px;
}}

QMenu {{
    background-color: {theme['bg_secondary']};
    border: 1px solid {theme['bg_tertiary']};
    border-radius: 8px;
    padding: 4px;
}}

QMenu::item {{
    padding: 8px 24px;
    border-radius: 4px;
}}

QMenu::item:selected {{
    background-color: {theme['bg_tertiary']};
}}

QDialog {{
    background-color: {theme['bg_primary']};
}}
"""



# === GitHub API Helper ===
class GitHubAPI:
    """Helper class for GitHub API operations."""
    
    def __init__(self, repo_url: str, token: str = ""):
        self.token = token
        self.repo_url = repo_url
        self.owner, self.repo = self._parse_repo_url(repo_url)
        self.api_base = "https://api.github.com"
        self.branch = "main"
    
    def _parse_repo_url(self, url: str) -> Tuple[str, str]:
        """Parse owner and repo from GitHub URL.
        
        Supported URL formats:
        - https://github.com/owner/repo
        - https://github.com/owner/repo.git
        - https://github.com/owner/repo/
        - git@github.com:owner/repo.git
        """
        # Pattern breakdown:
        # - github\.com[:/] - matches "github.com/" or "github.com:"
        # - ([^/]+) - captures the owner (anything except /)
        # - / - matches the separator
        # - ([^/\s]+?) - captures the repo name (non-greedy)
        # - (?:\.git)? - optionally matches ".git" suffix
        # - /?$ - optionally matches trailing slash at end
        pattern = r'github\.com[:/]([^/]+)/([^/\s]+?)(?:\.git)?/?$'
        match = re.search(pattern, url)
        if match:
            return match.group(1), match.group(2).replace('.git', '')
        raise ValueError(f"Invalid GitHub URL: {url}")
    
    def _request(self, method: str, endpoint: str, data: dict = None) -> dict:
        """Make a request to GitHub API."""
        url = f"{self.api_base}{endpoint}"
        headers = {
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": USER_AGENT
        }
        if self.token:
            headers["Authorization"] = f"token {self.token}"
        
        if data:
            body = json.dumps(data).encode('utf-8')
            headers["Content-Type"] = "application/json"
        else:
            body = None
        
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.loads(response.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            error_body = e.read().decode('utf-8') if e.fp else ""
            raise GitHubAPIError(e.code, error_body)
    
    def get_file(self, path: str) -> Tuple[str, str]:
        """Get file content and SHA from repository."""
        endpoint = f"/repos/{self.owner}/{self.repo}/contents/{path}?ref={self.branch}"
        try:
            data = self._request("GET", endpoint)
            content = base64.b64decode(data['content']).decode('utf-8')
            return content, data['sha']
        except Exception as e:
            if "404" in str(e):
                return None, None
            raise
    
    def list_directory(self, path: str = "") -> List[dict]:
        """List contents of a directory."""
        endpoint = f"/repos/{self.owner}/{self.repo}/contents/{path}?ref={self.branch}"
        try:
            return self._request("GET", endpoint)
        except Exception as e:
            if "404" in str(e):
                return []
            raise
    
    def create_or_update_file(self, path: str, content: str, message: str, sha: str = None) -> dict:
        """Create or update a file in the repository."""
        endpoint = f"/repos/{self.owner}/{self.repo}/contents/{path}"
        data = {
            "message": message,
            "content": base64.b64encode(content.encode('utf-8')).decode('utf-8'),
            "branch": self.branch
        }
        if sha:
            data["sha"] = sha
        return self._request("PUT", endpoint, data)
    
    def delete_file(self, path: str, message: str, sha: str) -> dict:
        """Delete a file from the repository."""
        endpoint = f"/repos/{self.owner}/{self.repo}/contents/{path}"
        data = {
            "message": message,
            "sha": sha,
            "branch": self.branch
        }
        return self._request("DELETE", endpoint, data)
    
    def test_connection(self) -> bool:
        """Test if the repository is accessible."""
        try:
            endpoint = f"/repos/{self.owner}/{self.repo}"
            self._request("GET", endpoint)
            return True
        except:
            return False
    
    def get_branches(self) -> List[str]:
        """Get list of branches."""
        endpoint = f"/repos/{self.owner}/{self.repo}/branches"
        try:
            branches = self._request("GET", endpoint)
            return [b['name'] for b in branches]
        except:
            return ["main"]


# === Image Loader Thread ===
class ImageLoaderThread(QThread):
    """Background thread for loading remote images."""
    image_loaded = pyqtSignal(str, QImage)  # url, image
    
    def __init__(self, url: str):
        super().__init__()
        self.url = url
        self._running = True
    
    def run(self):
        """Fetch image from URL."""
        if not self._running:
            return
        try:
            req = urllib.request.Request(self.url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=10) as response:
                data = response.read()
                if data and self._running:
                    image = QImage()
                    image.loadFromData(data)
                    if not image.isNull():
                        self.image_loaded.emit(self.url, image)
        except Exception as e:
            print(f"Failed to load image from {self.url}: {e}")
    
    def stop(self):
        self._running = False


# === Custom TextBrowser with Remote Image Support ===
class RemoteImageTextBrowser(QTextBrowser):
    """QTextBrowser subclass that can load and display remote images."""
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self._image_cache = {}  # url -> QImage
        self._pending_loads = {}  # url -> ImageLoaderThread
        self._pending_urls = set()  # URLs that need to be loaded
    
    def setHtml(self, html: str):
        """Override setHtml to preprocess and queue image loading."""
        # Clear previous pending loads and wait for them to finish
        for thread in self._pending_loads.values():
            thread.stop()
            thread.wait(100)  # Wait up to 100ms for each thread to finish
        self._pending_loads.clear()
        self._pending_urls.clear()
        
        # Scale images to fit within the browser width and ensure proper text flow
        # Add max-width, height:auto, display:block, and position:relative to prevent 
        # images from rendering over text
        def add_img_style(match):
            img_tag = match.group(0)
            # Use display:block to make images block-level elements that don't overlap text
            # Use position:relative with z-index:1 to ensure proper layering
            style_to_add = 'max-width: 100%; height: auto; display: block; position: relative; z-index: 1; margin: 8px 0;'
            # Check if style already exists
            style_match = re.search(r'style=["\']([^"\']*)["\']', img_tag, re.IGNORECASE)
            if style_match:
                # Append to existing style
                existing_style = style_match.group(1).rstrip(';')
                new_style = f'{existing_style}; {style_to_add}'
                img_tag = img_tag[:style_match.start(1)] + new_style + img_tag[style_match.end(1):]
            else:
                # Add new style attribute before the closing > or />
                if img_tag.rstrip().endswith('/>'):
                    img_tag = img_tag.rstrip()[:-2] + f' style="{style_to_add}" />'
                else:
                    img_tag = img_tag.rstrip()[:-1] + f' style="{style_to_add}">'
            return img_tag
        
        html = re.sub(r'<img[^>]*/?>', add_img_style, html, flags=re.IGNORECASE)
        
        # Find all image URLs in the HTML
        img_pattern = r'<img[^>]+src=["\']([^"\']+)["\']'
        urls = re.findall(img_pattern, html, re.IGNORECASE)
        
        # Queue loading for URLs not in cache
        for url in urls:
            if url.startswith(('http://', 'https://')):
                if url in self._image_cache:
                    # Already cached, will be served by loadResource
                    pass
                elif url not in self._pending_loads:
                    self._pending_urls.add(url)
        
        # Set the HTML first
        super().setHtml(html)
        
        # Start loading pending images
        self._start_image_loading()

    def _start_image_loading(self):
        """Start loading pending images."""
        for url in list(self._pending_urls):
            if url not in self._pending_loads:
                thread = ImageLoaderThread(url)
                thread.image_loaded.connect(self._on_image_loaded)
                # Use a no-arg lambda that captures url so finished (which emits no args) works
                thread.finished.connect(lambda url=url: self._on_load_finished(url))
                thread.finished.connect(thread.deleteLater)
                self._pending_loads[url] = thread
                thread.start()
    
    def _on_image_loaded(self, url: str, image: QImage):
        """Handle image loaded from thread."""
        # Scale large images to fit within the browser width
        if image.width() > MAX_DESCRIPTION_IMAGE_WIDTH:
            image = image.scaledToWidth(MAX_DESCRIPTION_IMAGE_WIDTH, Qt.TransformationMode.SmoothTransformation)
        self._image_cache[url] = image
        # Add the resource to the document directly to avoid full reload
        if PYQT_VERSION == 6:
            resource_type = QTextDocument.ResourceType.ImageResource
        else:
            resource_type = QTextDocument.ImageResource
        self.document().addResource(resource_type, QUrl(url), image)
        # Force document to recalculate layout and repaint to avoid images overlapping text
        # This ensures proper rendering when images load after the initial display
        doc = self.document()
        doc.markContentsDirty(0, doc.characterCount())
        self.viewport().update()
    
    def _on_load_finished(self, url: str):
        """Handle load finished."""
        if url in self._pending_loads:
            del self._pending_loads[url]
        # discard() is safe even if url is not in the set
        self._pending_urls.discard(url)
    
    def loadResource(self, type_: int, url: QUrl) -> object:
        """Override to provide cached images."""
        if PYQT_VERSION == 6:
            image_type = QTextDocument.ResourceType.ImageResource.value
        else:
            image_type = QTextDocument.ImageResource
        
        if type_ == image_type:
            url_str = url.toString()
            if url_str in self._image_cache:
                return self._image_cache[url_str]
            # Return empty image for pending loads
            if url_str.startswith(('http://', 'https://')):
                if url_str not in self._pending_urls:
                    self._pending_urls.add(url_str)
                    self._start_image_loading()
                return QImage()  # Return empty image while loading
        
        return super().loadResource(type_, url)
    
    def shutdown(self):
        """Stop and wait for all pending image load threads."""
        try:
            for thread in list(self._pending_loads.values()):
                try:
                    thread.stop()
                    thread.wait(1000)
                except Exception:
                    pass
        except Exception:
            pass
        self._pending_loads.clear()
        self._pending_urls.clear()


# === Icon Loading System ===
# Simple and lightweight icon loading system


class IconFetcher(QThread):
    """Background thread for fetching mod icons from source info."""
    icon_fetched = pyqtSignal(str, bytes)  # mod_id, icon_bytes
    fetch_complete = pyqtSignal()
    
    def __init__(self, mod_info: dict):
        super().__init__()
        self.mod_info = mod_info
        self._running = True
    
    def run(self):
        """Fetch icon from CurseForge or Modrinth."""
        source = self.mod_info.get('source', {})
        source_type = source.get('type', '')
        mod_id = self.mod_info.get('id', self.mod_info.get('numberId', ''))
        
        try:
            icon_data = None
            if source_type == 'modrinth':
                project_slug = source.get('projectSlug')
                if project_slug:
                    icon_data = self._fetch_modrinth_icon(project_slug)
            
            if icon_data and self._running:
                self.icon_fetched.emit(mod_id, icon_data)
        except (urllib.error.URLError, urllib.error.HTTPError, OSError):
            pass  # Silently ignore network errors
        
        self.fetch_complete.emit()
    
    def _fetch_modrinth_icon(self, project_slug: str) -> bytes:
        """Fetch icon from Modrinth API."""
        try:
            url = f"https://api.modrinth.com/v2/project/{project_slug}"
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read())
                icon_url = data.get('icon_url')
                if icon_url:
                    with urllib.request.urlopen(icon_url, timeout=10) as img_response:
                        return img_response.read()
        except (urllib.error.URLError, urllib.error.HTTPError, OSError):
            pass  # Silently ignore network errors
        return None
    
    def stop(self):
        self._running = False


class SimpleIconFetcher(QThread):
    """Simple icon fetcher that downloads an icon from a URL."""
    icon_fetched = pyqtSignal(str, str, bytes)  # mod_id, source, icon_bytes
    finished_loading = pyqtSignal(str)  # mod_id
    
    def __init__(self, mod_id: str, icon_url: str, source: str):
        super().__init__()
        self.mod_id = mod_id
        self.icon_url = icon_url
        self.source = source
        self._running = True
    
    def run(self):
        """Fetch icon from URL."""
        if not self._running or not self.icon_url:
            self.finished_loading.emit(self.mod_id)
            return
        
        try:
            req = urllib.request.Request(self.icon_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=8) as response:
                data = response.read()
                if data and self._running:
                    self.icon_fetched.emit(self.mod_id, self.source, data)
        except (urllib.error.URLError, urllib.error.HTTPError, OSError):
            pass  # Silently ignore network errors
        
        self.finished_loading.emit(self.mod_id)
    
    def stop(self):
        self._running = False


# === Hash Calculator ===
class HashCalculator(QThread):
    """Background thread for calculating file hashes."""
    hash_calculated = pyqtSignal(str)
    progress_updated = pyqtSignal(int)
    error_occurred = pyqtSignal(str)
    
    def __init__(self, url: str):
        super().__init__()
        self.url = url
        self._running = True
    
    def run(self):
        """Download file and calculate SHA-256 hash."""
        try:
            req = urllib.request.Request(self.url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as response:
                total_size = int(response.headers.get('content-length', 0))
                downloaded = 0
                hasher = hashlib.sha256()
                
                while self._running:
                    chunk = response.read(8192)
                    if not chunk:
                        break
                    hasher.update(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        progress = int((downloaded / total_size) * 100)
                        self.progress_updated.emit(progress)
                
                if self._running:
                    self.hash_calculated.emit(hasher.hexdigest())
        except Exception as e:
            if self._running:
                self.error_occurred.emit(str(e))
    
    def stop(self):
        self._running = False


# === Data Models ===
class ModEntry:
    """Represents a mod entry in mods.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.display_name = data.get('display_name', '')
        self.file_name = data.get('file_name', '')
        # Support both "id" (new) and "numberId" (legacy) for backward compatibility
        self.id = data.get('id', data.get('numberId', ''))
        self.hash = data.get('hash', '')
        self.install_location = data.get('installLocation', 'mods')
        self.source = data.get('source', {'type': 'url', 'url': ''})
        self.since = data.get('since', DEFAULT_VERSION)  # Version this mod was introduced
        self.icon_path = data.get('icon_path', '')
        self._is_new = not bool(self.id)
        self._is_from_previous = data.get('_is_from_previous', False)
        self._icon_data = None  # Cached icon bytes
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'display_name': self.display_name,
            'file_name': self.file_name,
            'id': self.id,  # Use new "id" field
            'hash': self.hash,
            'installLocation': self.install_location,
            'source': self.source,
            'since': self.since
        }
        # Don't include icon_path or internal flags in output
        return result
    
    def is_new(self) -> bool:
        return self._is_new
    
    def mark_saved(self):
        self._is_new = False


class FileEntry:
    """Represents a file entry in files.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.display_name = data.get('display_name', '')
        self.file_name = data.get('file_name', '')
        self.url = data.get('url', '')
        self.download_path = data.get('downloadPath', 'config/')
        self.hash = data.get('hash', '')
        self.overwrite = data.get('overwrite', True)
        self.extract = data.get('extract', False)
        self.since = data.get('since', DEFAULT_VERSION)  # Version this file was introduced
        self.icon_path = data.get('icon_path', '')
        self._is_from_previous = data.get('_is_from_previous', False)
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'display_name': self.display_name,
            'file_name': self.file_name,
            'url': self.url,
            'downloadPath': self.download_path,
            'hash': self.hash,
            'overwrite': self.overwrite,
            'extract': self.extract,
            'since': self.since
        }
        # Don't include icon_path or internal flags in output
        return result


class DeleteEntry:
    """Represents a delete entry in deletes.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.path = data.get('path', '')
        self.type = data.get('type', 'file')
        self.reason = data.get('reason', '')
        self.version = data.get('version', DEFAULT_VERSION)  # Version this deletion applies to
        self.icon_path = data.get('icon_path', '')
        self._is_unremovable = data.get('_is_unremovable', False)  # For auto-added deletes from removed mods/files
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'path': self.path,
            'type': self.type
        }
        if self.reason:
            result['reason'] = self.reason
        # Don't include icon_path, version (handled at group level), or internal flags in output
        return result


class ModpackConfig:
    """Represents the main config.json for the modpack."""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.modpack_version = data.get('modpackVersion', '1.0.0')
        self.configs_base_url = data.get('configsBaseUrl', '')
        self.mods_json = data.get('modsJson', 'mods.json')
        self.files_json = data.get('filesJson', 'files.json')
        self.deletes_json = data.get('deletesJson', 'deletes.json')
        self.check_current_version = data.get('checkCurrentVersion', True)
        self.max_retries = data.get('maxRetries', 3)
        self.backup_keep = data.get('backupKeep', 5)
        self.debug_mode = data.get('debugMode', False)
        self._sha = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'modpackVersion': self.modpack_version,
            'configsBaseUrl': self.configs_base_url,
            'modsJson': self.mods_json,
            'filesJson': self.files_json,
            'deletesJson': self.deletes_json,
            'checkCurrentVersion': self.check_current_version,
            'maxRetries': self.max_retries,
            'backupKeep': self.backup_keep,
            'debugMode': self.debug_mode
        }


class VersionConfig:
    """Represents a complete version configuration."""
    def __init__(self, version: str):
        self.version = version
        self.mods: List[ModEntry] = []
        self.files: List[FileEntry] = []
        self.deletes: List[DeleteEntry] = []
        self.icon_path = ""
        self.modified = False
        self._file_shas = {}
        self._is_locked = False  # Once saved/created to repo, version is locked
        self._is_new = True  # True if version hasn't been saved to repo yet
        self.safety_mode = True  # Safety mode for deletes - default enabled
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'mods': [m.to_dict() for m in self.mods],
            'files': [f.to_dict() for f in self.files],
            'deletes': [d.to_dict() for d in self.deletes]
        }
    
    def lock(self):
        """Lock the version after it has been saved to repo"""
        self._is_locked = True
        self._is_new = False
    
    def is_locked(self) -> bool:
        return self._is_locked
    
    def is_new(self) -> bool:
        return self._is_new



# === Dialogs ===
class LoadingDialog(QDialog):
    """Loading dialog shown during startup while preloading icons."""
    icons_loaded = pyqtSignal()  # Emitted when icons are loaded
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setup_ui()
        self._check_timer = None
    
    def setup_ui(self):
        self.setWindowTitle("Loading...")
        self.setFixedSize(300, 100)
        self.setModal(True)
        # Remove window frame and make background transparent for true rounded corners
        self.setWindowFlags(self.windowFlags() | Qt.WindowType.FramelessWindowHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        
        # Main container with rounded corners
        self.container = QFrame(self)
        self.container.setGeometry(0, 0, 300, 100)
        
        self._apply_theme()
        
        layout = QVBoxLayout(self.container)
        layout.setSpacing(12)
        layout.setContentsMargins(24, 20, 24, 20)
        
        # Loading text
        self.label = QLabel("Loading...")
        self.label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.label.setObjectName("loadingLabel")
        layout.addWidget(self.label)
        
        # Progress bar - improved styling
        self.progress = QProgressBar()
        self.progress.setRange(0, 0)  # Indeterminate
        self.progress.setObjectName("loadingProgress")
        self.progress.setTextVisible(False)
        self.progress.setFixedHeight(6)
        layout.addWidget(self.progress)
    
    def _apply_theme(self):
        """Apply the current theme to the loading dialog."""
        theme = get_current_theme()
        self.container.setStyleSheet(f"""
            QFrame {{
                background-color: {theme['bg_primary']};
                border: 2px solid {theme['accent']};
                border-radius: 12px;
            }}
            QLabel#loadingLabel {{
                font-size: 14px;
                font-weight: bold;
                color: {theme['text_primary']};
                background-color: transparent;
            }}
            QProgressBar#loadingProgress {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 3px;
            }}
            QProgressBar#loadingProgress::chunk {{
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 {theme['accent']}, stop:0.5 {theme['accent_hover']}, stop:1 {theme['accent']});
                border-radius: 3px;
            }}
        """)
    
    def start_checking(self):
        """Start checking if icons are loaded."""
        self._check_timer = QTimer()
        self._check_timer.timeout.connect(self._check_loading_complete)
        self._check_timer.start(100)  # Check every 100ms
    
    def _check_loading_complete(self):
        """Check if preloading is complete."""
        # Use public method to check loaded icon counts
        cf_loaded = ModBrowserDialog.get_loaded_icon_count('curseforge')
        mr_loaded = ModBrowserDialog.get_loaded_icon_count('modrinth')
        
        # Consider loaded if we have at least some icons from each source
        min_icons_per_source = min(5, SEARCH_PAGE_SIZE // 2)
        
        if cf_loaded >= min_icons_per_source and mr_loaded >= min_icons_per_source:
            self._finish_loading()
        elif cf_loaded + mr_loaded >= min_icons_per_source * 2:
            # If total is enough even if one source has more
            self._finish_loading()
    
    def _finish_loading(self):
        """Finish loading and close dialog."""
        if self._check_timer:
            self._check_timer.stop()
        self.icons_loaded.emit()
        self.accept()
    
    def force_close(self):
        """Force close after timeout."""
        if self._check_timer:
            self._check_timer.stop()
        self.icons_loaded.emit()
        self.accept()


class APITokenGuideDialog(QDialog):
    """Dialog showing how to create a GitHub API token."""
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("How to Create a GitHub API Token")
        self.setMinimumSize(600, 500)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        layout.setContentsMargins(24, 24, 24, 24)
        
        header = QLabel("🔑 Creating a GitHub Personal Access Token")
        header.setStyleSheet("font-size: 18px; font-weight: bold;")
        layout.addWidget(header)
        
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        content = QWidget()
        content_layout = QVBoxLayout(content)
        content_layout.setSpacing(12)
        
        steps = [
            ("Step 1: Go to GitHub Settings", 
             "Open GitHub.com and log in. Click your profile picture in the top-right corner, then click 'Settings'."),
            ("Step 2: Access Developer Settings", 
             "Scroll down the left sidebar and click 'Developer settings' at the bottom."),
            ("Step 3: Create a Personal Access Token", 
             "Click 'Personal access tokens' → 'Tokens (classic)' → 'Generate new token' → 'Generate new token (classic)'."),
            ("Step 4: Configure Token Settings", 
             "• Give your token a descriptive name (e.g., 'ModUpdater Config Editor')\n"
             "• Set an expiration date (or 'No expiration' for convenience)\n"
             "• Select the 'repo' scope to give full repository access"),
            ("Step 5: Generate and Copy", 
             "Click 'Generate token' at the bottom. IMPORTANT: Copy the token immediately - you won't be able to see it again!"),
            ("Step 6: Use the Token", 
             "Paste the token (starts with 'ghp_') into the API Token field in the setup dialog.")
        ]
        
        for title, description in steps:
            step_group = QGroupBox(title)
            step_layout = QVBoxLayout(step_group)
            desc_label = QLabel(description)
            desc_label.setWordWrap(True)
            step_layout.addWidget(desc_label)
            content_layout.addWidget(step_group)
        
        # Warning
        warning = QLabel("⚠️ Keep your token secure! Never share it publicly or commit it to repositories.")
        theme = get_current_theme()
        warning.setStyleSheet(f"color: {theme['warning']}; font-weight: bold; padding: 12px; background-color: rgba(249, 226, 175, 0.1); border-radius: 8px;")
        warning.setWordWrap(True)
        content_layout.addWidget(warning)
        
        content_layout.addStretch()
        scroll.setWidget(content)
        layout.addWidget(scroll)
        
        # Close button
        close_btn = QPushButton("Got it!")
        close_btn.setObjectName("primaryButton")
        close_btn.clicked.connect(self.accept)
        layout.addWidget(close_btn)


class ThemeCreationDialog(QDialog):
    """Dialog for creating a new custom theme."""
    
    def __init__(self, parent=None, base_theme_key: str = "dark", edit_theme_key: str = None):
        super().__init__(parent)
        self.base_theme_key = base_theme_key
        self.edit_theme_key = edit_theme_key  # If editing existing custom theme
        if edit_theme_key and edit_theme_key in THEMES:
            self.theme_data = dict(THEMES[edit_theme_key])
        else:
            self.theme_data = dict(THEMES.get(base_theme_key, THEMES["dark"]))
        self.setup_ui()
    
    def setup_ui(self):
        title = "Edit Custom Theme" if self.edit_theme_key else "Create Custom Theme"
        self.setWindowTitle(title)
        self.setMinimumSize(600, 700)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        layout.setContentsMargins(24, 24, 24, 24)
        
        header = QLabel(f"🎨 {title}")
        header.setStyleSheet("font-size: 18px; font-weight: bold;")
        layout.addWidget(header)
        
        # Theme name
        name_layout = QHBoxLayout()
        name_layout.addWidget(QLabel("Theme Name:"))
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("My Custom Theme")
        # If editing, pre-fill the name
        if self.edit_theme_key:
            self.name_edit.setText(self.theme_data.get('name', ''))
            self.name_edit.setReadOnly(True)  # Can't change name when editing
        name_layout.addWidget(self.name_edit)
        layout.addLayout(name_layout)
        
        # Base theme selection (only for new themes)
        if not self.edit_theme_key:
            base_layout = QHBoxLayout()
            base_layout.addWidget(QLabel("Base Theme:"))
            self.base_combo = QComboBox()
            for key, theme in THEMES.items():
                self.base_combo.addItem(theme['name'], key)
            idx = self.base_combo.findData(self.base_theme_key)
            if idx >= 0:
                self.base_combo.setCurrentIndex(idx)
            self.base_combo.currentIndexChanged.connect(self._on_base_changed)
            base_layout.addWidget(self.base_combo)
            layout.addLayout(base_layout)
        
        # Color editors in scroll area
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(8)
        
        # Color fields
        self.color_edits = {}
        self.color_buttons = {}
        color_labels = {
            'bg_primary': 'Background Primary',
            'bg_secondary': 'Background Secondary',
            'bg_tertiary': 'Background Tertiary',
            'bg_sidebar': 'Sidebar Background',
            'text_primary': 'Text Primary',
            'text_secondary': 'Text Secondary',
            'accent': 'Accent Color',
            'accent_hover': 'Accent Hover',
            'success': 'Success Color',
            'danger': 'Danger Color',
            'warning': 'Warning Color',
            'border': 'Border Color',
        }
        
        for key, label in color_labels.items():
            row = QHBoxLayout()
            lbl = QLabel(f"{label}:")
            lbl.setMinimumWidth(140)
            row.addWidget(lbl)
            
            edit = QLineEdit()
            edit.setPlaceholderText("#000000")
            edit.setText(self.theme_data.get(key, '#000000'))
            edit.setMaximumWidth(100)
            edit.textChanged.connect(lambda text, k=key: self._on_color_changed(k, text))
            self.color_edits[key] = edit
            row.addWidget(edit)
            
            # Color picker button
            pick_btn = QPushButton("🎨")
            pick_btn.setFixedSize(28, 28)
            pick_btn.setToolTip("Pick color")
            pick_btn.clicked.connect(lambda checked, k=key: self._pick_color(k))
            self.color_buttons[key] = pick_btn
            row.addWidget(pick_btn)
            
            # Color preview
            preview = QLabel()
            preview.setFixedSize(24, 24)
            preview.setStyleSheet(f"background-color: {self.theme_data.get(key, '#000000')}; border: 1px solid #888; border-radius: 4px;")
            preview.setObjectName(f"preview_{key}")
            row.addWidget(preview)
            
            row.addStretch()
            scroll_layout.addLayout(row)
        
        scroll_layout.addStretch()
        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)
        
        # Preview button
        preview_btn = QPushButton("Preview Theme")
        preview_btn.clicked.connect(self._preview_theme)
        layout.addWidget(preview_btn)
        
        # Error label
        self.error_label = QLabel("")
        theme = get_current_theme()
        self.error_label.setStyleSheet(f"color: {theme['danger']};")
        layout.addWidget(self.error_label)
        
        # Buttons
        btn_layout = QHBoxLayout()
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(cancel_btn)
        btn_layout.addStretch()
        action_btn_text = "Save Theme" if self.edit_theme_key else "Create Theme"
        create_btn = QPushButton(action_btn_text)
        create_btn.setObjectName("primaryButton")
        create_btn.clicked.connect(self._create_theme)
        btn_layout.addWidget(create_btn)
        layout.addLayout(btn_layout)
    
    def _pick_color(self, key: str):
        """Open a color picker dialog for the specified color key."""
        from PyQt6.QtWidgets import QColorDialog
        from PyQt6.QtGui import QColor
        
        current_color = self.theme_data.get(key, '#000000')
        color = QColorDialog.getColor(QColor(current_color), self, f"Select {key} color")
        if color.isValid():
            hex_color = color.name().upper()
            self.color_edits[key].setText(hex_color)
            self._on_color_changed(key, hex_color)
    
    def _on_base_changed(self):
        """Update colors when base theme changes."""
        if not hasattr(self, 'base_combo'):
            return
        key = self.base_combo.currentData()
        if key and key in THEMES:
            self.theme_data = dict(THEMES[key])
            # Update all color edits
            for color_key, edit in self.color_edits.items():
                edit.blockSignals(True)
                edit.setText(self.theme_data.get(color_key, '#000000'))
                edit.blockSignals(False)
                self._update_preview(color_key)
    
    def _on_color_changed(self, key: str, text: str):
        """Update theme data and preview when color changes."""
        self.theme_data[key] = text
        self._update_preview(key)
    
    def _update_preview(self, key: str):
        """Update the color preview for a specific key."""
        preview = self.findChild(QLabel, f"preview_{key}")
        if preview:
            color = self.theme_data.get(key, '#000000')
            # Validate color format
            if re.match(r'^#[0-9A-Fa-f]{6}$', color):
                preview.setStyleSheet(f"background-color: {color}; border: 1px solid #888; border-radius: 4px;")
            else:
                preview.setStyleSheet("background-color: #ff0000; border: 1px solid #888; border-radius: 4px;")
    
    def _preview_theme(self):
        """Preview the theme in the application."""
        name = self.name_edit.text().strip() or "Preview Theme"
        self.theme_data['name'] = name
        # Apply theme temporarily
        QApplication.instance().setStyleSheet(generate_stylesheet(self.theme_data))
    
    def _create_theme(self):
        """Create or update the theme and save it."""
        name = self.name_edit.text().strip()
        if not name:
            self.error_label.setText("Please enter a theme name")
            return
        
        # For editing, use the existing key
        if self.edit_theme_key:
            key = self.edit_theme_key
        else:
            # Generate a unique key for the theme
            key = re.sub(r'[^a-z0-9_]', '_', name.lower())
            key = f"custom_{key}"
            # Check for duplicate names (only for new themes)
            if key in THEMES:
                self.error_label.setText("A theme with this name already exists")
                return
        
        # Validate all colors
        for color_key, edit in self.color_edits.items():
            color = edit.text().strip()
            if not re.match(r'^#[0-9A-Fa-f]{6}$', color):
                self.error_label.setText(f"Invalid color format for {color_key}: {color}")
                return
        
        self.theme_data['name'] = name
        add_custom_theme(key, self.theme_data)
        self.accept()
    
    def get_theme_key(self) -> str:
        """Get the key of the created/edited theme."""
        if self.edit_theme_key:
            return self.edit_theme_key
        name = self.name_edit.text().strip()
        return f"custom_{re.sub(r'[^a-z0-9_]', '_', name.lower())}"


class SetupDialog(QDialog):
    """First-time setup dialog for GitHub configuration."""
    
    def __init__(self, parent=None, existing_config: dict = None):
        super().__init__(parent)
        self.config = existing_config or {}
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("GitHub Repository Setup")
        self.setMinimumSize(550, 500)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(20)
        layout.setContentsMargins(30, 30, 30, 30)
        
        header = QLabel("Welcome to ModUpdater Config Editor")
        header.setStyleSheet("font-size: 20px; font-weight: bold;")
        header.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(header)
        
        desc = QLabel("Configure your GitHub repository to store configuration files.\n"
                      "The editor will create and edit configs directly in your GitHub repository.")
        desc.setWordWrap(True)
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(desc)
        
        form_group = QGroupBox("Repository Settings")
        form_layout = QFormLayout(form_group)
        form_layout.setSpacing(15)
        
        self.repo_url_edit = QLineEdit(self.config.get('repo_url', ''))
        self.repo_url_edit.setPlaceholderText("https://github.com/username/repo")
        self.repo_url_edit.returnPressed.connect(self.validate_and_accept)
        form_layout.addRow("Repository URL:", self.repo_url_edit)
        
        # Token with help button
        token_layout = QHBoxLayout()
        self.token_edit = QLineEdit(self.config.get('token', ''))
        self.token_edit.setPlaceholderText("ghp_xxxxxxxxxxxx (REQUIRED)")
        self.token_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.token_edit.returnPressed.connect(self.validate_and_accept)
        token_layout.addWidget(self.token_edit)
        
        token_help_btn = QPushButton("?")
        token_help_btn.setFixedSize(30, 30)
        token_help_btn.setToolTip("How to create an API token")
        token_help_btn.clicked.connect(self.show_token_guide)
        token_layout.addWidget(token_help_btn)
        form_layout.addRow("API Token*:", token_layout)
        
        token_note = QLabel("API Token is required to edit the repository")
        theme = get_current_theme()
        token_note.setStyleSheet(f"font-size: 11px; color: {theme['warning']};")
        form_layout.addRow("", token_note)
        
        self.branch_edit = QLineEdit(self.config.get('branch', 'main'))
        self.branch_edit.setPlaceholderText("main")
        self.branch_edit.returnPressed.connect(self.validate_and_accept)
        form_layout.addRow("Branch:", self.branch_edit)
        
        # Hidden config_path field for backward compatibility
        self.config_path_edit = QLineEdit(self.config.get('config_path', ''))
        self.config_path_edit.setVisible(False)
        
        layout.addWidget(form_group)
        
        # Theme Selection
        theme_group = QGroupBox("Appearance")
        theme_layout = QFormLayout(theme_group)
        
        self.theme_combo = QComboBox()
        for key, theme_data in THEMES.items():
            self.theme_combo.addItem(theme_data['name'], key)
        # Set default theme from config or 'light'
        default_theme = self.config.get('theme', 'light')
        index = self.theme_combo.findData(default_theme)
        if index >= 0:
            self.theme_combo.setCurrentIndex(index)
        else:
            # Default to light theme
            light_index = self.theme_combo.findData('light')
            if light_index >= 0:
                self.theme_combo.setCurrentIndex(light_index)
        self.theme_combo.currentIndexChanged.connect(self._on_theme_preview)
        theme_layout.addRow("Theme:", self.theme_combo)
        
        layout.addWidget(theme_group)
        
        test_btn = QPushButton("Test Connection")
        test_btn.clicked.connect(self.test_connection)
        layout.addWidget(test_btn)
        
        self.status_label = QLabel("")
        self.status_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.status_label)
        
        layout.addStretch()
        
        button_layout = QHBoxLayout()
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)
        button_layout.addStretch()
        save_btn = QPushButton("Save")
        save_btn.setObjectName("primaryButton")
        save_btn.clicked.connect(self.validate_and_accept)
        button_layout.addWidget(save_btn)
        layout.addLayout(button_layout)
    
    def _on_theme_preview(self):
        """Preview theme change in the setup dialog."""
        theme_key = self.theme_combo.currentData()
        if theme_key and theme_key in THEMES:
            set_current_theme(theme_key)
            QApplication.instance().setStyleSheet(generate_stylesheet(THEMES[theme_key]))
    
    def show_token_guide(self):
        """Show the API token creation guide."""
        dialog = APITokenGuideDialog(self)
        dialog.exec()
    
    def test_connection(self):
        repo_url = self.repo_url_edit.text().strip()
        token = self.token_edit.text().strip()
        
        if not repo_url:
            self.status_label.setText("Please enter a repository URL")
            theme = get_current_theme()
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
            return
        
        if not token:
            self.status_label.setText("API Token is required")
            theme = get_current_theme()
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
            return
        
        self.status_label.setText("Testing connection...")
        theme = get_current_theme()
        self.status_label.setStyleSheet(f"color: {theme['warning']};")
        QApplication.processEvents()
        
        try:
            api = GitHubAPI(repo_url, token)
            api.branch = self.branch_edit.text().strip() or "main"
            if api.test_connection():
                self.status_label.setText("Connection successful!")
                theme = get_current_theme()
                self.status_label.setStyleSheet(f"color: {theme['success']};")
            else:
                self.status_label.setText("Could not connect to repository")
                theme = get_current_theme()
                self.status_label.setStyleSheet(f"color: {theme['danger']};")
        except Exception as e:
            self.status_label.setText(f"Error: {str(e)[:50]}")
            theme = get_current_theme()
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
    
    def validate_and_accept(self):
        """Validate that API token is provided before accepting."""
        theme = get_current_theme()
        if not self.repo_url_edit.text().strip():
            self.status_label.setText("Repository URL is required")
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
            return
        if not self.token_edit.text().strip():
            self.status_label.setText("API Token is required to edit the repository")
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
            return
        self.accept()
    
    def get_config(self) -> dict:
        return {
            'repo_url': self.repo_url_edit.text().strip(),
            'token': self.token_edit.text().strip(),
            'branch': self.branch_edit.text().strip() or 'main',
            'config_path': self.config_path_edit.text().strip(),
            'theme': self.theme_combo.currentData() or 'light'
        }


class AddVersionDialog(QDialog):
    """Dialog for adding a new version."""
    
    def __init__(self, existing_versions: List[str], parent=None):
        super().__init__(parent)
        self.existing_versions = existing_versions
        self.latest_version = self._get_latest_version()
        self.setup_ui()
    
    def _get_latest_version(self) -> Optional[str]:
        """Get the latest version from existing versions."""
        if not self.existing_versions:
            return None
        
        def version_tuple(v: str):
            parts = v.split('.')
            nums = []
            for x in parts:
                try:
                    nums.append(int(x))
                except ValueError:
                    nums.append(0)
            return tuple(nums)
        
        sorted_versions = sorted(self.existing_versions, key=version_tuple, reverse=True)
        return sorted_versions[0] if sorted_versions else None
    
    def _compare_versions(self, v1: str, v2: str) -> int:
        """Compare two version strings. Returns positive if v1 > v2, negative if v1 < v2, 0 if equal."""
        def parse(v):
            parts = v.split('.')
            nums = []
            for x in parts:
                try:
                    nums.append(int(x))
                except ValueError:
                    nums.append(0)
            return nums
        
        p1, p2 = parse(v1), parse(v2)
        # Pad with zeros
        while len(p1) < len(p2):
            p1.append(0)
        while len(p2) < len(p1):
            p2.append(0)
        
        for a, b in zip(p1, p2):
            if a > b:
                return 1
            elif a < b:
                return -1
        return 0
    
    def setup_ui(self):
        self.setWindowTitle("Add New Version")
        self.setMinimumSize(450, 320)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(20)
        layout.setContentsMargins(30, 30, 30, 30)
        
        header = QLabel("Add New Version")
        header.setStyleSheet("font-size: 18px; font-weight: bold;")
        layout.addWidget(header)
        
        # Warning message
        theme = get_current_theme()
        warning = QLabel("⚠️ Warning: Once a version is created and saved to the repository,\n"
                        "it cannot be edited. Make sure your mods and files are correct.")
        warning.setStyleSheet(f"color: {theme['warning']}; padding: 10px; background-color: rgba(249, 226, 175, 0.1); border-radius: 6px;")
        warning.setWordWrap(True)
        layout.addWidget(warning)
        
        form_layout = QFormLayout()
        form_layout.setSpacing(15)
        self.version_edit = QLineEdit()
        self.version_edit.setPlaceholderText("e.g., 1.0.0 (format: X.Y.Z)")
        self.version_edit.returnPressed.connect(self.validate_and_accept)
        form_layout.addRow("Version Number:", self.version_edit)
        layout.addLayout(form_layout)
        
        format_note = QLabel("Version must be in X.Y.Z format (e.g., 1.0.0, 2.1.0)")
        format_note.setStyleSheet(f"font-size: 11px; color: {theme['text_secondary']};")
        layout.addWidget(format_note)
        
        # Show latest version info
        if self.latest_version:
            latest_note = QLabel(f"Current latest version: {self.latest_version} - New version must be higher")
            latest_note.setStyleSheet(f"font-size: 11px; color: {theme['accent']};")
            layout.addWidget(latest_note)
        
        self.error_label = QLabel("")
        self.error_label.setStyleSheet(f"color: {theme['danger']};")
        layout.addWidget(self.error_label)
        
        layout.addStretch()
        
        button_layout = QHBoxLayout()
        cancel_btn = QPushButton("Back")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)
        button_layout.addStretch()
        confirm_btn = QPushButton("Create Version")
        confirm_btn.setObjectName("primaryButton")
        confirm_btn.clicked.connect(self.validate_and_accept)
        button_layout.addWidget(confirm_btn)
        layout.addLayout(button_layout)
    
    def validate_and_accept(self):
        version = self.version_edit.text().strip()
        if not version:
            self.error_label.setText("Please enter a version number")
            return
        # Only allow X.Y.Z format - no -beta, -rc, etc.
        if not re.match(r'^\d+\.\d+\.\d+$', version):
            self.error_label.setText("Version must be in X.Y.Z format (e.g., 1.0.0)")
            return
        if version in self.existing_versions:
            self.error_label.setText("This version already exists")
            return
        # Check that new version is higher than latest
        if self.latest_version and self._compare_versions(version, self.latest_version) <= 0:
            self.error_label.setText(f"New version must be higher than {self.latest_version}")
            return
        self.accept()
    
    def get_version(self) -> str:
        return self.version_edit.text().strip()


class AddModDialog(QDialog):
    """Dialog for adding a new mod."""
    
    def __init__(self, existing_ids: List[str], parent=None):
        super().__init__(parent)
        self.existing_ids = existing_ids
        self.custom_icon_path = ""
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("Add New Mod")
        self.setMinimumSize(500, 400)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        layout.setContentsMargins(25, 25, 25, 25)
        
        header = QLabel("Add New Mod")
        header.setStyleSheet("font-size: 18px; font-weight: bold;")
        layout.addWidget(header)
        
        form_layout = QFormLayout()
        form_layout.setSpacing(12)
        
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("Display name (e.g., Just Enough Items)")
        self.name_edit.returnPressed.connect(self.validate_and_accept)
        form_layout.addRow("Name:", self.name_edit)
        
        self.id_edit = QLineEdit()
        self.id_edit.setPlaceholderText("Unique ID (e.g., jei)")
        self.id_edit.returnPressed.connect(self.validate_and_accept)
        form_layout.addRow("ID:", self.id_edit)
        
        layout.addLayout(form_layout)
        
        icon_group = QGroupBox("Icon (Optional)")
        icon_layout = QHBoxLayout(icon_group)
        
        self.icon_preview = QLabel()
        self.icon_preview.setFixedSize(64, 64)
        theme = get_current_theme()
        self.icon_preview.setStyleSheet(f"border: 2px dashed {theme['border']}; border-radius: 8px;")
        self.icon_preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.icon_preview.setText("No Icon")
        icon_layout.addWidget(self.icon_preview)
        
        icon_btn_layout = QVBoxLayout()
        select_icon_btn = QPushButton("Select Icon...")
        select_icon_btn.clicked.connect(self.select_icon)
        icon_btn_layout.addWidget(select_icon_btn)
        clear_icon_btn = QPushButton("Clear")
        clear_icon_btn.clicked.connect(self.clear_icon)
        icon_btn_layout.addWidget(clear_icon_btn)
        icon_btn_layout.addStretch()
        icon_layout.addLayout(icon_btn_layout)
        icon_layout.addStretch()
        layout.addWidget(icon_group)
        
        self.error_label = QLabel("")
        self.error_label.setStyleSheet(f"color: {theme['danger']};")
        layout.addWidget(self.error_label)
        
        layout.addStretch()
        
        button_layout = QHBoxLayout()
        cancel_btn = QPushButton("Back")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)
        button_layout.addStretch()
        confirm_btn = QPushButton("Confirm")
        confirm_btn.setObjectName("primaryButton")
        confirm_btn.clicked.connect(self.validate_and_accept)
        button_layout.addWidget(confirm_btn)
        layout.addLayout(button_layout)
    
    def select_icon(self):
        file_path, _ = QFileDialog.getOpenFileName(
            self, "Select Icon", "", "Images (*.png *.jpg *.jpeg *.gif *.ico)"
        )
        if file_path:
            self.custom_icon_path = file_path
            pixmap = QPixmap(file_path)
            if not pixmap.isNull():
                self.icon_preview.setPixmap(pixmap.scaled(60, 60, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
    
    def clear_icon(self):
        self.custom_icon_path = ""
        self.icon_preview.clear()
        self.icon_preview.setText("No Icon")
    
    def validate_and_accept(self):
        name = self.name_edit.text().strip()
        mod_id = self.id_edit.text().strip()
        
        if not name:
            self.error_label.setText("Please enter a display name")
            return
        if not mod_id:
            self.error_label.setText("Please enter a unique ID")
            return
        if not re.match(r'^[a-zA-Z0-9_-]+$', mod_id):
            self.error_label.setText("ID can only contain letters, numbers, underscores, and hyphens")
            return
        if mod_id in self.existing_ids:
            self.error_label.setText("This ID already exists")
            return
        self.accept()
    
    def get_mod(self) -> ModEntry:
        mod = ModEntry()
        mod.display_name = self.name_edit.text().strip()
        mod.id = self.id_edit.text().strip()
        mod.icon_path = self.custom_icon_path
        return mod


class ConfirmDeleteDialog(QDialog):
    """Dialog for confirming deletion."""
    
    def __init__(self, item_name: str, item_type: str = "item", parent=None):
        super().__init__(parent)
        self.item_name = item_name
        self.item_type = item_type
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("Confirm Delete")
        self.setMinimumSize(400, 180)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(20)
        layout.setContentsMargins(30, 30, 30, 30)
        
        header = QLabel("Confirm Delete")
        theme = get_current_theme()
        header.setStyleSheet(f"font-size: 18px; font-weight: bold; color: {theme['danger']};")
        layout.addWidget(header)
        
        message = QLabel(f"Are you sure you want to delete this {self.item_type}?\n\n\"{self.item_name}\"")
        message.setWordWrap(True)
        layout.addWidget(message)
        
        layout.addStretch()
        
        button_layout = QHBoxLayout()
        cancel_btn = QPushButton("Back")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)
        button_layout.addStretch()
        delete_btn = QPushButton("Delete")
        delete_btn.setObjectName("dangerButton")
        delete_btn.clicked.connect(self.accept)
        button_layout.addWidget(delete_btn)
        layout.addLayout(button_layout)


class ModSearchThread(QThread):
    """Background thread for searching mods from CurseForge/Modrinth."""
    search_complete = pyqtSignal(list, int)  # results, total_count
    error_occurred = pyqtSignal(str)
    
    def __init__(self, source: str, query: str, version_filter: str = "", sort_by: str = "", loader_filter: str = ""):
        super().__init__()
        self.source = source
        self.query = query
        self.version_filter = version_filter
        self.sort_by = sort_by  # Sort option (source-specific value)
        self.loader_filter = loader_filter  # Mod loader filter (forge, fabric, etc.)
        self.offset = 0  # For pagination/infinite scroll
        self._running = True
    
    def run(self):
        try:
            if self.source == 'curseforge':
                results, total_count = self._search_curseforge()
            else:
                results, total_count = self._search_modrinth()
            
            if self._running:
                self.search_complete.emit(results, total_count)
        except Exception as e:
            if self._running:
                self.error_occurred.emit(str(e))
    
    def _search_curseforge(self) -> tuple:
        """Search CurseForge for mods. Returns (results, total_count)."""
        # Use curse.tools proxy API
        # Default to Downloads (sortField 6) since Relevance was removed
        sort_field = self.sort_by if self.sort_by else CURSEFORGE_SORT_OPTIONS.get("Downloads", "6")
        params = {
            'gameId': '432',  # Minecraft
            'classId': '6',   # Mods
            'sortField': sort_field,
            'sortOrder': 'desc',
            'pageSize': str(SEARCH_PAGE_SIZE),
            'index': str(self.offset)  # For pagination
        }
        # Only add search filter if query is not empty
        if self.query:
            params['searchFilter'] = self.query
        if self.version_filter:
            params['gameVersion'] = self.version_filter
        # Add mod loader filter for CurseForge
        if self.loader_filter:
            # CurseForge uses modLoaderType: 1=Forge, 4=Fabric, 5=Quilt, 6=NeoForge
            loader_map = {'forge': '1', 'fabric': '4', 'quilt': '5', 'neoforge': '6'}
            loader_value = loader_map.get(self.loader_filter.lower())
            if loader_value:
                params['modLoaderType'] = loader_value
        
        query_str = urllib.parse.urlencode(params)
        url = f"{CF_PROXY_BASE_URL}/mods/search?{query_str}"
        
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read())
            mods = data.get('data', [])
            # Get total count from pagination info
            pagination = data.get('pagination', {})
            total_count = pagination.get('totalCount', 0)
            
            results = []
            for mod in mods:
                results.append({
                    'source': 'curseforge',
                    'id': str(mod.get('id', '')),
                    'name': mod.get('name', ''),
                    'summary': mod.get('summary', ''),
                    'author': mod.get('authors', [{}])[0].get('name', '') if mod.get('authors') else '',
                    'downloads': mod.get('downloadCount', 0),
                    'icon_url': mod.get('logo', {}).get('thumbnailUrl', '') if mod.get('logo') else '',
                    'slug': mod.get('slug', '')
                })
            return results, total_count
    
    def _search_modrinth(self) -> tuple:
        """Search Modrinth for mods. Returns (results, total_count)."""
        facets = [['project_type:mod']]
        if self.version_filter:
            facets.append([f'versions:{self.version_filter}'])
        # Add mod loader filter for Modrinth
        if self.loader_filter:
            facets.append([f'categories:{self.loader_filter.lower()}'])
        
        # Default to downloads if not specified
        sort_index = self.sort_by if self.sort_by else MODRINTH_SORT_OPTIONS.get("Downloads", "downloads")
        params = {
            'facets': json.dumps(facets),
            'limit': str(SEARCH_PAGE_SIZE),
            'offset': str(self.offset),  # For pagination
            'index': sort_index
        }
        # Only add query if not empty
        if self.query:
            params['query'] = self.query
        
        query_str = urllib.parse.urlencode(params)
        url = f"https://api.modrinth.com/v2/search?{query_str}"
        
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read())
            hits = data.get('hits', [])
            # Get total count from API response
            total_count = data.get('total_hits', 0)
            
            results = []
            for mod in hits:
                results.append({
                    'source': 'modrinth',
                    'id': mod.get('project_id', ''),
                    'name': mod.get('title', ''),
                    'summary': mod.get('description', ''),
                    'author': mod.get('author', ''),
                    'downloads': mod.get('downloads', 0),
                    'icon_url': mod.get('icon_url', ''),
                    'slug': mod.get('slug', '')
                })
            return results, total_count
    
    def stop(self):
        self._running = False


class ModVersionFetchThread(QThread):
    """Background thread for fetching mod versions/files."""
    versions_fetched = pyqtSignal(list)
    error_occurred = pyqtSignal(str)
    
    def __init__(self, source: str, project_id: str, game_version: str = ""):
        super().__init__()
        self.source = source
        self.project_id = project_id
        self.game_version = game_version
        self._running = True
    
    def run(self):
        try:
            if self.source == 'curseforge':
                versions = self._fetch_curseforge_versions()
            else:
                versions = self._fetch_modrinth_versions()
            
            if self._running:
                self.versions_fetched.emit(versions)
        except Exception as e:
            if self._running:
                self.error_occurred.emit(str(e))
    
    def _fetch_curseforge_versions(self) -> list:
        """Fetch versions from CurseForge."""
        url = f"{CF_PROXY_BASE_URL}/mods/{self.project_id}/files"
        if self.game_version:
            url += f"?gameVersion={self.game_version}"
        
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read())
            files = data.get('data', [])
            
            results = []
            for f in files[:20]:  # Limit to 20 most recent
                results.append({
                    'file_id': str(f.get('id', '')),
                    'name': f.get('displayName', ''),
                    'file_name': f.get('fileName', ''),
                    'game_versions': f.get('gameVersions', []),
                    'download_url': f.get('downloadUrl', ''),
                    'release_type': ['Release', 'Beta', 'Alpha'][f.get('releaseType', 1) - 1] if f.get('releaseType') else 'Release'
                })
            return results
    
    def _fetch_modrinth_versions(self) -> list:
        """Fetch versions from Modrinth."""
        url = f"https://api.modrinth.com/v2/project/{self.project_id}/version"
        
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as response:
            versions = json.loads(response.read())
            
            results = []
            for v in versions[:20]:  # Limit to 20 most recent
                files = v.get('files', [])
                primary_file = files[0] if files else {}
                results.append({
                    'file_id': v.get('id', ''),
                    'name': v.get('name', ''),
                    'file_name': primary_file.get('filename', ''),
                    'game_versions': v.get('game_versions', []),
                    'download_url': primary_file.get('url', ''),
                    'release_type': v.get('version_type', 'release').capitalize()
                })
            return results
    
    def stop(self):
        self._running = False


class ModDescriptionFetchThread(QThread):
    """Background thread for fetching full mod description."""
    description_fetched = pyqtSignal(str)
    error_occurred = pyqtSignal(str)
    
    def __init__(self, source: str, project_id: str):
        super().__init__()
        self.source = source
        self.project_id = project_id
        self._running = True
    
    def run(self):
        try:
            if self.source == 'curseforge':
                description = self._fetch_curseforge_description()
            else:
                description = self._fetch_modrinth_description()
            
            if self._running and description is not None:
                self.description_fetched.emit(description)
        except Exception as e:
            if self._running:
                self.error_occurred.emit(str(e))
    
    def _fetch_curseforge_description(self) -> str:
        """Fetch full description from CurseForge."""
        url = f"{CF_PROXY_BASE_URL}/mods/{self.project_id}/description"
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read())
            description = data.get('data', '')
            # Sanitize HTML by removing potentially dangerous tags/attributes
            # Allow only safe HTML tags for display
            description = self._sanitize_html(description)
            return description
    
    def _sanitize_html(self, html_content: str) -> str:
        """Sanitize HTML content by removing script tags and event handlers."""
        if not html_content:
            return html_content
        # Remove script tags completely - match opening tag, content, and closing tag
        # Using a more robust pattern that handles whitespace and attributes in closing tags
        html_content = re.sub(r'<script\b[^>]*>[\s\S]*?<\s*/\s*script[^>]*>', '', html_content, flags=re.IGNORECASE)
        # Also remove any remaining standalone script tags
        html_content = re.sub(r'<\s*/?script\b[^>]*>', '', html_content, flags=re.IGNORECASE)
        # Remove event handlers (onclick, onload, etc.)
        html_content = re.sub(r'\s+on\w+\s*=\s*["\'][^"\']*["\']', '', html_content, flags=re.IGNORECASE)
        # Remove javascript: URLs
        html_content = re.sub(r'href\s*=\s*["\']javascript:[^"\']*["\']', '', html_content, flags=re.IGNORECASE)
        return html_content
    
    def _fetch_modrinth_description(self) -> str:
        """Fetch full description from Modrinth."""
        url = f"https://api.modrinth.com/v2/project/{self.project_id}"
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read())
            # Modrinth returns body (full description) as markdown
            return data.get('body', data.get('description', ''))
    
    def stop(self):
        self._running = False


class ModBrowserDialog(QDialog):
    """Dialog for browsing and selecting mods from CurseForge/Modrinth.
    
    Simple icon loading system:
    - Icons are cached per source (curseforge/modrinth)
    - First page icons are preloaded at program startup
    - Icons load on-demand when items become visible
    """
    
    # Class-level icon cache (persists across dialog instances)
    _icon_cache = {
        'curseforge': {},  # mod_id -> icon_bytes
        'modrinth': {}     # mod_id -> icon_bytes
    }
    
    # Track icons being preloaded to prevent duplicate loads
    _preloading_icons = {
        'curseforge': set(),
        'modrinth': set()
    }
    
    # Flag to track if startup preloading has been initiated
    _startup_preload_started = False
    _startup_preload_threads = []
    
    # Class-level session state (persists across dialog instances until program close)
    _session_filters = {
        'curseforge': {'version': '', 'sort': 0, 'loader': 0, 'page': 0},
        'modrinth': {'version': '', 'sort': 0, 'loader': 0, 'page': 0}
    }
    _session_active_source = 'curseforge'  # Track which source tab was last active
    
    @classmethod
    def start_startup_preload(cls):
        """Preload first page(s) icons for both sources at program startup."""
        if cls._startup_preload_started:
            return
        cls._startup_preload_started = True
        
        # Preload pages for both sources based on STARTUP_PRELOAD_PAGES setting
        for source in ['curseforge', 'modrinth']:
            for page in range(STARTUP_PRELOAD_PAGES):
                cls._preload_page(source, page)
    
    @classmethod
    def _preload_page(cls, source: str, page: int):
        """Fetch and preload icons for a specific page of a source."""
        thread = ModSearchThread(source, "", "")
        thread.offset = page * SEARCH_PAGE_SIZE
        thread.search_complete.connect(
            lambda results, total, s=source: cls._on_preload_results(results, s))
        thread.finished.connect(thread.deleteLater)
        cls._startup_preload_threads.append(thread)
        thread.start()
    
    @classmethod
    def _preload_first_page(cls, source: str):
        """Fetch and preload icons for the first page of a source."""
        cls._preload_page(source, 0)
    
    @classmethod
    def _on_preload_results(cls, results: list, source: str):
        """Handle preloaded search results - start fetching icons."""
        for mod in results:
            mod_id = mod.get('id', mod.get('slug', ''))
            icon_url = mod.get('icon_url', '')
            # Check both cache and preloading set to prevent duplicate loads
            if (mod_id and icon_url and 
                mod_id not in cls._icon_cache.get(source, {}) and
                mod_id not in cls._preloading_icons.get(source, set())):
                cls._preload_single_icon(mod_id, icon_url, source)
    
    @classmethod
    def _preload_single_icon(cls, mod_id: str, icon_url: str, source: str):
        """Preload a single icon in the background."""
        # Mark as preloading to prevent duplicates
        if source not in cls._preloading_icons:
            cls._preloading_icons[source] = set()
        cls._preloading_icons[source].add(mod_id)
        
        thread = SimpleIconFetcher(mod_id, icon_url, source)
        thread.icon_fetched.connect(cls._on_preload_icon_fetched)
        thread.finished_loading.connect(
            lambda mid=mod_id, s=source: cls._on_preload_complete(mid, s))
        thread.finished.connect(thread.deleteLater)
        cls._startup_preload_threads.append(thread)
        thread.start()
    
    @classmethod
    def _on_preload_icon_fetched(cls, mod_id: str, source: str, data: bytes):
        """Handle preloaded icon data."""
        if source not in cls._icon_cache:
            cls._icon_cache[source] = {}
        cls._icon_cache[source][mod_id] = data
    
    @classmethod
    def _on_preload_complete(cls, mod_id: str, source: str):
        """Handle preload completion - remove from tracking set."""
        if source in cls._preloading_icons:
            cls._preloading_icons[source].discard(mod_id)
    
    @classmethod
    def get_loaded_icon_count(cls, source: str) -> int:
        """Get the number of loaded icons for a source."""
        return len(cls._icon_cache.get(source, {}))
    
    def __init__(self, existing_ids: List[str], current_version: str = "1.0.0", parent=None):
        super().__init__(parent)
        self.existing_ids = existing_ids
        self.current_version = current_version
        self.search_thread: Optional[ModSearchThread] = None
        self.version_thread: Optional[ModVersionFetchThread] = None
        self.description_thread: Optional[ModDescriptionFetchThread] = None
        self.icon_threads: List[QThread] = []
        self.selected_mod = None
        self.selected_version = None
        self.all_search_results = []
        
        # Pagination state
        self.current_page = 0
        self.total_results = 0
        self.has_more_results = True
        self._is_loading_page = False
        
        # Track page state per source
        self._source_page_state = {
            'curseforge': {'page': 0, 'total': 0, 'has_more': True},
            'modrinth': {'page': 0, 'total': 0, 'has_more': True}
        }
        
        # Track which mod_ids are currently loading
        self._loading_mod_ids = set()
        
        # Debounce timer for scroll events
        self._scroll_debounce_timer = None
        
        # Debounce timer for search text changes (auto-search while typing)
        self._search_debounce_timer = None
        
        self.search_in_progress = False
        
        self.setup_ui()
        
        # Restore session state (source tab and filters)
        self._restore_session_state()
        
        # Load popular mods on startup
        QTimer.singleShot(100, self.load_popular_mods)

    def setup_ui(self):
        """Set up the UI for the mod browser dialog with pagination controls."""
        self.setWindowTitle("Browse Mods - CurseForge / Modrinth")
        self.setMinimumSize(1220, 820)
        self.setModal(True)

        # Main vertical layout with very tight spacing/margins to remove blank space
        layout = QVBoxLayout(self)
        layout.setSpacing(4)
        layout.setContentsMargins(2, 2, 2, 2)
        self.setContentsMargins(0, 0, 0, 0)

        # Title (remove extra padding)
        title = QLabel("🔍 Find and Add Mods")
        title.setStyleSheet("font-size:16px; font-weight:600; margin:0; padding:0;")
        layout.addWidget(title, alignment=Qt.AlignmentFlag.AlignLeft)

        # Theme for later styling
        theme = get_current_theme()

        # Source selection row
        source_layout = QHBoxLayout()
        source_layout.setSpacing(6)
        source_layout.setContentsMargins(0, 0, 0, 0)

        source_label = QLabel("Source:")
        source_label.setStyleSheet("font-weight: bold; margin:0; padding:0;")
        source_layout.addWidget(source_label)

        self.curseforge_source_btn = QPushButton("🔥 CurseForge")
        self.curseforge_source_btn.setCheckable(True)
        self.curseforge_source_btn.setChecked(True)
        self.curseforge_source_btn.setMinimumWidth(120)
        self.curseforge_source_btn.clicked.connect(lambda: self._select_source('curseforge'))
        source_layout.addWidget(self.curseforge_source_btn)

        self.modrinth_source_btn = QPushButton("📦 Modrinth")
        self.modrinth_source_btn.setCheckable(True)
        self.modrinth_source_btn.setMinimumWidth(120)
        self.modrinth_source_btn.clicked.connect(lambda: self._select_source('modrinth'))
        source_layout.addWidget(self.modrinth_source_btn)

        source_layout.addStretch()
        layout.addLayout(source_layout)

        # Search bar row
        search_layout = QHBoxLayout()
        search_layout.setSpacing(6)
        search_layout.setContentsMargins(0, 0, 0, 0)

        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("Search mods...")
        self.search_edit.returnPressed.connect(self.search_mods)
        self.search_edit.textChanged.connect(self._on_search_text_changed)
        search_layout.addWidget(self.search_edit, 1)

        # Note: Search button removed as search is now automatic while typing
        # (Can be added back if needed for explicit searches)

        layout.addLayout(search_layout)

        # Filter row: MC Version, Sort, Loader
        filter_layout = QHBoxLayout()
        filter_layout.setSpacing(8)
        filter_layout.setContentsMargins(0, 0, 0, 0)

        # MC Version dropdown
        version_lbl = QLabel("MC Version:")
        version_lbl.setStyleSheet("margin:0; padding:0;")
        filter_layout.addWidget(version_lbl)

        self.version_filter = QComboBox()
        self.version_filter.setEditable(False)  # Use dropdown only, not editable text
        self.version_filter.setMinimumWidth(100)  # Use minimum width instead of fixed
        self.version_filter.setMaximumWidth(120)  # Allow some expansion for dropdown button
        for version in MC_VERSION_OPTIONS:
            self.version_filter.addItem(version if version else "Any")
        self.version_filter.setCurrentIndex(0)
        self.version_filter.currentIndexChanged.connect(self._on_filter_changed)
        filter_layout.addWidget(self.version_filter)

        # Sort dropdown
        sort_lbl = QLabel("Sort by:")
        sort_lbl.setStyleSheet("margin:0; padding:0;")
        filter_layout.addWidget(sort_lbl)

        self.sort_combo = QComboBox()
        self.sort_combo.setFixedWidth(130)
        self._update_sort_options()
        self.sort_combo.currentIndexChanged.connect(self._on_filter_changed)
        filter_layout.addWidget(self.sort_combo)

        # Loader filter dropdown
        loader_lbl = QLabel("Loader:")
        loader_lbl.setStyleSheet("margin:0; padding:0;")
        filter_layout.addWidget(loader_lbl)

        self.loader_combo = QComboBox()
        self.loader_combo.setFixedWidth(100)
        for name in MOD_LOADER_OPTIONS.keys():
            self.loader_combo.addItem(name)
        self.loader_combo.setCurrentIndex(0)  # Default to "Both"
        self.loader_combo.currentIndexChanged.connect(self._on_filter_changed)
        filter_layout.addWidget(self.loader_combo)

        filter_layout.addStretch()
        layout.addLayout(filter_layout)

        # Splitter for results / description
        splitter = QSplitter(Qt.Orientation.Horizontal)

        # Left panel (results)
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(0, 0, 0, 0)
        left_layout.setSpacing(4)

        self.results_header = QLabel("Mods:")
        self.results_header.setStyleSheet("font-weight: bold; margin:0; padding:0;")
        left_layout.addWidget(self.results_header)

        self.results_list = QListWidget()
        self.results_list.itemClicked.connect(self.on_mod_selected)
        self.results_list.setAlternatingRowColors(True)
        self.results_list.setIconSize(QSize(40, 40))
        self.results_list.setSpacing(4)
        self.results_list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.results_list.setWordWrap(True)
        self.results_list.verticalScrollBar().valueChanged.connect(self._on_scroll)
        left_layout.addWidget(self.results_list)

        self.search_status = QLabel("")
        self.search_status.setStyleSheet(f"font-size:11px; color:{theme['text_secondary']}; margin:0; padding:0;")
        left_layout.addWidget(self.search_status)

        # Pagination controls - improved layout with arrows next to page number
        pagination_container = QWidget()
        pagination_container.setStyleSheet(f"""
            QWidget {{
                background-color: {theme['bg_tertiary']};
                border-radius: 8px;
                padding: 4px;
            }}
        """)
        pagination_layout = QHBoxLayout(pagination_container)
        pagination_layout.setSpacing(4)
        pagination_layout.setContentsMargins(8, 6, 8, 6)

        # First page button (far left)
        self.first_page_btn = QPushButton("⏮")
        self.first_page_btn.setFixedSize(28, 26)
        self.first_page_btn.setToolTip("Go to first page")
        self.first_page_btn.clicked.connect(self._go_to_first_page)
        pagination_layout.addWidget(self.first_page_btn)

        pagination_layout.addStretch()

        # Center group: prev arrow, page selector, next arrow - all together
        center_widget = QWidget()
        center_widget.setStyleSheet(f"""
            QWidget {{
                background-color: {theme['bg_secondary']};
                border-radius: 6px;
            }}
        """)
        center_layout = QHBoxLayout(center_widget)
        center_layout.setSpacing(2)
        center_layout.setContentsMargins(4, 2, 4, 2)

        # Previous page button
        self.prev_page_btn = QPushButton("◀")
        self.prev_page_btn.setFixedSize(28, 26)
        self.prev_page_btn.setToolTip("Go to previous page")
        self.prev_page_btn.clicked.connect(self._go_to_prev_page)
        center_layout.addWidget(self.prev_page_btn)

        page_label = QLabel("Page")
        page_label.setStyleSheet(f"font-weight: 600; font-size: 12px; color: {theme['text_secondary']}; padding: 0 4px;")
        center_layout.addWidget(page_label)

        self.page_spin = QSpinBox()
        self.page_spin.setMinimum(1)
        self.page_spin.setMaximum(1)
        self.page_spin.setValue(1)
        self.page_spin.setFixedWidth(55)
        self.page_spin.setStyleSheet(f"""
            QSpinBox {{
                background-color: {theme['bg_primary']};
                border: 1px solid {theme['border']};
                border-radius: 4px;
                padding: 2px 4px;
                font-weight: bold;
            }}
            QSpinBox:focus {{
                border-color: {theme['accent']};
            }}
        """)
        self.page_spin.valueChanged.connect(self._on_page_spin_changed)
        center_layout.addWidget(self.page_spin)

        self.page_total_label = QLabel("of 1")
        self.page_total_label.setStyleSheet(f"color: {theme['text_secondary']}; font-size: 12px; padding: 0 4px;")
        center_layout.addWidget(self.page_total_label)

        # Next page button
        self.next_page_btn = QPushButton("▶")
        self.next_page_btn.setFixedSize(28, 26)
        self.next_page_btn.setToolTip("Go to next page")
        self.next_page_btn.clicked.connect(self._go_to_next_page)
        center_layout.addWidget(self.next_page_btn)

        pagination_layout.addWidget(center_widget)

        pagination_layout.addStretch()

        # Last page button (far right)
        self.last_page_btn = QPushButton("⏭")
        self.last_page_btn.setFixedSize(28, 26)
        self.last_page_btn.setToolTip("Go to last page")
        self.last_page_btn.clicked.connect(self._go_to_last_page)
        pagination_layout.addWidget(self.last_page_btn)

        left_layout.addWidget(pagination_container)

        # Style pagination buttons - modern compact design
        pagination_btn_style = f"""
            QPushButton {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 4px;
                padding: 2px;
                font-size: 12px;
                font-weight: bold;
            }}
            QPushButton:hover {{
                background-color: {theme['accent']};
                color: {theme['bg_primary']};
            }}
            QPushButton:pressed {{
                background-color: {theme['accent_hover']};
            }}
            QPushButton:disabled {{
                background-color: transparent;
                color: {theme['text_secondary']};
            }}
        """
        self.first_page_btn.setStyleSheet(pagination_btn_style)
        self.prev_page_btn.setStyleSheet(pagination_btn_style)
        self.next_page_btn.setStyleSheet(pagination_btn_style)
        self.last_page_btn.setStyleSheet(pagination_btn_style)

        splitter.addWidget(left_panel)

        # Right panel (description + versions)
        right_panel = QWidget()
        right_layout = QVBoxLayout(right_panel)
        right_layout.setContentsMargins(0, 0, 0, 0)
        right_layout.setSpacing(4)

        self.mod_info_header = QLabel("Select a mod to view its description")
        self.mod_info_header.setStyleSheet(f"font-weight:bold; font-size:14px; color:{theme['accent']}; margin:0; padding:0;")
        self.mod_info_header.setWordWrap(True)
        right_layout.addWidget(self.mod_info_header)

        self.description_browser = RemoteImageTextBrowser()
        self.description_browser.setOpenExternalLinks(True)
        self.description_browser.setMinimumHeight(200)
        self.description_browser.setStyleSheet(f"""
            QTextBrowser {{
                background-color: {theme['bg_secondary']};
                border: 1px solid {theme['border']};
                border-radius: 6px;
                padding:6px;
                margin:0;
            }}
        """)
        right_layout.addWidget(self.description_browser, 1)

        version_layout = QHBoxLayout()
        version_layout.setSpacing(6)
        version_layout.setContentsMargins(0, 0, 0, 0)

        versions_label = QLabel("📁 Selected File:")
        versions_label.setStyleSheet("font-weight: bold; margin:0; padding:0;")
        version_layout.addWidget(versions_label)

        self.versions_combo = QComboBox()
        self.versions_combo.setMinimumWidth(300)
        self.versions_combo.setMaxVisibleItems(15)  # Show more items for easier scrolling
        self.versions_combo.view().setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.versions_combo.currentIndexChanged.connect(self.on_version_combo_changed)
        version_layout.addWidget(self.versions_combo, 1)

        right_layout.addLayout(version_layout)

        splitter.addWidget(right_panel)
        splitter.setSizes([400, 550])
        layout.addWidget(splitter, 1)

        # Bottom buttons
        button_layout = QHBoxLayout()
        button_layout.setSpacing(6)
        button_layout.setContentsMargins(0, 0, 0, 0)

        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)

        button_layout.addStretch()

        self.add_btn = QPushButton("✓ Add Mod")
        self.add_btn.setObjectName("primaryButton")
        self.add_btn.clicked.connect(self.add_selected_mod)
        self.add_btn.setEnabled(False)
        self.add_btn.setMinimumWidth(120)
        button_layout.addWidget(self.add_btn)

        layout.addLayout(button_layout)

        # Update source button styles (after creation)
        self._update_source_button_styles()

        # Initialize pagination state
        self._update_pagination_controls()

        # Set up debounce timer for scroll events (simplified icon loading)
        self._scroll_debounce_timer = QTimer()
        self._scroll_debounce_timer.setSingleShot(True)
        self._scroll_debounce_timer.timeout.connect(self._load_visible_icons)
        self._scroll_debounce_timer.setInterval(ICON_LOAD_DEBOUNCE_MS)
        
        # Set up debounce timer for search text changes
        self._search_debounce_timer = QTimer()
        self._search_debounce_timer.setSingleShot(True)
        self._search_debounce_timer.timeout.connect(self.search_mods)
        self._search_debounce_timer.setInterval(400)  # 400ms debounce for typing

    def _on_search_text_changed(self, text: str):
        """Handle search text changes - debounce and auto-search while typing."""
        if self._search_debounce_timer:
            self._search_debounce_timer.start()
    
    def _on_filter_changed(self):
        """Handle filter dropdown changes - refresh the mod list immediately."""
        # Save current filter state for this source to class-level session storage
        source = self._get_selected_source()
        ModBrowserDialog._session_filters[source]['version'] = self.version_filter.currentText()
        ModBrowserDialog._session_filters[source]['sort'] = self.sort_combo.currentIndex()
        ModBrowserDialog._session_filters[source]['loader'] = self.loader_combo.currentIndex()
        
        # Trigger search with new filters
        self.search_mods()

    def _on_scroll(self, value):
        """Handle scroll event for lazy icon loading."""
        # Debounce icon loading to avoid excessive processing during fast scrolls
        if self._scroll_debounce_timer:
            self._scroll_debounce_timer.start()

    def _get_visible_range(self) -> Tuple[int, int]:
        """Get the range of currently visible item indices using efficient indexAt() method."""
        if self.results_list.count() == 0:
            return (0, 0)

        # Get the viewport rect
        viewport_rect = self.results_list.viewport().rect()

        # Use indexAt() for efficient lookup of first visible item
        first_item = self.results_list.itemAt(viewport_rect.topLeft())
        if first_item:
            first_visible = self.results_list.row(first_item)
        else:
            first_visible = 0

        # Use indexAt() for efficient lookup of last visible item
        last_item = self.results_list.itemAt(viewport_rect.bottomLeft())
        if last_item:
            last_visible = self.results_list.row(last_item)
        else:
            # If no item at bottom, use the last item in the list
            last_visible = self.results_list.count() - 1

        return (first_visible, last_visible)

    def _load_visible_icons(self):
        """Load icons for currently visible items."""
        if self.results_list.count() == 0:
            return

        first_visible, last_visible = self._get_visible_range()
        source = self._get_selected_source()

        # Count active threads
        active_count = sum(1 for t in self.icon_threads if self._thread_is_running(t))

        # Process visible items only
        for i in range(first_visible, min(last_visible + 1, self.results_list.count())):
            if active_count >= ICON_MAX_CONCURRENT_LOADS:
                break

            item = self.results_list.item(i)
            if not item:
                continue

            mod = item.data(Qt.ItemDataRole.UserRole)
            if not mod:
                continue

            mod_id = mod.get('id', mod.get('slug', ''))
            icon_url = mod.get('icon_url', '')

            if not icon_url or not mod_id:
                continue

            # Skip if already loading
            if mod_id in self._loading_mod_ids:
                continue

            # Check cache
            if source in self._icon_cache and mod_id in self._icon_cache[source]:
                self._apply_icon_to_item(item, self._icon_cache[source][mod_id])
                continue

            # Start loading
            self._start_icon_load(item, mod_id, icon_url, source)
            active_count += 1

    def _start_icon_load(self, item: QListWidgetItem, mod_id: str, icon_url: str, source: str):
        """Start loading an icon in a background thread."""
        self._loading_mod_ids.add(mod_id)

        try:
            thread = SimpleIconFetcher(mod_id, icon_url, source)
            thread.icon_fetched.connect(self._on_icon_fetched)
            thread.finished_loading.connect(lambda mid=mod_id: self._on_icon_load_complete(mid))
            thread.finished.connect(thread.deleteLater)
            thread.item = item
            self.icon_threads.append(thread)
            thread.start()
        except Exception:
            self._loading_mod_ids.discard(mod_id)

    def _on_icon_fetched(self, mod_id: str, source: str, data: bytes):
        """Handle icon fetched from background thread."""
        # Cache the icon
        ModBrowserDialog._icon_cache[source][mod_id] = data

        # Find and update the item
        try:
            thread = self.sender()
            if thread and hasattr(thread, 'item') and thread.item:
                self._apply_icon_to_item(thread.item, data)
        except RuntimeError:
            # Qt C++ object was deleted before we could access it
            pass

    def _on_icon_load_complete(self, mod_id: str):
        """Handle when an icon load completes."""
        self._loading_mod_ids.discard(mod_id)

        # Clean up finished threads
        self.icon_threads = [t for t in self.icon_threads if self._thread_is_running(t)]

        # Load more icons if needed
        active_count = len(self.icon_threads)
        if active_count < ICON_MAX_CONCURRENT_LOADS // 2:
            if self._scroll_debounce_timer and not self._scroll_debounce_timer.isActive():
                QTimer.singleShot(ICON_LOAD_DEBOUNCE_MS, self._load_visible_icons)

    def _get_selected_source(self) -> str:
        """Get the currently selected source."""
        return 'curseforge' if self.curseforge_source_btn.isChecked() else 'modrinth'

    def _select_source(self, source: str):
        """Select a source and update UI."""
        # Save current source to session state
        ModBrowserDialog._session_active_source = source
        self.curseforge_source_btn.setChecked(source == 'curseforge')
        self.modrinth_source_btn.setChecked(source == 'modrinth')
        self._update_source_button_styles()
        self.on_source_changed()
    
    def _restore_session_state(self):
        """Restore session state (source tab and filters) from class-level storage."""
        # Restore active source tab
        source = ModBrowserDialog._session_active_source
        self.curseforge_source_btn.setChecked(source == 'curseforge')
        self.modrinth_source_btn.setChecked(source == 'modrinth')
        self._update_source_button_styles()
        
        # Update sort options for the current source
        self._update_sort_options()
        
        # Restore filters for the current source
        saved_filters = ModBrowserDialog._session_filters.get(source, {})
        
        self.version_filter.blockSignals(True)
        self.sort_combo.blockSignals(True)
        self.loader_combo.blockSignals(True)
        
        if saved_filters.get('version'):
            idx = self.version_filter.findText(saved_filters['version'])
            if idx >= 0:
                self.version_filter.setCurrentIndex(idx)
            else:
                self.version_filter.setCurrentIndex(0)
        else:
            self.version_filter.setCurrentIndex(0)
            
        if saved_filters.get('sort', 0) < self.sort_combo.count():
            self.sort_combo.setCurrentIndex(saved_filters.get('sort', 0))
        if saved_filters.get('loader', 0) < self.loader_combo.count():
            self.loader_combo.setCurrentIndex(saved_filters.get('loader', 0))
        
        # Restore page state
        if saved_filters.get('page', 0) > 0:
            self.current_page = saved_filters.get('page', 0)
        
        self.version_filter.blockSignals(False)
        self.sort_combo.blockSignals(False)
        self.loader_combo.blockSignals(False)

    def _update_source_button_styles(self):
        """Update source button styles to show selected state."""
        # Use theme colors directly for proper theming
        theme = get_current_theme()
        selected_style = f"background-color: {theme['accent']}; border: 2px solid {theme['accent']}; font-weight: bold; color: {theme['bg_primary']};"
        normal_style = ""

        self.curseforge_source_btn.setStyleSheet(selected_style if self.curseforge_source_btn.isChecked() else normal_style)
        self.modrinth_source_btn.setStyleSheet(selected_style if self.modrinth_source_btn.isChecked() else normal_style)

    def _update_sort_options(self):
        """Update sort dropdown options based on current source."""
        self.sort_combo.blockSignals(True)
        self.sort_combo.clear()
        
        source = self._get_selected_source()
        if source == 'curseforge':
            for name in CURSEFORGE_SORT_OPTIONS.keys():
                self.sort_combo.addItem(name)
        else:
            for name in MODRINTH_SORT_OPTIONS.keys():
                self.sort_combo.addItem(name)
        
        # Default to Downloads (first item)
        self.sort_combo.setCurrentIndex(0)
        self.sort_combo.blockSignals(False)

    def _get_current_sort_value(self) -> str:
        """Get the current sort value for the API based on source."""
        source = self._get_selected_source()
        sort_name = self.sort_combo.currentText()
        
        if source == 'curseforge':
            return CURSEFORGE_SORT_OPTIONS.get(sort_name, CURSEFORGE_SORT_OPTIONS.get("Downloads", "6"))
        else:
            return MODRINTH_SORT_OPTIONS.get(sort_name, MODRINTH_SORT_OPTIONS.get("Downloads", "downloads"))

    def _preload_next_page_icons(self, page: int):
        """Preload icons for the next page in the background."""
        if self._is_loading_page:
            return
        
        source = self._get_selected_source()
        query = self.search_edit.text().strip()
        
        # Get version filter from combo box
        version_text = self.version_filter.currentText().strip()
        version_filter = version_text if version_text and version_text != "Any" else ""
        
        # Get sort option based on current source
        sort_by = self._get_current_sort_value()
        
        # Get loader filter
        loader_name = self.loader_combo.currentText()
        loader_filter = MOD_LOADER_OPTIONS.get(loader_name, "")
        
        # Create a lightweight thread just for fetching the next page data
        preload_thread = ModSearchThread(source, query, version_filter, sort_by, loader_filter)
        preload_thread.offset = page * SEARCH_PAGE_SIZE
        preload_thread.search_complete.connect(
            lambda results, total, s=source: self._on_preload_page_results(results, s))
        preload_thread.finished.connect(preload_thread.deleteLater)
        self.icon_threads.append(preload_thread)
        preload_thread.start()

    def _on_preload_page_results(self, results: list, source: str):
        """Handle preloaded page results - start fetching icons up to NEXT_PAGE_PRELOAD_ICONS."""
        icons_to_preload = min(len(results), NEXT_PAGE_PRELOAD_ICONS)
        for mod in results[:icons_to_preload]:
            mod_id = mod.get('id', mod.get('slug', ''))
            icon_url = mod.get('icon_url', '')
            # Check both cache and preloading set to prevent duplicate loads
            if (mod_id and icon_url and 
                mod_id not in ModBrowserDialog._icon_cache.get(source, {}) and
                mod_id not in ModBrowserDialog._preloading_icons.get(source, set())):
                ModBrowserDialog._preload_single_icon(mod_id, icon_url, source)

    # Pagination methods
    def _update_pagination_controls(self):
        """Update pagination controls based on current state."""
        # Calculate total pages based on total_results if available
        total_pages = self._estimate_total_pages()
        source = self._get_selected_source()

        # Update page spinner
        self.page_spin.blockSignals(True)
        self.page_spin.setMinimum(1)
        self.page_spin.setMaximum(max(1, total_pages))
        self.page_spin.setValue(self.current_page + 1)  # Display is 1-based
        self.page_spin.blockSignals(False)

        # Update total pages label
        if self.total_results > 0:
            self.page_total_label.setText(f"of {total_pages}")
        elif self.has_more_results:
            self.page_total_label.setText(f"of {total_pages}+")
        else:
            self.page_total_label.setText(f"of {total_pages}")

        # Enable/disable navigation buttons
        self.first_page_btn.setEnabled(self.current_page > 0)
        self.prev_page_btn.setEnabled(self.current_page > 0)
        
        # For CurseForge, disable next button on page 200 (index 199, max page limit)
        at_curseforge_limit = source == 'curseforge' and self.current_page >= CURSEFORGE_MAX_PAGES - 1
        can_go_next = (self.has_more_results or self.current_page < total_pages - 1) and not at_curseforge_limit
        self.next_page_btn.setEnabled(can_go_next)
        
        # Last button is enabled when we know the total results from API
        # This allows navigation to the last page even when we haven't loaded all pages
        has_known_total = self.total_results > 0
        self.last_page_btn.setEnabled(has_known_total and self.current_page < total_pages - 1 and not at_curseforge_limit)

    def _estimate_total_pages(self) -> int:
        """Estimate total number of pages based on current data."""
        source = self._get_selected_source()
        max_pages = CURSEFORGE_MAX_PAGES if source == 'curseforge' else 9999
        
        if self.total_results > 0:
            calculated_pages = (self.total_results + SEARCH_PAGE_SIZE - 1) // SEARCH_PAGE_SIZE
            return min(calculated_pages, max_pages)
        elif len(self.all_search_results) > 0:
            # If we have results but no total, estimate from current count
            pages_loaded = self.current_page + 1
            if self.has_more_results:
                return min(pages_loaded + 1, max_pages)  # At least one more page
            return min(pages_loaded, max_pages)
        return 1  # Minimum 1 page

    def _go_to_first_page(self):
        """Navigate to the first page."""
        if self.current_page != 0:
            self._navigate_to_page(0)

    def _go_to_prev_page(self):
        """Navigate to the previous page."""
        if self.current_page > 0:
            self._navigate_to_page(self.current_page - 1)

    def _go_to_next_page(self):
        """Navigate to the next page."""
        self._navigate_to_page(self.current_page + 1)

    def _go_to_last_page(self):
        """Navigate to the last page based on total results from API."""
        total_pages = self._estimate_total_pages()
        if total_pages > 0 and self.current_page < total_pages - 1:
            last_page = total_pages - 1
            self._navigate_to_page(last_page)

    def _on_page_spin_changed(self, value: int):
        """Handle page spinner value change."""
        target_page = value - 1  # Convert from 1-based to 0-based
        if target_page != self.current_page:
            self._navigate_to_page(target_page)

    def _navigate_to_page(self, target_page: int):
        """Navigate to a specific page."""
        if self._is_loading_page:
            return

        source = self._get_selected_source()
        
        # Save source page state
        self._source_page_state[source] = {
            'page': target_page,
            'total': self.total_results,
            'has_more': self.has_more_results
        }
        
        # Also save page to class-level session filters
        ModBrowserDialog._session_filters[source]['page'] = target_page

        self.current_page = target_page
        
        # Load the page
        self._load_page(target_page)

    def _load_page(self, page: int):
        """Load a specific page of results."""
        if self._is_loading_page:
            return

        self._is_loading_page = True

        # Cancel any pending icon loads
        self._cancel_all_icon_loads()

        source = self._get_selected_source()
        query = self.search_edit.text().strip()
        
        # Get version filter from combo box
        version_text = self.version_filter.currentText().strip()
        version_filter = version_text if version_text and version_text != "Any" else ""
        
        # Get sort option based on current source
        sort_by = self._get_current_sort_value()
        
        # Get loader filter
        loader_name = self.loader_combo.currentText()
        loader_filter = MOD_LOADER_OPTIONS.get(loader_name, "")
        
        # Enforce CurseForge max page limit
        if source == 'curseforge' and page >= CURSEFORGE_MAX_PAGES:
            page = CURSEFORGE_MAX_PAGES - 1
            self.current_page = page

        # Clear current results
        self.results_list.clear()
        self.selected_mod = None
        self.selected_version = None
        self.versions_combo.clear()
        self.add_btn.setEnabled(False)

        self.search_status.setText(f"Loading page {page + 1}...")

        # Stop any running search thread
        if self.search_thread:
            try:
                if self._thread_is_running(self.search_thread):
                    self.search_thread.stop()
                    self.search_thread.wait()
            except Exception:
                pass
            self.search_thread = None
            self.search_in_progress = False

        # Create search thread with offset for the target page
        self.search_thread = ModSearchThread(source, query, version_filter, sort_by, loader_filter)
        self.search_thread.offset = page * SEARCH_PAGE_SIZE
        self.search_thread.started.connect(lambda: setattr(self, "search_in_progress", True))
        self.search_thread.search_complete.connect(self._on_page_loaded)
        self.search_thread.error_occurred.connect(self._on_page_load_error)
        self.search_thread.finished.connect(self._on_search_thread_finished)
        self.search_thread.finished.connect(self.search_thread.deleteLater)
        self.search_thread.start()
        
        # Preload next page icons for faster experience
        if page < (CURSEFORGE_MAX_PAGES - 1 if source == 'curseforge' else 9999):
            QTimer.singleShot(500, lambda: self._preload_next_page_icons(page + 1))

    def _on_page_loaded(self, results: list, total_count: int = 0):
        """Handle page load completion."""
        self._is_loading_page = False
        source = self._get_selected_source()

        # Store total results from API
        if total_count > 0:
            self.total_results = total_count
            state = self._source_page_state.get(source, {})
            state['total'] = total_count
            self._source_page_state[source] = state

        # Check if there are more results
        self.has_more_results = len(results) >= SEARCH_PAGE_SIZE
        
        # Store results locally
        self.all_search_results = results

        # Display results with icons
        self._display_page_results(results, source)

        # Update pagination
        self._update_pagination_controls()

        # Update status
        if len(results) == 0:
            self.search_status.setText("No results found")
        else:
            page_start = self.current_page * SEARCH_PAGE_SIZE + 1
            page_end = page_start + len(results) - 1
            status_text = f"Showing {page_start}-{page_end}"
            if self.total_results > 0:
                status_text += f" of {self.total_results}"
            self.search_status.setText(status_text)

        # Load icons for visible items
        QTimer.singleShot(50, self._load_visible_icons)

    def _on_page_load_error(self, error: str):
        """Handle page load error."""
        self._is_loading_page = False
        self.search_status.setText(f"Error: {error}")
        self._update_pagination_controls()

    def _display_page_results(self, results: list, source: str):
        """Display page results with cached icons applied immediately."""
        self.results_list.clear()
        
        # Show "No results" message if empty
        if len(results) == 0:
            theme = get_current_theme()
            no_results_item = QListWidgetItem("No results found\nTry a different search or filter")
            no_results_item.setFlags(no_results_item.flags() & ~Qt.ItemFlag.ItemIsSelectable)  # Make not selectable
            no_results_item.setForeground(QColor(theme['text_secondary']))
            self.results_list.addItem(no_results_item)
            return
        
        # Create a placeholder icon for items without cached icons
        placeholder_pixmap = self._create_placeholder_icon()

        for mod in results:
            item = QListWidgetItem()
            item.setText(f"{mod['name']}\nby {mod['author']} • {mod['downloads']:,} downloads")
            item.setData(Qt.ItemDataRole.UserRole, mod)

            # Check if icon is already cached
            mod_id = mod.get('id', mod.get('slug', ''))
            if source in self._icon_cache and mod_id in self._icon_cache[source]:
                # Apply cached icon immediately
                self._apply_icon_to_item(item, self._icon_cache[source][mod_id])
            else:
                # Set placeholder icon while loading
                if placeholder_pixmap:
                    item.setIcon(QIcon(placeholder_pixmap))

            self.results_list.addItem(item)
    
    def _create_placeholder_icon(self) -> Optional[QPixmap]:
        """Create a placeholder icon for items without cached icons."""
        try:
            theme = get_current_theme()
            size = 40
            pixmap = QPixmap(size, size)
            pixmap.fill(Qt.GlobalColor.transparent)
            
            from PyQt6.QtGui import QPainter, QFont, QColor
            painter = QPainter(pixmap)
            painter.setRenderHint(QPainter.RenderHint.Antialiasing)
            
            # Draw a rounded rectangle background
            bg_color = QColor(theme['bg_tertiary'])
            painter.setBrush(bg_color)
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRoundedRect(0, 0, size, size, 6, 6)
            
            # Draw the package emoji
            font = QFont()
            font.setPixelSize(20)
            painter.setFont(font)
            painter.setPen(QColor(theme['text_secondary']))
            painter.drawText(pixmap.rect(), Qt.AlignmentFlag.AlignCenter, "📦")
            
            painter.end()
            return pixmap
        except Exception:
            return None

    def _cancel_all_icon_loads(self):
        """Cancel all pending icon load threads."""
        for thread in list(self.icon_threads):
            try:
                if self._thread_is_running(thread):
                    thread.stop()
                    thread.wait(500)
            except Exception:
                pass
        self.icon_threads.clear()
        self._loading_mod_ids.clear()

    def _apply_icon_to_item(self, item: QListWidgetItem, data: bytes):
        """Apply icon data to a list item."""
        try:
            pixmap = QPixmap()
            if pixmap.loadFromData(data):
                icon = QIcon(pixmap)
                item.setIcon(icon)
        except Exception:
            pass

    def load_popular_mods(self):
        """Load popular mods without search query."""
        # Reset pagination state
        self.current_page = 0
        self.total_results = 0
        self.has_more_results = True
        self.all_search_results = []

        self._cancel_all_icon_loads()

        self.results_list.clear()
        self.versions_combo.clear()
        self.selected_mod = None
        self.selected_version = None
        self.add_btn.setEnabled(False)
        self.search_status.setText("Loading mods...")
        self.results_header.setText("Mods:")

        self._update_pagination_controls()

        # Load first page
        self._load_page(0)

    def on_source_changed(self):
        """Handle source tab change."""
        source = self._get_selected_source()
        
        # Update sort options for the new source
        self._update_sort_options()
        
        # Restore session filter state for this source from class-level storage
        self.version_filter.blockSignals(True)
        self.sort_combo.blockSignals(True)
        self.loader_combo.blockSignals(True)
        
        saved_filters = ModBrowserDialog._session_filters.get(source, {})
        if saved_filters.get('version'):
            idx = self.version_filter.findText(saved_filters['version'])
            if idx >= 0:
                self.version_filter.setCurrentIndex(idx)
            else:
                self.version_filter.setCurrentIndex(0)
        else:
            self.version_filter.setCurrentIndex(0)
            
        if saved_filters.get('sort', 0) < self.sort_combo.count():
            self.sort_combo.setCurrentIndex(saved_filters.get('sort', 0))
        if saved_filters.get('loader', 0) < self.loader_combo.count():
            self.loader_combo.setCurrentIndex(saved_filters.get('loader', 0))
        
        self.version_filter.blockSignals(False)
        self.sort_combo.blockSignals(False)
        self.loader_combo.blockSignals(False)
        
        # Restore page state for this source
        state = self._source_page_state.get(source, {'page': 0, 'total': 0, 'has_more': True})
        self.current_page = state.get('page', 0)
        self.total_results = state.get('total', 0)
        self.has_more_results = state.get('has_more', True)
        self.all_search_results = []

        self.results_list.clear()
        self.versions_combo.clear()
        self.selected_mod = None
        self.selected_version = None
        self.add_btn.setEnabled(False)
        self.mod_info_header.setText("Select a mod to view its description")
        self.description_browser.setHtml("")

        self._update_pagination_controls()
        self.load_popular_mods()

    def search_mods(self):
        """Search for mods on the selected platform."""
        query = self.search_edit.text().strip()

        # Reset pagination state
        self.current_page = 0
        self.total_results = 0
        self.has_more_results = True
        self.all_search_results = []

        self._cancel_all_icon_loads()

        self.results_list.clear()
        self.versions_combo.clear()
        self.selected_mod = None
        self.selected_version = None
        self.add_btn.setEnabled(False)

        if query:
            self.search_status.setText("Searching...")
            self.results_header.setText(f"🔍 Search Results for '{query}':")
        else:
            self.search_status.setText("Loading mods...")
            self.results_header.setText("Popular Mods:")

        self._update_pagination_controls()

        # Load first page with current query
        self._load_page(0)

    def on_search_error(self, error: str):
        """Handle search error."""
        self.search_status.setText(f"Error: {error}")

    def on_mod_selected(self, item: QListWidgetItem):
        """Handle mod selection."""
        mod = item.data(Qt.ItemDataRole.UserRole)
        if not mod:
            return

        self.selected_mod = mod
        self.selected_version = None
        self.add_btn.setEnabled(False)

        # Update mod info header with name, author, downloads info
        self.mod_info_header.setText(f"{mod['name']} by {mod['author']} • {mod['downloads']:,} downloads")

        # Fetch versions FIRST (before description) for faster usability
        self.versions_combo.clear()
        self.versions_combo.addItem("Loading versions...")

        # Use safe thread check to avoid RuntimeError on deleted C++ objects
        if self._thread_is_running(self.version_thread):
            self.version_thread.stop()
            self.version_thread.wait()
        self.version_thread = None  # Clear reference after stopping

        game_version_text = self.version_filter.currentText().strip()
        game_version = game_version_text if game_version_text and game_version_text != "Any" else ""
        self.version_thread = ModVersionFetchThread(mod['source'], mod['id'], game_version)
        self.version_thread.versions_fetched.connect(self.on_versions_fetched)
        self.version_thread.error_occurred.connect(self.on_versions_error)
        self.version_thread.finished.connect(self._on_version_thread_finished)
        self.version_thread.finished.connect(self.version_thread.deleteLater)
        self.version_thread.start()

        # Show loading message for description (loaded after versions)
        self.description_browser.setHtml("<i>Loading description...</i>")

        # Fetch full description AFTER starting version fetch
        # Use safe thread check to avoid RuntimeError on deleted C++ objects
        if self._thread_is_running(self.description_thread):
            self.description_thread.stop()
            self.description_thread.wait(1000)  # Wait up to 1 second
        self.description_thread = None  # Clear reference after stopping

        self.description_thread = ModDescriptionFetchThread(mod['source'], mod['id'])
        self.description_thread.description_fetched.connect(self.on_description_fetched)
        self.description_thread.error_occurred.connect(self.on_description_error)
        self.description_thread.finished.connect(self._on_description_thread_finished)
        self.description_thread.finished.connect(self.description_thread.deleteLater)
        self.description_thread.start()

    def on_description_fetched(self, description: str):
        """Handle full description fetch."""
        # Check if description is HTML by looking for common HTML tags
        # CurseForge returns HTML, Modrinth returns Markdown
        html_pattern = r'<\s*(p|div|span|br|img|a|h[1-6]|ul|ol|li|strong|em|b|i)\b'
        is_html = bool(re.search(html_pattern, description, re.IGNORECASE))

        if is_html:
            self.description_browser.setHtml(description)
        else:
            # Convert markdown-style text to basic HTML
            # Replace newlines with <br> for better display
            html_desc = description.replace('\n', '<br>')
            self.description_browser.setHtml(html_desc)

    def on_description_error(self, error: str):
        """Handle description fetch error."""
        # Fall back to summary if description fetch fails
        if self.selected_mod is not None and isinstance(self.selected_mod, dict):
            self.description_browser.setHtml(self.selected_mod.get('summary', ''))
        else:
            self.description_browser.setHtml('')

    def on_versions_fetched(self, versions: list):
        """Handle version list."""
        self.versions_combo.clear()

        for v in versions:
            game_vers = ', '.join(v['game_versions'][:3])
            if len(v['game_versions']) > 3:
                game_vers += '...'
            self.versions_combo.addItem(f"[{v['release_type']}] {v['name']} ({game_vers})", v)

        # Auto-select the first (most recent) version
        if self.versions_combo.count() > 0:
            self.versions_combo.setCurrentIndex(0)
            version = self.versions_combo.currentData()
            if version:
                self.selected_version = version
                self.add_btn.setEnabled(True)

    def on_versions_error(self, error: str):
        """Handle version fetch error."""
        self.versions_combo.clear()
        self.versions_combo.addItem(f"Error: {error}")

    def on_version_combo_changed(self, index: int):
        """Handle version combo box selection change."""
        if index < 0:
            return
        version = self.versions_combo.currentData()
        if version:
            self.selected_version = version
            self.add_btn.setEnabled(True)

    def add_selected_mod(self):
        """Add the selected mod."""
        if not self.selected_mod or not self.selected_version:
            return
        self.accept()

    def get_mod(self) -> Optional[ModEntry]:
        """Get the selected mod as a ModEntry."""
        if not self.selected_mod or not self.selected_version:
            return None

        mod = ModEntry()
        # Leave display_name blank - user should set info_name manually if needed
        mod.display_name = ''
        mod.id = self.selected_mod['slug'] or self.selected_mod['id']
        # Leave file_name blank by default - don't autofill
        mod.file_name = ''
        mod.since = self.current_version

        # Store icon URL for later fetching
        mod._icon_url = self.selected_mod.get('icon_url', '')

        if self.selected_mod['source'] == 'curseforge':
            try:
                project_id = int(self.selected_mod['id'])
                file_id = int(self.selected_version['file_id'])
            except (ValueError, TypeError):
                project_id = 0
                file_id = 0
            mod.source = {
                'type': 'curseforge',
                'projectId': project_id,
                'fileId': file_id
            }
        else:
            mod.source = {
                'type': 'modrinth',
                'projectSlug': self.selected_mod['slug'],
                'versionId': str(self.selected_version['file_id'])
            }

        return mod

    def closeEvent(self, event):
        """Clean up threads when dialog is closed."""
        self._cleanup_threads()
        super().closeEvent(event)

    def _cleanup_threads(self):
        """Stop and clean up all running threads."""
        # Stop scroll debounce timer
        if self._scroll_debounce_timer:
            self._scroll_debounce_timer.stop()

        # Stop search thread
        try:
            if self._thread_is_running(self.search_thread):
                self.search_thread.stop()
                self.search_thread.wait(1000)
        except Exception:
            pass
        finally:
            self.search_thread = None
            self.search_in_progress = False

        # Stop version thread
        try:
            if self._thread_is_running(self.version_thread):
                self.version_thread.stop()
                self.version_thread.wait(1000)
        except Exception:
            pass
        finally:
            self.version_thread = None

        # Stop description thread
        try:
            if self._thread_is_running(self.description_thread):
                self.description_thread.stop()
                self.description_thread.wait(1000)
        except Exception:
            pass
        finally:
            self.description_thread = None

        # Stop icon threads
        for thread in self.icon_threads:
            try:
                if self._thread_is_running(thread):
                    thread.stop()
                    thread.wait(100)
            except Exception:
                pass
        self.icon_threads.clear()
        self._loading_mod_ids.clear()

        # Shutdown description browser image threads
        if hasattr(self, 'description_browser'):
            try:
                self.description_browser.shutdown()
            except Exception:
                pass

    def _thread_is_running(self, t) -> bool:
        """Return True if QThread is running, without crashing on deleted C++ objects."""
        try:
            return t is not None and t.isRunning()
        except RuntimeError:
            return False

    def _on_search_thread_finished(self):
        """Reset state when the search thread finishes."""
        self.search_in_progress = False
        self.search_thread = None

    def _on_version_thread_finished(self):
        """Reset state when the version thread finishes."""
        self.version_thread = None

    def _on_description_thread_finished(self):
        """Reset state when the description thread finishes."""
        self.description_thread = None


# === Grid Item Widget ===
class ItemCard(QFrame):
    """Clickable card widget for grid display."""
    clicked = pyqtSignal()
    double_clicked = pyqtSignal()

    def __init__(self, name: str, icon_path: str = "", is_add_button: bool = False, icon_data: bytes = None, parent=None):
        super().__init__(parent)
        self.name = name
        self.icon_path = icon_path
        self.is_add_button = is_add_button
        self.selected = False
        self._icon_data = icon_data
        self.setup_ui()

    def setup_ui(self):
        self.setFixedSize(120, 120)  # Made slightly bigger
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        # Do not call update_style() here because some widgets (like name_label) are not created yet.

        theme = get_current_theme()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(4)

        self.icon_label = QLabel()
        self.icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.icon_label.setFixedSize(56, 56)  # Made icon slightly bigger

        if self.is_add_button:
            self.icon_label.setText("+")
            # Use a more visible color that works on both light and dark themes
            self.icon_label.setStyleSheet(f"font-size: 36px; font-weight: bold; background-color: transparent; color: {theme['text_primary']};")
        elif self._icon_data:
            # Load icon from bytes data
            self._load_icon_from_bytes(self._icon_data)
            self.icon_label.setStyleSheet("background-color: transparent;")
        elif self.icon_path and os.path.exists(self.icon_path):
            pixmap = QPixmap(self.icon_path)
            if not pixmap.isNull():
                self.icon_label.setPixmap(pixmap.scaled(56, 56, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
                self.icon_label.setStyleSheet("background-color: transparent;")
            else:
                self._set_default_icon()
        else:
            self._set_default_icon()

        layout.addWidget(self.icon_label, alignment=Qt.AlignmentFlag.AlignCenter)

        self.name_label = QLabel(self.name if not self.is_add_button else "Add")
        self.name_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.name_label.setWordWrap(True)
        self.name_label.setStyleSheet(f"font-size: 11px; background-color: transparent; color: {theme['text_primary']};")
        layout.addWidget(self.name_label)

        # Now that the UI elements are created, set the initial style.
        self.update_style()

    def _set_default_icon(self):
        """Set the default package icon."""
        theme = get_current_theme()
        self.icon_label.setText("📦")
        self.icon_label.setStyleSheet(f"font-size: 28px; background-color: transparent; color: {theme['text_primary']};")  # Slightly bigger icon

    def _load_icon_from_bytes(self, data: bytes):
        """Load icon from bytes data."""
        try:
            pixmap = QPixmap()
            if pixmap.loadFromData(data):
                self.icon_label.setPixmap(pixmap.scaled(56, 56, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
                self.icon_label.setStyleSheet("background-color: transparent;")
            else:
                self._set_default_icon()
        except Exception:
            self._set_default_icon()

    def update_style(self):
        # Use theme colors directly for proper theming
        theme = get_current_theme()
        if self.selected:
            self.setStyleSheet(f"""
                ItemCard {{
                    background-color: {theme['accent']};
                    border: 2px solid {theme['accent']};
                    border-radius: 8px;
                }}
            """)
            # Update label colors for selected state, if label exists
            if hasattr(self, "name_label"):
                self.name_label.setStyleSheet(f"font-size: 11px; background-color: transparent; color: {theme['bg_primary']};")
        else:
            self.setStyleSheet(f"""
                ItemCard {{
                    background-color: {theme['bg_secondary']};
                    border: 2px solid {theme['border']};
                    border-radius: 8px;
                }}
                ItemCard:hover {{
                    border-color: {theme['accent']};
                }}
            """)
            # Update label colors for normal state, if label exists
            if hasattr(self, "name_label"):
                self.name_label.setStyleSheet(f"font-size: 11px; background-color: transparent; color: {theme['text_primary']};")

    def set_selected(self, selected: bool):
        self.selected = selected
        self.update_style()

    def set_icon(self, pixmap: QPixmap):
        if not pixmap.isNull():
            self.icon_label.setPixmap(pixmap.scaled(48, 48, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))

    def set_icon_from_bytes(self, data: bytes):
        """Set icon from bytes data."""
        self._load_icon_from_bytes(data)

    def mousePressEvent(self, event):
        self.clicked.emit()

    def mouseDoubleClickEvent(self, event):
        self.double_clicked.emit()


# === Version Card Widget ===
class VersionCard(QFrame):
    """Card widget for displaying versions with optional delete button."""
    clicked = pyqtSignal(str)
    delete_clicked = pyqtSignal(str)

    def __init__(self, version: str, is_latest: bool = False, is_new: bool = True, icon_path: str = "", is_add_button: bool = False, parent=None):
        super().__init__(parent)
        self.version = version
        self.is_latest = is_latest
        self.is_new = is_new
        self.icon_path = icon_path
        self.is_add_button = is_add_button
        self.setup_ui()

    def setup_ui(self):
        self.setFixedSize(110, 120)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.update_style()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(2)

        theme = get_current_theme()

        # Header with delete button for all versions (not the add button)
        if not self.is_add_button:
            header_layout = QHBoxLayout()
            header_layout.setContentsMargins(0, 0, 0, 0)
            header_layout.addStretch()

            # Create clickable delete button with visible X
            self.delete_button = QPushButton("×")
            self.delete_button.setFixedSize(20, 20)
            self.delete_button.setToolTip("Delete this version")
            self.delete_button.setCursor(Qt.CursorShape.PointingHandCursor)
            self.delete_button.setStyleSheet(f"""
                QPushButton {{
                    background-color: {theme['danger']};
                    color: white;
                    border: none;
                    border-radius: 10px;
                    font-weight: bold;
                    font-size: 16px;
                    padding: 0 0 2px 0;
                }}
                QPushButton:hover {{
                    background-color: {theme['accent_hover']};
                }}
            """)
            self.delete_button.clicked.connect(self.on_delete_clicked)
            header_layout.addWidget(self.delete_button)
            layout.addLayout(header_layout)

        # Icon
        self.icon_label = QLabel()
        self.icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.icon_label.setFixedSize(40, 40)

        if self.is_add_button:
            self.icon_label.setText("+")
            self.icon_label.setStyleSheet(f"font-size: 28px; font-weight: bold; background-color: transparent; color: {theme['text_primary']};")
        elif self.icon_path and os.path.exists(self.icon_path):
            pixmap = QPixmap(self.icon_path)
            if not pixmap.isNull():
                self.icon_label.setPixmap(pixmap.scaled(40, 40, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
                self.icon_label.setStyleSheet("background-color: transparent;")
            else:
                self.icon_label.setText("📦")
                self.icon_label.setStyleSheet(f"font-size: 20px; background-color: transparent; color: {theme['text_primary']};")
        else:
            self.icon_label.setText("📦")
            self.icon_label.setStyleSheet(f"font-size: 20px; background-color: transparent; color: {theme['text_primary']};")

        layout.addWidget(self.icon_label, alignment=Qt.AlignmentFlag.AlignCenter)

        # Version name
        if self.is_add_button:
            name_text = "Add"
        elif self.is_latest:
            name_text = f"{self.version}\n(Latest)"
        else:
            name_text = self.version

        self.name_label = QLabel(name_text)
        self.name_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.name_label.setWordWrap(True)
        self.name_label.setStyleSheet(f"font-size: 11px; background-color: transparent; color: {theme['text_primary']};")
        layout.addWidget(self.name_label)

        layout.addStretch()

    def update_style(self):
        theme = get_current_theme()
        self.setStyleSheet(f"""
            VersionCard {{
                background-color: {theme['bg_secondary']};
                border: 2px solid {theme['border']};
                border-radius: 8px;
            }}
            VersionCard:hover {{
                border-color: {theme['accent']};
            }}
        """)

    def on_delete_clicked(self):
        self.delete_clicked.emit(self.version)

    def mousePressEvent(self, event):
        self.clicked.emit(self.version)


# === Mod Editor Panel ===
class ModEditorPanel(QWidget):
    """Right panel for editing a selected mod."""
    mod_changed = pyqtSignal()
    mod_saved = pyqtSignal()  # Emitted when save is clicked - to close panel
    mod_deleted = pyqtSignal(object)  # Emitted when delete is confirmed, passes the mod
    hash_requested = pyqtSignal(str)
    icon_changed = pyqtSignal()  # Emitted when icon is added/changed

    def __init__(self, parent=None):
        super().__init__(parent)
        self.current_mod: Optional[ModEntry] = None
        self.hash_calculator: Optional[HashCalculator] = None
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)

        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(16)

        # ID Section (read-only after creation)
        id_group = QGroupBox("Identifier")
        id_layout = QFormLayout(id_group)
        self.id_edit = QLineEdit()
        self.id_edit.setPlaceholderText("Unique mod ID")
        id_layout.addRow("ID:", self.id_edit)
        scroll_layout.addWidget(id_group)

        # Hash Section - Read only, automatically filled
        hash_group = QGroupBox("Hash")
        hash_layout = QVBoxLayout(hash_group)
        self.hash_edit = QLineEdit()
        self.hash_edit.setPlaceholderText("SHA-256 hash (auto-filled)")
        self.hash_edit.setReadOnly(True)  # Make hash field read-only
        self.hash_edit.setToolTip("Hash is automatically calculated when you click 'Auto-fill Hash'")
        hash_layout.addWidget(self.hash_edit)

        hash_btn_layout = QHBoxLayout()
        self.auto_hash_btn = QPushButton("Auto-fill Hash")
        self.auto_hash_btn.clicked.connect(self.auto_fill_hash)
        hash_btn_layout.addWidget(self.auto_hash_btn)
        self.hash_progress = QProgressBar()
        self.hash_progress.setVisible(False)
        hash_btn_layout.addWidget(self.hash_progress)
        hash_layout.addLayout(hash_btn_layout)
        scroll_layout.addWidget(hash_group)

        # Source Section
        source_group = QGroupBox("Source")
        source_layout = QFormLayout(source_group)

        source_btn_layout = QHBoxLayout()
        self.curseforge_btn = QPushButton("CurseForge")
        self.curseforge_btn.setCheckable(True)
        self.curseforge_btn.clicked.connect(lambda: self.set_source_type('curseforge'))
        source_btn_layout.addWidget(self.curseforge_btn)

        self.modrinth_btn = QPushButton("Modrinth")
        self.modrinth_btn.setCheckable(True)
        self.modrinth_btn.clicked.connect(lambda: self.set_source_type('modrinth'))
        source_btn_layout.addWidget(self.modrinth_btn)

        self.url_btn = QPushButton("URL")
        self.url_btn.setCheckable(True)
        self.url_btn.clicked.connect(lambda: self.set_source_type('url'))
        source_btn_layout.addWidget(self.url_btn)

        source_layout.addRow("Type:", source_btn_layout)

        self.mod_id_edit = QLineEdit()
        self.mod_id_edit.setPlaceholderText("Project ID or slug")
        source_layout.addRow("Mod ID:", self.mod_id_edit)

        self.file_id_edit = QLineEdit()
        self.file_id_edit.setPlaceholderText("File ID or version ID")
        source_layout.addRow("File ID:", self.file_id_edit)

        self.url_edit = QLineEdit()
        self.url_edit.setPlaceholderText("Direct download URL")
        source_layout.addRow("URL:", self.url_edit)

        scroll_layout.addWidget(source_group)

        # Install Location
        location_group = QGroupBox("Install Location")
        location_layout = QFormLayout(location_group)
        self.install_location_edit = QLineEdit()
        self.install_location_edit.setPlaceholderText("mods")
        self.install_location_edit.setText("mods")
        location_layout.addRow("Folder:", self.install_location_edit)

        self.file_name_edit = QLineEdit()
        self.file_name_edit.setPlaceholderText("Optional: custom filename")
        location_layout.addRow("File Name:", self.file_name_edit)

        scroll_layout.addWidget(location_group)

        # Naming Section
        naming_group = QGroupBox("Naming")
        naming_layout = QFormLayout(naming_group)

        # Display Name - just shown under boxes in config GUI
        self.display_name_edit = QLineEdit()
        self.display_name_edit.setPlaceholderText("Name shown under mod card (GUI only)")
        naming_layout.addRow("Display Name:", self.display_name_edit)

        theme = get_current_theme()
        display_note = QLabel("Shown under mod card in editor only")
        display_note.setStyleSheet(f"font-size: 11px; color: {theme['text_secondary']};")
        naming_layout.addRow("", display_note)

        # Info Name - saves as display_name in config
        self.info_name_edit = QLineEdit()
        self.info_name_edit.setPlaceholderText("Name saved to config (blank by default)")
        naming_layout.addRow("Info Name:", self.info_name_edit)

        info_note = QLabel("Saved as 'display_name' in config file")
        info_note.setStyleSheet(f"font-size: 11px; color: {theme['text_secondary']};")
        naming_layout.addRow("", info_note)

        scroll_layout.addWidget(naming_group)

        # Icon Section - for custom icons
        icon_group = QGroupBox("Icon (Optional)")
        icon_layout = QHBoxLayout(icon_group)

        self.icon_preview = QLabel()
        self.icon_preview.setFixedSize(64, 64)
        self.icon_preview.setStyleSheet(f"border: 2px dashed {theme['border']}; border-radius: 8px;")
        self.icon_preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.icon_preview.setText("No Icon")
        icon_layout.addWidget(self.icon_preview)

        icon_btn_layout = QVBoxLayout()
        self.select_icon_btn = QPushButton("Select Custom Icon...")
        self.select_icon_btn.clicked.connect(self.select_custom_icon)
        icon_btn_layout.addWidget(self.select_icon_btn)

        self.fetch_icon_btn = QPushButton("Fetch from Source")
        self.fetch_icon_btn.setToolTip("Fetch icon from CurseForge/Modrinth")
        self.fetch_icon_btn.clicked.connect(self.fetch_source_icon)
        icon_btn_layout.addWidget(self.fetch_icon_btn)

        self.clear_icon_btn = QPushButton("Clear")
        self.clear_icon_btn.clicked.connect(self.clear_icon)
        icon_btn_layout.addWidget(self.clear_icon_btn)
        icon_btn_layout.addStretch()
        icon_layout.addLayout(icon_btn_layout)
        icon_layout.addStretch()
        scroll_layout.addWidget(icon_group)

        scroll_layout.addStretch()
        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)

        # Action Buttons
        btn_layout = QHBoxLayout()
        self.delete_btn = QPushButton("Delete")
        self.delete_btn.setObjectName("dangerButton")
        self.delete_btn.clicked.connect(self.request_delete)
        btn_layout.addWidget(self.delete_btn)

        btn_layout.addStretch()

        self.save_btn = QPushButton("Save")
        self.save_btn.setObjectName("successButton")
        self.save_btn.clicked.connect(self.save_changes)
        btn_layout.addWidget(self.save_btn)

        layout.addLayout(btn_layout)

        # Connect change signals
        self.id_edit.textChanged.connect(self.on_field_changed)
        self.hash_edit.textChanged.connect(self.on_field_changed)
        self.mod_id_edit.textChanged.connect(self.on_field_changed)
        self.file_id_edit.textChanged.connect(self.on_field_changed)
        self.url_edit.textChanged.connect(self.on_field_changed)
        self.install_location_edit.textChanged.connect(self.on_field_changed)
        self.file_name_edit.textChanged.connect(self.on_field_changed)
        self.display_name_edit.textChanged.connect(self.on_field_changed)
        self.info_name_edit.textChanged.connect(self.on_field_changed)

    def _update_source_button_styles(self):
        """Update source button styles to show selected state with darker tint."""
        # Use theme colors directly for proper theming
        theme = get_current_theme()
        selected_style = f"background-color: {theme['accent']}; border: 2px solid {theme['accent']}; color: {theme['bg_primary']};"
        normal_style = ""

        self.curseforge_btn.setStyleSheet(selected_style if self.curseforge_btn.isChecked() else normal_style)
        self.modrinth_btn.setStyleSheet(selected_style if self.modrinth_btn.isChecked() else normal_style)
        self.url_btn.setStyleSheet(selected_style if self.url_btn.isChecked() else normal_style)

    def set_source_type(self, source_type: str):
        self.curseforge_btn.setChecked(source_type == 'curseforge')
        self.modrinth_btn.setChecked(source_type == 'modrinth')
        self.url_btn.setChecked(source_type == 'url')

        # Update button styles to show selected state
        self._update_source_button_styles()

        # Update field visibility
        is_curseforge = source_type == 'curseforge'
        is_modrinth = source_type == 'modrinth'
        is_url = source_type == 'url'

        self.mod_id_edit.setEnabled(is_curseforge or is_modrinth)
        self.file_id_edit.setEnabled(is_curseforge or is_modrinth)
        self.url_edit.setEnabled(is_url)

        self.mod_id_edit.setPlaceholderText("Project ID" if is_curseforge else "Project slug" if is_modrinth else "")
        self.file_id_edit.setPlaceholderText("File ID" if is_curseforge else "Version ID" if is_modrinth else "")

    def load_mod(self, mod: ModEntry):
        self.current_mod = mod

        # Block signals during load
        self.blockSignals(True)

        self.id_edit.setText(mod.id)
        self.id_edit.setEnabled(mod.is_new())  # Only editable for new mods

        self.hash_edit.setText(mod.hash)
        # Display name is used for GUI display under cards
        # Use mod.id as fallback for display if no display name set
        gui_display_name = getattr(mod, '_gui_display_name', '') or mod.display_name or mod.id
        self.display_name_edit.setText(gui_display_name if gui_display_name != mod.id else '')
        # Info name saves as display_name in config
        self.info_name_edit.setText(mod.display_name)
        self.file_name_edit.setText(mod.file_name)
        self.install_location_edit.setText(mod.install_location or 'mods')

        # Load source
        source = mod.source
        source_type = source.get('type', 'url')
        self.set_source_type(source_type)

        if source_type == 'curseforge':
            self.mod_id_edit.setText(str(source.get('projectId', '')))
            self.file_id_edit.setText(str(source.get('fileId', '')))
            self.url_edit.clear()
        elif source_type == 'modrinth':
            self.mod_id_edit.setText(source.get('projectSlug', ''))
            self.file_id_edit.setText(source.get('versionId', ''))
            self.url_edit.clear()
        else:
            self.mod_id_edit.clear()
            self.file_id_edit.clear()
            self.url_edit.setText(source.get('url', ''))

        # Hide auto-fill hash button for mods from Find and Add (curseforge/modrinth sources)
        # because hash is automatically calculated for these mods
        is_from_api_source = source_type in ['curseforge', 'modrinth']
        self.auto_hash_btn.setVisible(not is_from_api_source)
        
        # Make hash field read-only for API sources (hash is auto-calculated)
        # For URL sources, hash can still be edited if needed
        self.hash_edit.setReadOnly(is_from_api_source)

        # Load icon preview
        self._update_icon_preview()

        # If mod has icon URL but no icon data, try to fetch it
        if hasattr(mod, '_icon_url') and mod._icon_url and not mod._icon_data:
            self.fetch_source_icon()

        self.blockSignals(False)

        # Auto-fill hash if from curseforge/modrinth and no hash is set
        # Use short delay to allow UI to update first before starting the hash calculation
        if is_from_api_source and not mod.hash:
            QTimer.singleShot(100, self.auto_fill_hash)

    def _update_icon_preview(self):
        """Update the icon preview label."""
        theme = get_current_theme()
        if self.current_mod and self.current_mod._icon_data:
            pixmap = QPixmap()
            if pixmap.loadFromData(self.current_mod._icon_data):
                self.icon_preview.setPixmap(pixmap.scaled(60, 60, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
                self.icon_preview.setStyleSheet(f"border: 2px solid {theme['accent']}; border-radius: 8px;")
            else:
                self._set_no_icon()
        elif self.current_mod and self.current_mod.icon_path and os.path.exists(self.current_mod.icon_path):
            pixmap = QPixmap(self.current_mod.icon_path)
            if not pixmap.isNull():
                self.icon_preview.setPixmap(pixmap.scaled(60, 60, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
                self.icon_preview.setStyleSheet(f"border: 2px solid {theme['accent']}; border-radius: 8px;")
            else:
                self._set_no_icon()
        else:
            self._set_no_icon()

    def _set_no_icon(self):
        """Set the icon preview to show no icon."""
        theme = get_current_theme()
        self.icon_preview.clear()
        self.icon_preview.setText("No Icon")
        self.icon_preview.setStyleSheet(f"border: 2px dashed {theme['border']}; border-radius: 8px;")

    def select_custom_icon(self):
        """Select a custom icon file."""
        file_path, _ = QFileDialog.getOpenFileName(
            self, "Select Icon", "", "Images (*.png *.jpg *.jpeg *.gif *.ico)"
        )
        if file_path and self.current_mod:
            self.current_mod.icon_path = file_path
            self.current_mod._icon_data = None  # Clear any fetched icon data
            with open(file_path, 'rb') as f:
                self.current_mod._icon_data = f.read()
            self._update_icon_preview()
            self.icon_changed.emit()

    def fetch_source_icon(self):
        """Fetch icon from CurseForge or Modrinth source."""
        if not self.current_mod:
            return

        # Check if we have a stored icon URL
        icon_url = getattr(self.current_mod, '_icon_url', None)

        if not icon_url:
            # Try to fetch from source
            source = self.current_mod.source
            source_type = source.get('type', '')

            if source_type == 'curseforge':
                project_id = source.get('projectId', '')
                if project_id:
                    try:
                        url = f"{CF_PROXY_BASE_URL}/mods/{project_id}"
                        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                        with urllib.request.urlopen(req, timeout=10) as response:
                            data = json.loads(response.read())
                            mod_data = data.get('data', data)
                            logo = mod_data.get('logo', {})
                            icon_url = logo.get('thumbnailUrl', logo.get('url', ''))
                    except Exception as e:
                        QMessageBox.warning(self, "Error", f"Failed to fetch icon from CurseForge: {e}")
                        return
            elif source_type == 'modrinth':
                project_slug = source.get('projectSlug', '')
                if project_slug:
                    try:
                        url = f"https://api.modrinth.com/v2/project/{project_slug}"
                        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                        with urllib.request.urlopen(req, timeout=10) as response:
                            data = json.loads(response.read())
                            icon_url = data.get('icon_url', '')
                    except Exception as e:
                        QMessageBox.warning(self, "Error", f"Failed to fetch icon from Modrinth: {e}")
                        return

        if not icon_url:
            QMessageBox.information(self, "No Icon", "No icon URL available for this source.")
            return

        # Fetch the icon
        try:
            req = urllib.request.Request(icon_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=10) as response:
                self.current_mod._icon_data = response.read()
                self.current_mod.icon_path = ''  # Clear custom path
                self._update_icon_preview()
                self.icon_changed.emit()
        except Exception as e:
            QMessageBox.warning(self, "Error", f"Failed to download icon: {e}")

    def clear_icon(self):
        """Clear the current icon."""
        if self.current_mod:
            self.current_mod.icon_path = ''
            self.current_mod._icon_data = None
            self._update_icon_preview()
            self.icon_changed.emit()

    def save_changes(self):
        if not self.current_mod:
            return

        self.current_mod.id = self.id_edit.text().strip()
        self.current_mod.hash = self.hash_edit.text().strip()
        # Info name saves to display_name in config
        self.current_mod.display_name = self.info_name_edit.text().strip()
        # Store GUI display name separately (not saved to config)
        self.current_mod._gui_display_name = self.display_name_edit.text().strip()
        self.current_mod.file_name = self.file_name_edit.text().strip()
        self.current_mod.install_location = self.install_location_edit.text().strip() or 'mods'

        # Save source
        if self.curseforge_btn.isChecked():
            self.current_mod.source = {
                'type': 'curseforge',
                'projectId': int(self.mod_id_edit.text()) if self.mod_id_edit.text().isdigit() else 0,
                'fileId': int(self.file_id_edit.text()) if self.file_id_edit.text().isdigit() else 0
            }
        elif self.modrinth_btn.isChecked():
            self.current_mod.source = {
                'type': 'modrinth',
                'projectSlug': self.mod_id_edit.text().strip(),
                'versionId': self.file_id_edit.text().strip()
            }
        else:
            self.current_mod.source = {
                'type': 'url',
                'url': self.url_edit.text().strip()
            }

        self.current_mod.mark_saved()
        self.mod_changed.emit()
        self.mod_saved.emit()  # Signal to close the editor panel

    def auto_fill_hash(self):
        """Auto-fill hash from URL, CurseForge, or Modrinth source."""
        url = None

        if self.url_btn.isChecked():
            url = self.url_edit.text().strip()
            if not url:
                QMessageBox.warning(self, "No URL", "Please enter a URL first to auto-fill the hash.")
                return
        elif self.curseforge_btn.isChecked():
            # Fetch download URL from CurseForge using proxy API
            project_id = self.mod_id_edit.text().strip()
            file_id = self.file_id_edit.text().strip()
            if not project_id or not file_id:
                QMessageBox.warning(self, "Missing Info", "Please enter Project ID and File ID first.")
                return
            try:
                # Use the curse.tools proxy which doesn't require API key
                api_url = f"{CF_PROXY_BASE_URL}/mods/{project_id}/files/{file_id}"
                req = urllib.request.Request(api_url, headers={
                    "User-Agent": USER_AGENT
                })
                with urllib.request.urlopen(req, timeout=30) as response:
                    data = json.loads(response.read())
                    # Handle both direct response and nested data response
                    file_data = data.get('data', data)
                    url = file_data.get('downloadUrl')
                    if not url:
                        QMessageBox.warning(self, "Error", "Could not get download URL from CurseForge.")
                        return
            except Exception as e:
                QMessageBox.warning(self, "API Error", f"Failed to fetch from CurseForge:\n{str(e)}")
                return
        elif self.modrinth_btn.isChecked():
            # Fetch download URL from Modrinth
            version_id = self.file_id_edit.text().strip()
            if not version_id:
                QMessageBox.warning(self, "Missing Info", "Please enter Version ID first.")
                return
            try:
                api_url = f"https://api.modrinth.com/v2/version/{version_id}"
                req = urllib.request.Request(api_url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=30) as response:
                    data = json.loads(response.read())
                    files = data.get('files', [])
                    if files:
                        url = files[0].get('url')
                    if not url:
                        QMessageBox.warning(self, "Error", "Could not get download URL from Modrinth.")
                        return
            except Exception as e:
                QMessageBox.warning(self, "API Error", f"Failed to fetch from Modrinth:\n{str(e)}")
                return
        else:
            QMessageBox.warning(self, "No Source", "Please select a source type and enter required information.")
            return

        self.hash_progress.setVisible(True)
        self.hash_progress.setValue(0)
        self.auto_hash_btn.setEnabled(False)

        self.hash_calculator = HashCalculator(url)
        self.hash_calculator.hash_calculated.connect(self.on_hash_calculated)
        self.hash_calculator.progress_updated.connect(self.hash_progress.setValue)
        self.hash_calculator.error_occurred.connect(self.on_hash_error)
        self.hash_calculator.finished.connect(self.hash_calculator.deleteLater)
        self.hash_calculator.start()

    def on_hash_calculated(self, hash_value: str):
        self.hash_edit.setText(hash_value)
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)

    def on_hash_error(self, error: str):
        error_msg = f"Failed to calculate hash:\n{error}"
        # Add helpful hint for common CurseForge errors
        if "400" in error or "403" in error:
            error_msg += "\n\nNote: Some CurseForge files may not be directly downloadable. " \
                        "Try using a direct download link from the CurseForge website."
        QMessageBox.warning(self, "Hash Error", error_msg)
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)

    def on_field_changed(self):
        pass  # Could add unsaved indicator

    def request_delete(self):
        if self.current_mod:
            dialog = ConfirmDeleteDialog(self.current_mod.display_name or self.current_mod.id, "mod", self)
            if dialog.exec():
                self.mod_deleted.emit(self.current_mod)

    def clear(self):
        # Stop any running hash calculation
        if self.hash_calculator is not None:
            try:
                if self.hash_calculator.isRunning():
                    self.hash_calculator.stop()
                    self.hash_calculator.wait(1000)
            except RuntimeError:
                # Handle case where Qt C++ object was deleted
                pass
            self.hash_calculator = None
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)
        self.auto_hash_btn.setVisible(True)  # Reset visibility for next mod
        self.hash_edit.setReadOnly(True)  # Hash is always read-only, calculated via button or auto
        self.current_mod = None
        self.id_edit.clear()
        self.hash_edit.clear()
        self.display_name_edit.clear()
        self.info_name_edit.clear()
        self.file_name_edit.clear()
        self.install_location_edit.setText("mods")
        self.mod_id_edit.clear()
        self.file_id_edit.clear()
        self.url_edit.clear()
        self.set_source_type('url')



# === File Editor Panel ===
class FileEditorPanel(QWidget):
    """Panel for editing a file entry."""
    file_changed = pyqtSignal()
    file_saved = pyqtSignal()  # Emitted when save is clicked - to close panel
    file_deleted = pyqtSignal(object)  # Emitted when delete is confirmed, passes the file

    def __init__(self, parent=None):
        super().__init__(parent)
        self.current_file: Optional[FileEntry] = None
        self.hash_calculator: Optional[HashCalculator] = None
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)

        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(16)

        # Basic Info
        info_group = QGroupBox("File Information")
        info_layout = QFormLayout(info_group)

        # Info Name - saves as display_name in config
        self.info_name_edit = QLineEdit()
        self.info_name_edit.setPlaceholderText("Name saved to config (blank by default)")
        info_layout.addRow("Info Name:", self.info_name_edit)
        
        theme = get_current_theme()
        info_note = QLabel("Saved as 'display_name' in config file")
        info_note.setStyleSheet(f"font-size: 11px; color: {theme['text_secondary']};")
        info_layout.addRow("", info_note)

        # Display Name - just for GUI display under cards
        self.display_name_edit = QLineEdit()
        self.display_name_edit.setPlaceholderText("Name shown under file card (GUI only)")
        info_layout.addRow("Display Name:", self.display_name_edit)
        
        display_note = QLabel("Shown under file card in editor only")
        display_note.setStyleSheet(f"font-size: 11px; color: {theme['text_secondary']};")
        info_layout.addRow("", display_note)

        self.file_name_edit = QLineEdit()
        self.file_name_edit.setPlaceholderText("Optional: custom filename")
        info_layout.addRow("File Name:", self.file_name_edit)

        self.url_edit = QLineEdit()
        self.url_edit.setPlaceholderText("Direct download URL")
        info_layout.addRow("URL:", self.url_edit)

        self.download_path_edit = QLineEdit()
        self.download_path_edit.setPlaceholderText("config/")
        self.download_path_edit.setText("config/")
        info_layout.addRow("Download Path:", self.download_path_edit)

        scroll_layout.addWidget(info_group)

        # Hash Section - Read only, automatically filled
        hash_group = QGroupBox("Hash")
        hash_layout = QVBoxLayout(hash_group)
        self.hash_edit = QLineEdit()
        self.hash_edit.setPlaceholderText("SHA-256 hash (auto-filled)")
        self.hash_edit.setReadOnly(True)  # Make hash field read-only
        self.hash_edit.setToolTip("Hash is automatically calculated when you click 'Auto-fill Hash'")
        hash_layout.addWidget(self.hash_edit)

        hash_btn_layout = QHBoxLayout()
        self.auto_hash_btn = QPushButton("Auto-fill Hash")
        self.auto_hash_btn.clicked.connect(self.auto_fill_hash)
        hash_btn_layout.addWidget(self.auto_hash_btn)
        self.hash_progress = QProgressBar()
        self.hash_progress.setVisible(False)
        hash_btn_layout.addWidget(self.hash_progress)
        hash_layout.addLayout(hash_btn_layout)
        scroll_layout.addWidget(hash_group)

        # Options
        options_group = QGroupBox("Options")
        options_layout = QVBoxLayout(options_group)

        self.overwrite_check = QCheckBox("Overwrite existing file")
        self.overwrite_check.setChecked(True)
        options_layout.addWidget(self.overwrite_check)

        self.extract_check = QCheckBox("Extract ZIP archive")
        options_layout.addWidget(self.extract_check)

        scroll_layout.addWidget(options_group)
        scroll_layout.addStretch()

        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)

        # Buttons
        btn_layout = QHBoxLayout()
        self.delete_btn = QPushButton("Delete")
        self.delete_btn.setObjectName("dangerButton")
        self.delete_btn.clicked.connect(self.request_delete)
        btn_layout.addWidget(self.delete_btn)
        btn_layout.addStretch()
        self.save_btn = QPushButton("Save")
        self.save_btn.setObjectName("successButton")
        self.save_btn.clicked.connect(self.save_changes)
        btn_layout.addWidget(self.save_btn)
        layout.addLayout(btn_layout)

    def load_file(self, file_entry: FileEntry):
        self.current_file = file_entry
        # Info name saves to display_name in config
        self.info_name_edit.setText(file_entry.display_name)
        # Display name is GUI-only display name
        gui_display_name = getattr(file_entry, '_gui_display_name', '') or file_entry.display_name or file_entry.file_name
        self.display_name_edit.setText(gui_display_name if gui_display_name != file_entry.display_name else '')
        self.file_name_edit.setText(file_entry.file_name)
        self.url_edit.setText(file_entry.url)
        self.download_path_edit.setText(file_entry.download_path or 'config/')
        self.hash_edit.setText(file_entry.hash)
        self.overwrite_check.setChecked(file_entry.overwrite)
        self.extract_check.setChecked(file_entry.extract)

    def save_changes(self):
        if not self.current_file:
            return
        # Info name saves to display_name in config
        self.current_file.display_name = self.info_name_edit.text().strip()
        # Store GUI display name separately (not saved to config)
        self.current_file._gui_display_name = self.display_name_edit.text().strip()
        self.current_file.file_name = self.file_name_edit.text().strip()
        self.current_file.url = self.url_edit.text().strip()
        self.current_file.download_path = self.download_path_edit.text().strip() or 'config/'
        self.current_file.hash = self.hash_edit.text().strip()
        self.current_file.overwrite = self.overwrite_check.isChecked()
        self.current_file.extract = self.extract_check.isChecked()
        self.file_changed.emit()
        self.file_saved.emit()  # Signal to close the editor panel

    def auto_fill_hash(self):
        url = self.url_edit.text().strip()
        if not url:
            QMessageBox.warning(self, "No URL", "Please enter a URL first.")
            return
        
        # Validate URL format using urlparse for proper validation
        try:
            parsed = urllib.parse.urlparse(url)
            if parsed.scheme not in ('http', 'https'):
                QMessageBox.warning(self, "Invalid URL", "URL must start with http:// or https://")
                return
            if not parsed.netloc:
                QMessageBox.warning(self, "Invalid URL", "URL must include a valid domain")
                return
        except Exception:
            QMessageBox.warning(self, "Invalid URL", "URL format is invalid")
            return

        self.hash_progress.setVisible(True)
        self.hash_progress.setValue(0)
        self.auto_hash_btn.setEnabled(False)

        self.hash_calculator = HashCalculator(url)
        self.hash_calculator.hash_calculated.connect(self.on_hash_calculated)
        self.hash_calculator.progress_updated.connect(self.hash_progress.setValue)
        self.hash_calculator.error_occurred.connect(self.on_hash_error)
        self.hash_calculator.finished.connect(self.hash_calculator.deleteLater)
        self.hash_calculator.start()

    def on_hash_calculated(self, hash_value: str):
        self.hash_edit.setText(hash_value)
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)

    def on_hash_error(self, error: str):
        error_msg = f"Failed to calculate hash:\n{error}"
        # Add helpful hint for common CurseForge errors
        if "400" in error or "403" in error:
            error_msg += "\n\nNote: Some CurseForge files may not be directly downloadable. " \
                        "Try using a direct download link from the CurseForge website."
        QMessageBox.warning(self, "Hash Error", error_msg)
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)

    def request_delete(self):
        if self.current_file:
            dialog = ConfirmDeleteDialog(self.current_file.display_name, "file", self)
            if dialog.exec():
                self.file_deleted.emit(self.current_file)

    def clear(self):
        # Stop any running hash calculation
        if self.hash_calculator is not None:
            try:
                if self.hash_calculator.isRunning():
                    self.hash_calculator.stop()
                    self.hash_calculator.wait(1000)
            except RuntimeError:
                # Handle case where Qt C++ object was deleted
                pass
            self.hash_calculator = None
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)
        self.current_file = None
        self.info_name_edit.clear()
        self.display_name_edit.clear()
        self.file_name_edit.clear()
        self.url_edit.clear()
        self.download_path_edit.setText("config/")
        self.hash_edit.clear()
        self.overwrite_check.setChecked(True)
        self.extract_check.setChecked(False)


# === Delete Editor Panel ===
class DeleteEditorPanel(QWidget):
    """Panel for editing a delete entry."""
    delete_changed = pyqtSignal()
    delete_saved = pyqtSignal()  # Emitted when save is clicked - to close panel
    delete_entry_deleted = pyqtSignal(object)  # Emitted when delete is confirmed

    def __init__(self, parent=None):
        super().__init__(parent)
        self.current_delete: Optional[DeleteEntry] = None
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        # Info
        info_group = QGroupBox("Delete Entry")
        info_layout = QFormLayout(info_group)

        self.path_edit = QLineEdit()
        self.path_edit.setPlaceholderText("Path to delete (e.g., mods/oldmod.jar)")
        info_layout.addRow("Path:", self.path_edit)

        self.type_combo = QComboBox()
        self.type_combo.addItems(['file', 'folder'])
        info_layout.addRow("Type:", self.type_combo)

        self.reason_edit = QLineEdit()
        self.reason_edit.setPlaceholderText("Optional: reason for deletion")
        info_layout.addRow("Reason:", self.reason_edit)

        layout.addWidget(info_group)
        layout.addStretch()

        # Buttons
        btn_layout = QHBoxLayout()
        self.delete_btn = QPushButton("Delete")
        self.delete_btn.setObjectName("dangerButton")
        self.delete_btn.clicked.connect(self.request_delete)
        btn_layout.addWidget(self.delete_btn)
        btn_layout.addStretch()
        self.save_btn = QPushButton("Save")
        self.save_btn.setObjectName("successButton")
        self.save_btn.clicked.connect(self.save_changes)
        btn_layout.addWidget(self.save_btn)
        layout.addLayout(btn_layout)

    def load_delete(self, delete_entry: DeleteEntry):
        self.current_delete = delete_entry
        self.path_edit.setText(delete_entry.path)
        self.type_combo.setCurrentText(delete_entry.type)
        self.reason_edit.setText(delete_entry.reason)
        # Disable delete button for unremovable entries
        self.delete_btn.setEnabled(not delete_entry._is_unremovable)
        if delete_entry._is_unremovable:
            self.delete_btn.setToolTip("This entry was auto-added and cannot be removed")

    def save_changes(self):
        if not self.current_delete:
            return
        self.current_delete.path = self.path_edit.text().strip()
        self.current_delete.type = self.type_combo.currentText()
        self.current_delete.reason = self.reason_edit.text().strip()
        self.delete_changed.emit()
        self.delete_saved.emit()  # Signal to close the editor panel

    def request_delete(self):
        if self.current_delete:
            if self.current_delete._is_unremovable:
                QMessageBox.warning(self, "Cannot Delete", "This entry was auto-added from a removed mod/file and cannot be deleted.")
                return
            dialog = ConfirmDeleteDialog(self.current_delete.path, "delete entry", self)
            if dialog.exec():
                self.delete_entry_deleted.emit(self.current_delete)

    def clear(self):
        self.current_delete = None
        self.path_edit.clear()
        self.type_combo.setCurrentIndex(0)
        self.reason_edit.clear()
        self.delete_btn.setEnabled(True)
        self.delete_btn.setToolTip("")



# === Version Editor Page ===
class VersionEditorPage(QWidget):
    """Page for editing a specific version (mods, files, deletes)."""
    version_modified = pyqtSignal()
    back_requested = pyqtSignal()
    create_requested = pyqtSignal(object)  # Emitted with version_config when Create is clicked

    # Number of icons to load immediately when opening a version
    INITIAL_ICON_LOAD_COUNT = 8

    def __init__(self, parent=None):
        super().__init__(parent)
        self.version_config: Optional[VersionConfig] = None
        self.selected_mod_index = -1
        self.selected_file_index = -1
        self.selected_delete_index = -1
        self.icon_cache = {}
        # Pending items - items being edited but not yet added to the list
        self._pending_mod: Optional[ModEntry] = None
        self._pending_file: Optional[FileEntry] = None
        self._pending_delete: Optional[DeleteEntry] = None
        # Track which mod icons have been loaded (for lazy loading)
        self._icons_loaded_count = 0
        self._icon_load_threads: List[QThread] = []
        self._remaining_icons_loaded = False  # Whether all remaining icons have been loaded
        self.setup_ui()

    def setup_ui(self):
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(0, 0, 0, 0)

        # Header with back button
        header_layout = QHBoxLayout()
        header_layout.setContentsMargins(16, 12, 16, 12)

        self.back_btn = QPushButton("← Back")
        self.back_btn.clicked.connect(self.on_back_clicked)
        header_layout.addWidget(self.back_btn)

        self.version_label = QLabel("Version")
        self.version_label.setStyleSheet("font-size: 18px; font-weight: bold;")
        header_layout.addWidget(self.version_label)

        header_layout.addStretch()

        # Locked indicator (shown for already-saved versions)
        theme = get_current_theme()
        self.locked_label = QLabel("🔒 Locked")
        self.locked_label.setStyleSheet(f"color: {theme['warning']}; font-weight: bold;")
        self.locked_label.setVisible(False)
        header_layout.addWidget(self.locked_label)

        # Create Version button (only shown for new versions)
        self.create_btn = QPushButton("✓ Create Version")
        self.create_btn.setObjectName("successButton")
        self.create_btn.setToolTip("Save this version to the repository. Once created, it cannot be edited.")
        self.create_btn.clicked.connect(self.on_create_clicked)
        self.create_btn.setVisible(False)
        header_layout.addWidget(self.create_btn)

        main_layout.addLayout(header_layout)

        # Tab widget
        self.tabs = QTabWidget()
        self.tabs.currentChanged.connect(self.on_tab_changed)

        # Mods Tab
        self.mods_tab = QWidget()
        self.setup_mods_tab()
        self.tabs.addTab(self.mods_tab, "Mods")

        # Files Tab
        self.files_tab = QWidget()
        self.setup_files_tab()
        self.tabs.addTab(self.files_tab, "Files")

        # Delete Tab
        self.delete_tab = QWidget()
        self.setup_delete_tab()
        self.tabs.addTab(self.delete_tab, "Delete")

        # Settings Tab
        self.settings_tab = QWidget()
        self.setup_settings_tab()
        self.tabs.addTab(self.settings_tab, "Settings")

        main_layout.addWidget(self.tabs)
        
        # Refresh panel styles to ensure they match the current theme
        # This is important because panels are created before theme is fully applied
        QTimer.singleShot(0, self.refresh_editor_panels_style)

    def setup_mods_tab(self):
        layout = QHBoxLayout(self.mods_tab)
        layout.setContentsMargins(0, 0, 0, 0)

        # Left: Grid of mods
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(16, 16, 8, 16)

        self.mods_scroll = QScrollArea()
        self.mods_scroll.setWidgetResizable(True)
        self.mods_scroll.setFrameShape(QFrame.Shape.NoFrame)

        self.mods_grid_widget = QWidget()
        self.mods_grid = QGridLayout(self.mods_grid_widget)
        self.mods_grid.setSpacing(8)  # Reduced spacing
        self.mods_scroll.setWidget(self.mods_grid_widget)

        left_layout.addWidget(self.mods_scroll)

        # Right: Stacked widget for editor panel and placeholder
        # Wrap in a container with distinct styling
        self.mod_right_container = QFrame()
        theme = get_current_theme()
        self.mod_right_container.setStyleSheet(f"""
            QFrame {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 8px;
                margin: 4px;
            }}
            QScrollBar:vertical {{
                background-color: {theme['bg_secondary']};
                width: 12px;
                border-radius: 6px;
            }}
            QScrollBar::handle:vertical {{
                background-color: {theme['border']};
                border-radius: 4px;
                min-height: 30px;
            }}
            QScrollBar::handle:vertical:hover {{
                background-color: {theme['accent']};
            }}
        """)
        mod_right_container_layout = QVBoxLayout(self.mod_right_container)
        mod_right_container_layout.setContentsMargins(8, 8, 8, 8)

        self.mod_right_stack = QStackedWidget()

        # Placeholder for when no mod is selected
        self.mod_placeholder = QWidget()
        placeholder_layout = QVBoxLayout(self.mod_placeholder)
        placeholder_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        placeholder_layout.setContentsMargins(20, 20, 20, 20)
        placeholder_label = QLabel("No option selected")
        placeholder_label.setStyleSheet(f"color: {theme['text_secondary']}; font-size: 16px; font-style: italic; background-color: transparent; padding: 16px;")
        placeholder_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        placeholder_layout.addWidget(placeholder_label)
        self.mod_right_stack.addWidget(self.mod_placeholder)

        # Editor panel
        self.mod_editor = ModEditorPanel()
        self.mod_editor.mod_changed.connect(self.on_mod_changed)
        self.mod_editor.mod_saved.connect(self.on_mod_saved)
        self.mod_editor.mod_deleted.connect(self.on_mod_deleted)
        self.mod_right_stack.addWidget(self.mod_editor)

        # Show placeholder by default
        self.mod_right_stack.setCurrentWidget(self.mod_placeholder)

        mod_right_container_layout.addWidget(self.mod_right_stack)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(self.mod_right_container)
        splitter.setSizes([400, 400])

        layout.addWidget(splitter)

    def setup_files_tab(self):
        layout = QHBoxLayout(self.files_tab)
        layout.setContentsMargins(0, 0, 0, 0)

        # Left: Grid of files
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(16, 16, 8, 16)

        self.files_scroll = QScrollArea()
        self.files_scroll.setWidgetResizable(True)
        self.files_scroll.setFrameShape(QFrame.Shape.NoFrame)

        self.files_grid_widget = QWidget()
        self.files_grid = QGridLayout(self.files_grid_widget)
        self.files_grid.setSpacing(8)  # Reduced spacing
        self.files_scroll.setWidget(self.files_grid_widget)

        left_layout.addWidget(self.files_scroll)

        # Right: Stacked widget for editor panel and placeholder
        # Wrap in a container with distinct styling
        self.file_right_container = QFrame()
        theme = get_current_theme()
        self.file_right_container.setStyleSheet(f"""
            QFrame {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 8px;
                margin: 4px;
            }}
            QScrollBar:vertical {{
                background-color: {theme['bg_secondary']};
                width: 12px;
                border-radius: 6px;
            }}
            QScrollBar::handle:vertical {{
                background-color: {theme['border']};
                border-radius: 4px;
                min-height: 30px;
            }}
            QScrollBar::handle:vertical:hover {{
                background-color: {theme['accent']};
            }}
        """)
        file_right_container_layout = QVBoxLayout(self.file_right_container)
        file_right_container_layout.setContentsMargins(8, 8, 8, 8)

        self.file_right_stack = QStackedWidget()

        # Placeholder for when no file is selected
        self.file_placeholder = QWidget()
        placeholder_layout = QVBoxLayout(self.file_placeholder)
        placeholder_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        placeholder_layout.setContentsMargins(20, 20, 20, 20)
        placeholder_label = QLabel("No option selected")
        placeholder_label.setStyleSheet(f"color: {theme['text_secondary']}; font-size: 16px; font-style: italic; background-color: transparent; padding: 16px;")
        placeholder_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        placeholder_layout.addWidget(placeholder_label)
        self.file_right_stack.addWidget(self.file_placeholder)

        # Editor panel
        self.file_editor = FileEditorPanel()
        self.file_editor.file_changed.connect(self.on_file_changed)
        self.file_editor.file_saved.connect(self.on_file_saved)
        self.file_editor.file_deleted.connect(self.on_file_deleted)
        self.file_right_stack.addWidget(self.file_editor)

        # Show placeholder by default
        self.file_right_stack.setCurrentWidget(self.file_placeholder)

        file_right_container_layout.addWidget(self.file_right_stack)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(self.file_right_container)
        splitter.setSizes([400, 400])

        layout.addWidget(splitter)

    def setup_delete_tab(self):
        layout = QHBoxLayout(self.delete_tab)
        layout.setContentsMargins(0, 0, 0, 0)

        # Left: List of deletes
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(16, 16, 8, 16)

        self.deletes_list = QListWidget()
        self.deletes_list.itemClicked.connect(self.on_delete_selected)
        left_layout.addWidget(self.deletes_list)

        add_delete_btn = QPushButton("+ Add Delete Entry")
        add_delete_btn.setObjectName("primaryButton")
        add_delete_btn.clicked.connect(self.add_delete)
        left_layout.addWidget(add_delete_btn)

        # Right: Stacked widget for editor panel and placeholder
        # Wrap in a container with distinct styling
        self.delete_right_container = QFrame()
        theme = get_current_theme()
        self.delete_right_container.setStyleSheet(f"""
            QFrame {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 8px;
                margin: 4px;
            }}
            QScrollBar:vertical {{
                background-color: {theme['bg_secondary']};
                width: 12px;
                border-radius: 6px;
            }}
            QScrollBar::handle:vertical {{
                background-color: {theme['border']};
                border-radius: 4px;
                min-height: 30px;
            }}
            QScrollBar::handle:vertical:hover {{
                background-color: {theme['accent']};
            }}
        """)
        delete_right_container_layout = QVBoxLayout(self.delete_right_container)
        delete_right_container_layout.setContentsMargins(8, 8, 8, 8)

        self.delete_right_stack = QStackedWidget()

        # Placeholder for when no delete entry is selected
        self.delete_placeholder = QWidget()
        placeholder_layout = QVBoxLayout(self.delete_placeholder)
        placeholder_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        placeholder_layout.setContentsMargins(20, 20, 20, 20)
        placeholder_label = QLabel("No option selected")
        placeholder_label.setStyleSheet(f"color: {theme['text_secondary']}; font-size: 16px; font-style: italic; background-color: transparent; padding: 16px;")
        placeholder_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        placeholder_layout.addWidget(placeholder_label)
        self.delete_right_stack.addWidget(self.delete_placeholder)

        # Editor panel
        self.delete_editor = DeleteEditorPanel()
        self.delete_editor.delete_changed.connect(self.on_delete_changed)
        self.delete_editor.delete_saved.connect(self.on_delete_entry_saved)
        self.delete_editor.delete_entry_deleted.connect(self.on_delete_entry_deleted)
        self.delete_right_stack.addWidget(self.delete_editor)

        # Show placeholder by default
        self.delete_right_stack.setCurrentWidget(self.delete_placeholder)

        delete_right_container_layout.addWidget(self.delete_right_stack)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(self.delete_right_container)
        splitter.setSizes([300, 400])

        layout.addWidget(splitter)

    def setup_settings_tab(self):
        layout = QVBoxLayout(self.settings_tab)
        layout.setContentsMargins(24, 24, 24, 24)

        header = QLabel("Version Settings")
        header.setStyleSheet("font-size: 18px; font-weight: bold;")
        layout.addWidget(header)

        theme = get_current_theme()
        
        # Safety Mode section
        safety_group = QGroupBox("Safety")
        safety_layout = QVBoxLayout(safety_group)
        
        self.safety_mode_check = QCheckBox("Safety Mode")
        self.safety_mode_check.setChecked(True)  # Default to enabled
        self.safety_mode_check.setToolTip("When enabled, deletes will only remove files from the specified install location.\n"
                                           "This prevents accidental deletion of important files.")
        self.safety_mode_check.setStyleSheet(f"font-weight: bold; color: {theme['success']};")
        self.safety_mode_check.stateChanged.connect(self._on_safety_mode_changed)
        safety_layout.addWidget(self.safety_mode_check)
        
        safety_note = QLabel("Safety mode prevents accidental deletion of important files")
        safety_note.setStyleSheet(f"font-size: 11px; color: {theme['text_secondary']};")
        safety_layout.addWidget(safety_note)
        
        layout.addWidget(safety_group)

        # Version icon - improved layout with icon and buttons in a row
        icon_group = QGroupBox("Version Icon (Optional)")
        icon_main_layout = QVBoxLayout(icon_group)
        
        icon_row = QHBoxLayout()
        icon_row.setSpacing(16)

        self.version_icon_preview = QLabel()
        self.version_icon_preview.setFixedSize(64, 64)
        self.version_icon_preview.setStyleSheet(f"border: 2px dashed {theme['border']}; border-radius: 8px;")
        self.version_icon_preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.version_icon_preview.setText("No Icon")
        icon_row.addWidget(self.version_icon_preview)

        icon_btn_layout = QVBoxLayout()
        icon_btn_layout.setSpacing(8)
        select_icon_btn = QPushButton("Select Icon...")
        select_icon_btn.clicked.connect(self.select_version_icon)
        icon_btn_layout.addWidget(select_icon_btn)
        clear_icon_btn = QPushButton("Clear")
        clear_icon_btn.clicked.connect(self.clear_version_icon)
        icon_btn_layout.addWidget(clear_icon_btn)
        icon_row.addLayout(icon_btn_layout)
        icon_row.addStretch()
        
        icon_main_layout.addLayout(icon_row)

        layout.addWidget(icon_group)
        layout.addStretch()

    def load_version(self, version_config: VersionConfig):
        self.version_config = version_config
        self.version_label.setText(f"Version {version_config.version}")

        # Reset icon loading state
        self._icons_loaded_count = 0
        self._remaining_icons_loaded = False
        self._cancel_icon_load_threads()

        self.refresh_mods_grid()
        self.refresh_files_grid()
        self.refresh_deletes_list()

        # Clear editor panels and show placeholders
        self.mod_editor.clear()
        self.mod_right_stack.setCurrentWidget(self.mod_placeholder)
        self.file_editor.clear()
        self.file_right_stack.setCurrentWidget(self.file_placeholder)
        self.delete_editor.clear()
        self.delete_right_stack.setCurrentWidget(self.delete_placeholder)

        self.selected_mod_index = -1
        self.selected_file_index = -1
        self.selected_delete_index = -1
        
        # Set safety mode checkbox state
        if hasattr(self, 'safety_mode_check'):
            self.safety_mode_check.blockSignals(True)
            self.safety_mode_check.setChecked(version_config.safety_mode)
            self.safety_mode_check.blockSignals(False)

        # Update UI based on locked/new status
        is_locked = version_config.is_locked()
        is_new = version_config.is_new()

        self.locked_label.setVisible(is_locked)
        self.create_btn.setVisible(is_new)

        # Always enable tabs for viewing, but disable editing controls if locked
        self.tabs.setEnabled(True)
        self._set_editing_enabled(not is_locked)

        if is_locked:
            self.version_label.setText(f"Version {version_config.version} (View Only)")

        # Set Mods tab as first tab when creating a new version
        if is_new:
            self.tabs.setCurrentIndex(0)  # Mods tab

        # Load version icon
        if version_config.icon_path and os.path.exists(version_config.icon_path):
            pixmap = QPixmap(version_config.icon_path)
            if not pixmap.isNull():
                self.version_icon_preview.setPixmap(pixmap.scaled(60, 60, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))

        # Start loading the first 8 mod icons
        QTimer.singleShot(50, self._load_initial_icons)

    def _set_editing_enabled(self, enabled: bool):
        """Enable or disable editing controls (for locked versions).

        Note: When locked, users can still view all data but cannot modify it.
        They can also still delete items (which marks them for removal in next version).
        """
        # Mod editor controls - disable modification but allow viewing
        self.mod_editor.save_btn.setEnabled(enabled)
        # Delete is always enabled (users should be able to delete locked files/old versions)
        # self.mod_editor.delete_btn.setEnabled(enabled)  # Keep enabled for deleting
        self.mod_editor.id_edit.setReadOnly(not enabled)
        self.mod_editor.hash_edit.setReadOnly(not enabled)
        self.mod_editor.mod_id_edit.setReadOnly(not enabled)
        self.mod_editor.file_id_edit.setReadOnly(not enabled)
        self.mod_editor.url_edit.setReadOnly(not enabled)
        self.mod_editor.install_location_edit.setReadOnly(not enabled)
        self.mod_editor.file_name_edit.setReadOnly(not enabled)
        self.mod_editor.display_name_edit.setReadOnly(not enabled)
        self.mod_editor.info_name_edit.setReadOnly(not enabled)
        self.mod_editor.auto_hash_btn.setEnabled(enabled)
        self.mod_editor.curseforge_btn.setEnabled(enabled)
        self.mod_editor.modrinth_btn.setEnabled(enabled)
        self.mod_editor.url_btn.setEnabled(enabled)

        # File editor controls
        self.file_editor.save_btn.setEnabled(enabled)
        # self.file_editor.delete_btn.setEnabled(enabled)  # Keep enabled for deleting
        self.file_editor.info_name_edit.setReadOnly(not enabled)
        self.file_editor.display_name_edit.setReadOnly(not enabled)
        self.file_editor.file_name_edit.setReadOnly(not enabled)
        self.file_editor.url_edit.setReadOnly(not enabled)
        self.file_editor.download_path_edit.setReadOnly(not enabled)
        self.file_editor.hash_edit.setReadOnly(not enabled)
        self.file_editor.overwrite_check.setEnabled(enabled)
        self.file_editor.extract_check.setEnabled(enabled)
        self.file_editor.auto_hash_btn.setEnabled(enabled)

        # Delete editor controls
        self.delete_editor.save_btn.setEnabled(enabled)
        # self.delete_editor.delete_btn.setEnabled(enabled)  # Keep enabled for deleting
        self.delete_editor.path_edit.setReadOnly(not enabled)
        self.delete_editor.type_combo.setEnabled(enabled)
        self.delete_editor.reason_edit.setReadOnly(not enabled)

    def on_back_clicked(self):
        """Handle back button click."""
        self.back_requested.emit()

    def on_tab_changed(self, index: int):
        """Handle tab change - auto-select first item in mods, files, or deletes tabs."""
        if not self.version_config:
            return

        # Tab indices: 0=Mods, 1=Files, 2=Delete, 3=Settings
        if index == 0:  # Mods tab
            if self.version_config.mods and self.selected_mod_index < 0:
                # Auto-select first mod
                self.select_mod(0)
        elif index == 1:  # Files tab
            if self.version_config.files and self.selected_file_index < 0:
                # Auto-select first file
                self.select_file(0)
        elif index == 2:  # Delete tab
            if self.version_config.deletes and self.selected_delete_index < 0:
                # Auto-select first delete entry
                self.deletes_list.setCurrentRow(0)
                first_item = self.deletes_list.item(0)
                if first_item:
                    self.on_delete_selected(first_item)

    def on_create_clicked(self):
        """Handle Create button click - save version to repo."""
        if not self.version_config:
            return

        # Show confirmation dialog
        reply = QMessageBox.question(
            self, "Create Version",
            f"Are you sure you want to create version {self.version_config.version}?\n\n"
            "⚠️ Warning: Once created, this version cannot be edited.\n"
            "Make sure all mods and files are correctly configured.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No
        )

        if reply == QMessageBox.StandardButton.Yes:
            self.create_requested.emit(self.version_config)

    def refresh_mods_grid(self):
        # Clear grid
        while self.mods_grid.count():
            item = self.mods_grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        if not self.version_config:
            return

        row, col = 0, 0
        max_cols = 4

        # Add mod cards
        for i, mod in enumerate(self.version_config.mods):
            # Support both icon_path and cached icon_data
            icon_data = getattr(mod, '_icon_data', None)
            # Use GUI display name if set, otherwise fall back to display_name or id
            gui_display = getattr(mod, '_gui_display_name', '') or mod.display_name or mod.id
            card = ItemCard(gui_display, mod.icon_path, icon_data=icon_data)
            card.clicked.connect(lambda idx=i: self.select_mod(idx))
            card.double_clicked.connect(lambda idx=i: self.select_mod(idx))
            self.mods_grid.addWidget(card, row, col)

            col += 1
            if col >= max_cols:
                col = 0
                row += 1

        # Add "Add" button only if version is not locked
        if self.version_config and not self.version_config.is_locked():
            add_card = ItemCard("", "", is_add_button=True)
            add_card.clicked.connect(self.add_mod)
            self.mods_grid.addWidget(add_card, row, col)

    def refresh_files_grid(self):
        # Clear grid
        while self.files_grid.count():
            item = self.files_grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        if not self.version_config:
            return

        row, col = 0, 0
        max_cols = 4

        # Add file cards
        for i, file in enumerate(self.version_config.files):
            # Use GUI display name if set, otherwise fall back to display_name or file_name
            gui_display = getattr(file, '_gui_display_name', '') or file.display_name or file.file_name
            card = ItemCard(gui_display, file.icon_path)
            card.clicked.connect(lambda idx=i: self.select_file(idx))
            self.files_grid.addWidget(card, row, col)

            col += 1
            if col >= max_cols:
                col = 0
                row += 1

        # Add "Add" button only if version is not locked
        if self.version_config and not self.version_config.is_locked():
            add_card = ItemCard("", "", is_add_button=True)
            add_card.clicked.connect(self.add_file)
            self.files_grid.addWidget(add_card, row, col)

    def refresh_deletes_list(self):
        self.deletes_list.clear()
        if not self.version_config:
            return

        for delete in self.version_config.deletes:
            self.deletes_list.addItem(f"{delete.path} ({delete.type})")

    def select_mod(self, index: int):
        if not self.version_config or index < 0 or index >= len(self.version_config.mods):
            return
        self.selected_mod_index = index
        self.mod_editor.load_mod(self.version_config.mods[index])
        self.mod_right_stack.setCurrentWidget(self.mod_editor)  # Show editor panel

        # Update selection visuals
        for i in range(self.mods_grid.count()):
            widget = self.mods_grid.itemAt(i).widget()
            if isinstance(widget, ItemCard) and not widget.is_add_button:
                widget.set_selected(i == index)

    def select_file(self, index: int):
        if not self.version_config or index < 0 or index >= len(self.version_config.files):
            return
        self.selected_file_index = index
        self.file_editor.load_file(self.version_config.files[index])
        self.file_right_stack.setCurrentWidget(self.file_editor)  # Show editor panel

        # Update selection visuals
        for i in range(self.files_grid.count()):
            widget = self.files_grid.itemAt(i).widget()
            if isinstance(widget, ItemCard) and not widget.is_add_button:
                widget.set_selected(i == index)

    def on_delete_selected(self, item):
        index = self.deletes_list.row(item)
        if not self.version_config or index < 0 or index >= len(self.version_config.deletes):
            return
        self.selected_delete_index = index
        self.delete_editor.load_delete(self.version_config.deletes[index])
        self.delete_right_stack.setCurrentWidget(self.delete_editor)  # Show editor panel

    def add_mod(self):
        if not self.version_config:
            return

        # Show a choice dialog: Browse CurseForge/Modrinth first, then Manual Add
        menu = QMenu(self)
        browse_action = menu.addAction("🔍 Browse CurseForge/Modrinth")
        manual_action = menu.addAction("✏️ Add Manually")

        # Get the "Add" button position
        add_card = None
        for i in range(self.mods_grid.count()):
            widget = self.mods_grid.itemAt(i).widget()
            if isinstance(widget, ItemCard) and widget.is_add_button:
                add_card = widget
                break

        if add_card:
            pos = add_card.mapToGlobal(add_card.rect().bottomLeft())
        else:
            pos = self.mods_grid_widget.mapToGlobal(self.mods_grid_widget.rect().center())

        action = menu.exec(pos)

        if action == browse_action:
            self._add_mod_browse()
        elif action == manual_action:
            self._add_mod_manual()

    def _add_mod_manual(self):
        """Add a mod manually."""
        existing_ids = [m.id for m in self.version_config.mods]
        dialog = AddModDialog(existing_ids, self)
        if dialog.exec():
            mod = dialog.get_mod()
            mod.since = self.version_config.version  # Set since to current version
            mod._is_pending = True  # Mark as pending until saved
            # Show in editor but don't add to list yet
            self._pending_mod = mod
            self.mod_editor.load_mod(mod)
            self.mod_right_stack.setCurrentWidget(self.mod_editor)
            # Connect save to add the pending mod

    def _add_mod_browse(self):
        """Add a mod by browsing CurseForge/Modrinth."""
        # Load remaining icons now that the user is opening the mod browser
        self._load_remaining_icons()

        existing_ids = [m.id for m in self.version_config.mods]
        dialog = ModBrowserDialog(existing_ids, self.version_config.version, self)
        if dialog.exec():
            mod = dialog.get_mod()
            if mod:
                # Check for duplicate
                if mod.id in existing_ids:
                    QMessageBox.warning(self, "Duplicate", f"A mod with ID '{mod.id}' already exists.")
                    return
                mod._is_pending = True  # Mark as pending until saved
                # Show in editor but don't add to list yet
                self._pending_mod = mod
                self.mod_editor.load_mod(mod)
                self.mod_right_stack.setCurrentWidget(self.mod_editor)

    def add_file(self):
        if not self.version_config:
            return

        file_entry = FileEntry()
        file_entry._is_pending = True  # Mark as pending until saved
        self._pending_file = file_entry
        self.file_editor.load_file(file_entry)
        self.file_right_stack.setCurrentWidget(self.file_editor)

    def add_delete(self):
        if not self.version_config:
            return

        delete_entry = DeleteEntry()
        delete_entry._is_pending = True  # Mark as pending until saved
        self._pending_delete = delete_entry
        self.delete_editor.load_delete(delete_entry)
        self.delete_right_stack.setCurrentWidget(self.delete_editor)

    def on_mod_changed(self):
        self.version_config.modified = True
        self.refresh_mods_grid()
        self.version_modified.emit()

    def on_file_changed(self):
        self.version_config.modified = True
        self.refresh_files_grid()
        self.version_modified.emit()

    def on_delete_changed(self):
        self.version_config.modified = True
        self.refresh_deletes_list()
        self.version_modified.emit()

    def on_mod_saved(self):
        """Handle when mod save button is clicked - add pending mod and show placeholder."""
        # Check if we have a pending mod to add
        if hasattr(self, '_pending_mod') and self._pending_mod is not None:
            mod = self._pending_mod

            # Check for duplicate ID
            mod_id = mod.id
            existing_mod = None
            for m in self.version_config.mods:
                if m.id == mod_id:
                    existing_mod = m
                    break

            if existing_mod:
                # Compare hashes
                if mod.hash and existing_mod.hash and mod.hash == existing_mod.hash:
                    # Same hash - this file has already been added
                    QMessageBox.warning(self, "Duplicate Mod",
                        f"This file has already been added.\n\n"
                        f"A mod with ID '{mod_id}' and the same hash already exists.")
                    return  # Don't add the mod
                else:
                    # Different hash or hash not available - warn about possible duplicate
                    reply = QMessageBox.warning(self, "Possible Duplicate",
                        f"There is already a mod with ID '{mod_id}'.\n\n"
                        f"It might be a duplicate mod. Double check before adding.\n"
                        f"If you are sure it is not a duplicate, please change the ID and try again.",
                        QMessageBox.StandardButton.Ok)
                    return  # Don't add the mod - user must change ID

            mod._is_pending = False
            mod.since = self.version_config.version
            self.version_config.mods.append(mod)
            self.version_config.modified = True
            self._pending_mod = None
            self.refresh_mods_grid()
            self.version_modified.emit()

        self.mod_right_stack.setCurrentWidget(self.mod_placeholder)
        self.selected_mod_index = -1

    def on_mod_deleted(self, mod):
        """Handle when mod delete is confirmed."""
        # Check if this is a pending mod being cancelled
        if hasattr(self, '_pending_mod') and self._pending_mod == mod:
            self._pending_mod = None
            self.mod_editor.clear()
            self.mod_right_stack.setCurrentWidget(self.mod_placeholder)
            self.selected_mod_index = -1
            return

        if not self.version_config or mod not in self.version_config.mods:
            return

        # If this is a mod from a previous version, add to deletes
        if mod._is_from_previous and mod.install_location:
            delete_entry = DeleteEntry({
                'path': f"{mod.install_location}/{mod.file_name or mod.id + '.jar'}",
                'type': 'file',
                'reason': f"Removed mod: {mod.display_name or mod.id}",
                '_is_unremovable': True
            })
            self.version_config.deletes.append(delete_entry)

        self.version_config.mods.remove(mod)
        self.version_config.modified = True
        self.mod_editor.clear()
        self.mod_right_stack.setCurrentWidget(self.mod_placeholder)
        self.selected_mod_index = -1
        self.refresh_mods_grid()
        self.refresh_deletes_list()
        self.version_modified.emit()

    def on_file_saved(self):
        """Handle when file save button is clicked - add pending file and show placeholder."""
        # Check if we have a pending file to add
        if hasattr(self, '_pending_file') and self._pending_file is not None:
            file_entry = self._pending_file
            file_entry._is_pending = False
            file_entry.since = self.version_config.version
            self.version_config.files.append(file_entry)
            self.version_config.modified = True
            self._pending_file = None
            self.refresh_files_grid()
            self.version_modified.emit()

        self.file_right_stack.setCurrentWidget(self.file_placeholder)
        self.selected_file_index = -1

    def on_file_deleted(self, file_entry):
        """Handle when file delete is confirmed."""
        # Check if this is a pending file being cancelled
        if hasattr(self, '_pending_file') and self._pending_file == file_entry:
            self._pending_file = None
            self.file_editor.clear()
            self.file_right_stack.setCurrentWidget(self.file_placeholder)
            self.selected_file_index = -1
            return

        if not self.version_config or file_entry not in self.version_config.files:
            return

        # If this is a file from a previous version, add to deletes
        if file_entry._is_from_previous and file_entry.download_path:
            delete_entry = DeleteEntry({
                'path': f"{file_entry.download_path}{file_entry.file_name or file_entry.display_name}",
                'type': 'file',
                'reason': f"Removed file: {file_entry.display_name or file_entry.file_name}",
                '_is_unremovable': True
            })
            self.version_config.deletes.append(delete_entry)

        self.version_config.files.remove(file_entry)
        self.version_config.modified = True
        self.file_editor.clear()
        self.file_right_stack.setCurrentWidget(self.file_placeholder)
        self.selected_file_index = -1
        self.refresh_files_grid()
        self.refresh_deletes_list()
        self.version_modified.emit()

    def on_delete_entry_saved(self):
        """Handle when delete save button is clicked - add pending delete and show placeholder."""
        # Check if we have a pending delete to add
        if hasattr(self, '_pending_delete') and self._pending_delete is not None:
            delete_entry = self._pending_delete
            delete_entry._is_pending = False
            delete_entry.version = self.version_config.version
            self.version_config.deletes.append(delete_entry)
            self.version_config.modified = True
            self._pending_delete = None
            self.refresh_deletes_list()
            self.version_modified.emit()

        self.delete_right_stack.setCurrentWidget(self.delete_placeholder)
        self.selected_delete_index = -1

    def on_delete_entry_deleted(self, delete_entry):
        """Handle when delete entry delete is confirmed."""
        # Check if this is a pending delete being cancelled
        if hasattr(self, '_pending_delete') and self._pending_delete == delete_entry:
            self._pending_delete = None
            self.delete_editor.clear()
            self.delete_right_stack.setCurrentWidget(self.delete_placeholder)
            self.selected_delete_index = -1
            return

        if not self.version_config or delete_entry not in self.version_config.deletes:
            return

        self.version_config.deletes.remove(delete_entry)
        self.version_config.modified = True
        self.delete_editor.clear()
        self.delete_right_stack.setCurrentWidget(self.delete_placeholder)
        self.selected_delete_index = -1
        self.refresh_deletes_list()
        self.version_modified.emit()

    def _on_safety_mode_changed(self, state):
        """Handle safety mode checkbox state change."""
        if self.version_config:
            self.version_config.safety_mode = (state == Qt.CheckState.Checked.value)
            self.version_config.modified = True
            self.version_modified.emit()

    def select_version_icon(self):
        if not self.version_config:
            return
        file_path, _ = QFileDialog.getOpenFileName(
            self, "Select Icon", "", "Images (*.png *.jpg *.jpeg *.gif *.ico)"
        )
        if file_path:
            self.version_config.icon_path = file_path
            pixmap = QPixmap(file_path)
            if not pixmap.isNull():
                self.version_icon_preview.setPixmap(pixmap.scaled(60, 60, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
                # Update the style to show border around icon
                theme = get_current_theme()
                self.version_icon_preview.setStyleSheet(f"border: 2px solid {theme['accent']}; border-radius: 8px;")
            self.version_modified.emit()

    def clear_version_icon(self):
        if not self.version_config:
            return
        self.version_config.icon_path = ""
        self.version_icon_preview.clear()
        self.version_icon_preview.setText("No Icon")
        # Reset to dashed border style
        theme = get_current_theme()
        self.version_icon_preview.setStyleSheet(f"border: 2px dashed {theme['border']}; border-radius: 8px;")
        self.version_modified.emit()

    def refresh_editor_panels_style(self):
        """Refresh the styling of editor panel containers when theme changes."""
        theme = get_current_theme()
        container_style = f"""
            QFrame {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 8px;
                margin: 4px;
            }}
            QScrollBar:vertical {{
                background-color: {theme['bg_secondary']};
                width: 12px;
                border-radius: 6px;
            }}
            QScrollBar::handle:vertical {{
                background-color: {theme['border']};
                border-radius: 4px;
                min-height: 30px;
            }}
            QScrollBar::handle:vertical:hover {{
                background-color: {theme['accent']};
            }}
        """
        placeholder_style = f"color: {theme['text_secondary']}; font-size: 16px; font-style: italic; background-color: transparent; padding: 16px;"
        
        if hasattr(self, 'mod_right_container'):
            self.mod_right_container.setStyleSheet(container_style)
        if hasattr(self, 'file_right_container'):
            self.file_right_container.setStyleSheet(container_style)
        if hasattr(self, 'delete_right_container'):
            self.delete_right_container.setStyleSheet(container_style)
        
        # Update placeholder label styles
        if hasattr(self, 'mod_placeholder'):
            for label in self.mod_placeholder.findChildren(QLabel):
                label.setStyleSheet(placeholder_style)
        if hasattr(self, 'file_placeholder'):
            for label in self.file_placeholder.findChildren(QLabel):
                label.setStyleSheet(placeholder_style)
        if hasattr(self, 'delete_placeholder'):
            for label in self.delete_placeholder.findChildren(QLabel):
                label.setStyleSheet(placeholder_style)

    # === Lazy Icon Loading Methods ===

    def _cancel_icon_load_threads(self):
        """Cancel all running icon load threads and wait for them to finish."""
        for thread in self._icon_load_threads:
            try:
                if thread.isRunning():
                    thread.stop()
                    thread.wait(1000)
            except RuntimeError:
                # Handle case where Qt C++ object was deleted
                pass
        self._icon_load_threads.clear()

    def _load_initial_icons(self):
        """Load the first INITIAL_ICON_LOAD_COUNT icons when a version is opened."""
        if not self.version_config:
            return

        # Get mods that need icon loading
        mods_to_load = []
        for i, mod in enumerate(self.version_config.mods[:self.INITIAL_ICON_LOAD_COUNT]):
            if not getattr(mod, '_icon_data', None):
                # Check if mod has a source that can provide an icon
                source = mod.source
                if source and source.get('type') in ('curseforge', 'modrinth'):
                    mods_to_load.append((i, mod))

        # Start loading icons for these mods
        for idx, mod in mods_to_load:
            self._start_mod_icon_load(idx, mod)

        self._icons_loaded_count = min(self.INITIAL_ICON_LOAD_COUNT, len(self.version_config.mods))

    def _load_remaining_icons(self):
        """Load icons for mods beyond the initial INITIAL_ICON_LOAD_COUNT.

        This is called when the ModBrowserDialog is about to open.
        """
        if not self.version_config or self._remaining_icons_loaded:
            return

        self._remaining_icons_loaded = True

        # Get remaining mods that need icon loading
        for i, mod in enumerate(self.version_config.mods[self.INITIAL_ICON_LOAD_COUNT:], start=self.INITIAL_ICON_LOAD_COUNT):
            if not getattr(mod, '_icon_data', None):
                # Check if mod has a source that can provide an icon
                source = mod.source
                if source and source.get('type') in ('curseforge', 'modrinth'):
                    self._start_mod_icon_load(i, mod)

    def _start_mod_icon_load(self, mod_index: int, mod: ModEntry):
        """Start loading an icon for a specific mod."""
        source = mod.source
        source_type = source.get('type', '')

        if source_type == 'modrinth':
            project_slug = source.get('projectSlug', '')
            if project_slug:
                thread = IconFetcher({'source': source, 'id': mod.id})
                thread.icon_fetched.connect(lambda mod_id, data, idx=mod_index: self._on_mod_icon_loaded(idx, data))
                thread.finished.connect(thread.deleteLater)
                self._icon_load_threads.append(thread)
                thread.start()
        elif source_type == 'curseforge':
            # CurseForge icons require fetching project info first
            project_id = source.get('projectId', '')
            if project_id:
                thread = _CurseForgeIconFetcher(str(project_id), mod.id, mod_index)
                thread.icon_fetched.connect(self._on_mod_icon_loaded)
                thread.finished.connect(thread.deleteLater)
                self._icon_load_threads.append(thread)
                thread.start()

    def _on_mod_icon_loaded(self, mod_index: int, icon_data: bytes):
        """Handle when a mod icon has been loaded."""
        if not self.version_config or mod_index >= len(self.version_config.mods):
            return

        mod = self.version_config.mods[mod_index]
        mod._icon_data = icon_data

        # Update the card in the grid if it exists
        if mod_index < self.mods_grid.count():
            widget = self.mods_grid.itemAt(mod_index)
            if widget:
                card = widget.widget()
                if isinstance(card, ItemCard):
                    card.set_icon_from_bytes(icon_data)


class _CurseForgeIconFetcher(QThread):
    """Background thread for fetching CurseForge mod icons."""
    icon_fetched = pyqtSignal(int, bytes)  # mod_index, icon_bytes

    def __init__(self, project_id: str, mod_id: str, mod_index: int):
        super().__init__()
        self.project_id = project_id
        self.mod_id = mod_id
        self.mod_index = mod_index
        self._running = True

    def run(self):
        """Fetch icon from CurseForge API."""
        if not self._running:
            return
        try:
            # Use curse.tools proxy API
            url = f"{CF_PROXY_BASE_URL}/mods/{self.project_id}"
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read())
                mod_data = data.get('data', {})
                logo = mod_data.get('logo', {})
                icon_url = logo.get('thumbnailUrl', '') or logo.get('url', '')

                if icon_url and self._running:
                    with urllib.request.urlopen(icon_url, timeout=10) as img_response:
                        icon_data = img_response.read()
                        if icon_data and self._running:
                            self.icon_fetched.emit(self.mod_index, icon_data)
        except Exception:
            pass  # Silently fail icon loads

    def stop(self):
        self._running = False



# === Version Selection Page ===
class VersionSelectionPage(QWidget):
    version_selected = pyqtSignal(str)
    version_deleted = pyqtSignal(str)
    def refresh_grid(self):
        # Clear grid
        while self.grid.count():
            item = self.grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.versions: Dict[str, VersionConfig] = {}
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)

        header = QLabel("Select Version")
        header.setObjectName("headerLabel")
        layout.addWidget(header)

        desc = QLabel("Choose a version to edit or create a new one. Click × to delete a version.")
        layout.addWidget(desc)

        # Latest version indicator
        self.latest_version_label = QLabel("")
        theme = get_current_theme()
        self.latest_version_label.setStyleSheet(f"color: {theme['accent']}; font-weight: bold; font-size: 14px; padding: 8px 0;")
        layout.addWidget(self.latest_version_label)

        layout.addSpacing(10)

        # Grid of versions
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)

        self.grid_widget = QWidget()
        self.grid = QGridLayout(self.grid_widget)
        self.grid.setSpacing(8)  # Reduced spacing between version cards
        self.scroll.setWidget(self.grid_widget)

        layout.addWidget(self.scroll)

    def set_versions(self, versions: Dict[str, VersionConfig]):
        self.versions = versions
        self.refresh_grid()
    
    def refresh_grid(self):
        # Clear grid
        while self.grid.count():
            item = self.grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        
        row, col = 0, 0
        max_cols = 5
        
        def version_sort_key(v: str):
            """Sort key for semantic versions like 1.0.0, 1.0.0-beta, etc."""
            # Split into base version and pre-release tag
            parts = v.split('-', 1)
            base = parts[0]
            tag = parts[1] if len(parts) > 1 else ''
            
            # Parse base version numbers
            nums = []
            for x in base.split('.'):
                try:
                    nums.append(int(x))
                except ValueError:
                    nums.append(0)
            
            # Pre-release versions sort before release (empty tag = release)
            # Release versions have higher priority (1), pre-release have lower (0)
            tag_priority = 0 if tag else 1
            
            return (nums, tag_priority, tag)
        
        # Sort versions (newest first)
        sorted_versions = sorted(self.versions.keys(), key=version_sort_key, reverse=True)
        
        # Update latest version label
        if sorted_versions:
            latest = sorted_versions[0]
            theme = get_current_theme()
            self.latest_version_label.setText(f"📌 Latest Version: {latest}")
            self.latest_version_label.setStyleSheet(f"color: {theme['accent']}; font-weight: bold; font-size: 14px; padding: 8px 0;")
        else:
            self.latest_version_label.setText("")
        
        # Add version cards
        for i, version in enumerate(sorted_versions):
            config = self.versions[version]
            icon_path = config.icon_path if hasattr(config, 'icon_path') else ""
            is_latest = (i == 0)
            is_new = config.is_new() if hasattr(config, 'is_new') else True
            
            # Use VersionCard for versions (with delete button for non-new ones)
            card = VersionCard(version, is_latest=is_latest, is_new=is_new, icon_path=icon_path)
            card.clicked.connect(lambda v=version: self.version_selected.emit(v))
            card.delete_clicked.connect(self.on_delete_version)
            self.grid.addWidget(card, row, col)
            
            col += 1
            if col >= max_cols:
                col = 0
                row += 1
        
        # Add "Add" button
        add_card = VersionCard("", is_add_button=True)
        add_card.clicked.connect(lambda v="": self.add_version())
        self.grid.addWidget(add_card, row, col)

    def on_delete_version(self, version: str):
        """Handle version delete request."""
        # Show confirmation dialog with note about saving
        dialog = ConfirmDeleteDialog(version, "version", self)
        if dialog.exec():
            # Check if version is saved to repo before showing info
            version_is_saved = False
            if version in self.versions:
                config = self.versions[version]
                version_is_saved = not config.is_new() if hasattr(config, 'is_new') else False
            
            # Remove version from local storage
            if version in self.versions:
                del self.versions[version]
                self.refresh_grid()
                self.version_deleted.emit(version)
                
                # Only show the save reminder if the version was saved to the repo
                if version_is_saved:
                    # Process events to ensure grid is fully updated before showing message
                    QApplication.processEvents()
                    QMessageBox.information(
                        self, "Version Deleted",
                        f"Version '{version}' has been deleted locally.\n\n"
                        "Click 'Save All' in the sidebar to permanently remove it from the repository."
                    )
    
    def add_version(self):
        existing = list(self.versions.keys())
        dialog = AddVersionDialog(existing, self)
        if dialog.exec():
            version = dialog.get_version()
            new_config = VersionConfig(version)
            
            # Copy mods and files from the most recent version (if any)
            if self.versions:
                # Find the most recent version
                def version_sort_key(v: str):
                    """Sort key for semantic versions."""
                    parts = v.split('.') 
                    nums = []
                    for x in parts:
                        try:
                            nums.append(int(x))
                        except ValueError:
                            nums.append(0)
                    return nums
                
                sorted_versions = sorted(self.versions.keys(), key=version_sort_key, reverse=True)
                if sorted_versions:
                    latest_version = sorted_versions[0]
                    latest_config = self.versions[latest_version]
                    
                    # Copy mods, marking them as from previous version
                    for mod in latest_config.mods:
                        new_mod_data = mod.to_dict()
                        new_mod_data['_is_from_previous'] = True
                        new_config.mods.append(ModEntry(new_mod_data))
                    
                    # Copy files, marking them as from previous version
                    for file in latest_config.files:
                        new_file_data = file.to_dict()
                        new_file_data['_is_from_previous'] = True
                        new_config.files.append(FileEntry(new_file_data))
                    
                    # Clear deletes for new version
                    new_config.deletes = []
            
            new_config._is_new = True  # Mark as new version
            self.versions[version] = new_config
            self.refresh_grid()
            self.version_selected.emit(version)


# === Configuration Page ===
class ConfigurationPage(QWidget):
    """Page for editing the main config.json file."""
    config_changed = pyqtSignal()
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.modpack_config: Optional[ModpackConfig] = None
        self._repo_url = ""
        self._config_path = ""
        self._branch = "main"
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        
        header = QLabel("Modpack Configuration")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        desc = QLabel("Edit the main config.json that controls how ModUpdater fetches updates.\n"
                      "This file is stored in the repository and downloaded by clients.")
        desc.setWordWrap(True)
        layout.addWidget(desc)
        
        layout.addSpacing(16)
        
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(16)
        
        # Hidden fields for internal use (not visible to user)
        self.modpack_version_edit = QLineEdit()
        self.modpack_version_edit.setVisible(False)
        
        self.configs_base_url_edit = QLineEdit()
        self.configs_base_url_edit.setVisible(False)
        
        self.mods_json_edit = QLineEdit()
        self.mods_json_edit.setText("mods.json")
        self.mods_json_edit.setVisible(False)
        
        self.files_json_edit = QLineEdit()
        self.files_json_edit.setText("files.json")
        self.files_json_edit.setVisible(False)
        
        self.deletes_json_edit = QLineEdit()
        self.deletes_json_edit.setText("deletes.json")
        self.deletes_json_edit.setVisible(False)
        
        # Advanced Options
        advanced_group = QGroupBox("Advanced Options")
        advanced_layout = QFormLayout(advanced_group)
        
        self.check_current_version_check = QCheckBox()
        self.check_current_version_check.setChecked(True)
        self.check_current_version_check.stateChanged.connect(self.on_field_changed)
        advanced_layout.addRow("Check Current Version:", self.check_current_version_check)
        
        self.max_retries_spin = QSpinBox()
        self.max_retries_spin.setRange(1, 10)
        self.max_retries_spin.setValue(3)
        self.max_retries_spin.valueChanged.connect(self.on_field_changed)
        advanced_layout.addRow("Max Retries:", self.max_retries_spin)
        
        self.backup_keep_spin = QSpinBox()
        self.backup_keep_spin.setRange(1, 20)
        self.backup_keep_spin.setValue(5)
        self.backup_keep_spin.valueChanged.connect(self.on_field_changed)
        advanced_layout.addRow("Backups to Keep:", self.backup_keep_spin)
        
        self.debug_mode_check = QCheckBox()
        self.debug_mode_check.stateChanged.connect(self.on_field_changed)
        advanced_layout.addRow("Debug Mode:", self.debug_mode_check)
        
        scroll_layout.addWidget(advanced_group)
        scroll_layout.addStretch()
        
        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)

        # Save button at bottom
        btn_layout = QHBoxLayout()
        btn_layout.addStretch()
        
        self.save_btn = QPushButton("Save Configuration")
        self.save_btn.setObjectName("successButton")
        self.save_btn.clicked.connect(self.save_config)
        btn_layout.addWidget(self.save_btn)
        
        layout.addLayout(btn_layout)
    
    def set_repository_info(self, repo_url: str, config_path: str, branch: str):
        """Set repository information for automatic URL generation."""
        self._repo_url = repo_url
        self._config_path = config_path
        self._branch = branch
        self._update_automatic_url()
    
    def _update_automatic_url(self):
        """Update the automatic base URL from repository info."""
        if self._repo_url:
            # Parse GitHub URL to create raw URL
            # e.g., https://github.com/user/repo -> https://raw.githubusercontent.com/user/repo/main/
            pattern = r'github\.com[:/]([^/]+)/([^/\s]+?)(?:\.git)?/?$'
            match = re.search(pattern, self._repo_url)
            if match:
                owner = match.group(1)
                repo = match.group(2).replace('.git', '')
                base_url = f"https://raw.githubusercontent.com/{owner}/{repo}/{self._branch}/"
                if self._config_path:
                    base_url += f"{self._config_path}/"
                self.configs_base_url_edit.setText(base_url)
    
    def load_config(self, config: ModpackConfig):
        """Load a ModpackConfig into the form."""
        self.modpack_config = config
        
        # Block signals during load
        self.blockSignals(True)
        
        self.modpack_version_edit.setText(config.modpack_version)
        # Only set URL if not already set automatically
        if not self.configs_base_url_edit.text():
            self.configs_base_url_edit.setText(config.configs_base_url)
        self.mods_json_edit.setText(config.mods_json or 'mods.json')
        self.files_json_edit.setText(config.files_json or 'files.json')
        self.deletes_json_edit.setText(config.deletes_json or 'deletes.json')
        self.check_current_version_check.setChecked(config.check_current_version)
        self.max_retries_spin.setValue(config.max_retries)
        self.backup_keep_spin.setValue(config.backup_keep)
        self.debug_mode_check.setChecked(config.debug_mode)
        
        self.blockSignals(False)
    
    def update_version(self, version: str):
        """Update the modpack version (called when a new version is created)."""
        self.modpack_version_edit.setText(version)
        if self.modpack_config:
            self.modpack_config.modpack_version = version
    
    def save_config(self):
        """Save the form values to the ModpackConfig."""
        if not self.modpack_config:
            self.modpack_config = ModpackConfig()
        
        self.modpack_config.modpack_version = self.modpack_version_edit.text().strip()
        self.modpack_config.configs_base_url = self.configs_base_url_edit.text().strip()
        self.modpack_config.mods_json = self.mods_json_edit.text().strip() or 'mods.json'
        self.modpack_config.files_json = self.files_json_edit.text().strip() or 'files.json'
        self.modpack_config.deletes_json = self.deletes_json_edit.text().strip() or 'deletes.json'
        self.modpack_config.check_current_version = self.check_current_version_check.isChecked()
        self.modpack_config.max_retries = self.max_retries_spin.value()
        self.modpack_config.backup_keep = self.backup_keep_spin.value()
        self.modpack_config.debug_mode = self.debug_mode_check.isChecked()
        
        self.config_changed.emit()
    
    def get_config(self) -> Optional[ModpackConfig]:
        """Get the current ModpackConfig."""
        return self.modpack_config
    
    def on_field_changed(self):
        """Handle field changes - mark as modified."""
        pass  # Could add unsaved indicator


# === Theme Page ===
class ThemePage(QWidget):
    """Theme page for managing application themes."""
    theme_changed = pyqtSignal(str)  # Emits the new theme key
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        
        header = QLabel("🎨 Theme")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(20)
        
        # Theme Selection
        theme_group = QGroupBox("Select Theme")
        theme_layout = QVBoxLayout(theme_group)
        
        self.theme_combo = QComboBox()
        self._populate_theme_combo()
        self.theme_combo.currentIndexChanged.connect(self.on_theme_changed)
        theme_layout.addWidget(self.theme_combo)
        
        scroll_layout.addWidget(theme_group)
        
        # Custom Theme Management
        custom_group = QGroupBox("Custom Themes")
        custom_layout = QVBoxLayout(custom_group)
        
        create_btn = QPushButton("✨ Create New Custom Theme...")
        create_btn.setObjectName("primaryButton")
        create_btn.clicked.connect(self._create_custom_theme)
        custom_layout.addWidget(create_btn)
        
        # List of custom themes for editing/deleting
        self.custom_themes_list = QListWidget()
        self.custom_themes_list.setMaximumHeight(150)
        self._populate_custom_themes_list()
        custom_layout.addWidget(self.custom_themes_list)
        
        btn_layout = QHBoxLayout()
        self.edit_theme_btn = QPushButton("✏️ Edit Selected")
        self.edit_theme_btn.clicked.connect(self._edit_custom_theme)
        self.edit_theme_btn.setEnabled(False)
        btn_layout.addWidget(self.edit_theme_btn)
        
        self.delete_theme_btn = QPushButton("🗑️ Delete Selected")
        self.delete_theme_btn.setObjectName("dangerButton")
        self.delete_theme_btn.clicked.connect(self._delete_custom_theme)
        self.delete_theme_btn.setEnabled(False)
        btn_layout.addWidget(self.delete_theme_btn)
        
        btn_layout.addStretch()
        custom_layout.addLayout(btn_layout)
        
        self.custom_themes_list.currentRowChanged.connect(self._on_custom_theme_selected)
        
        scroll_layout.addWidget(custom_group)
        
        # Theme preview info
        preview_group = QGroupBox("Current Theme Preview")
        preview_layout = QVBoxLayout(preview_group)
        
        theme = get_current_theme()
        self.preview_container = QWidget()
        preview_inner = QHBoxLayout(self.preview_container)
        preview_inner.setSpacing(8)
        
        # Color swatches
        for color_key in ['bg_primary', 'bg_secondary', 'accent', 'text_primary', 'success', 'danger']:
            swatch = QLabel()
            swatch.setFixedSize(32, 32)
            swatch.setStyleSheet(f"background-color: {theme.get(color_key, '#000')}; border: 1px solid #888; border-radius: 4px;")
            swatch.setToolTip(color_key)
            preview_inner.addWidget(swatch)
        
        preview_inner.addStretch()
        preview_layout.addWidget(self.preview_container)
        
        scroll_layout.addWidget(preview_group)
        scroll_layout.addStretch()
        
        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)
    
    def _populate_theme_combo(self):
        """Populate the theme combo box with all themes."""
        self.theme_combo.blockSignals(True)
        self.theme_combo.clear()
        for key, theme in THEMES.items():
            self.theme_combo.addItem(theme['name'], key)
        self.theme_combo.blockSignals(False)
    
    def _populate_custom_themes_list(self):
        """Populate the list of custom themes."""
        self.custom_themes_list.clear()
        for key, theme in THEMES.items():
            if key.startswith('custom_'):
                self.custom_themes_list.addItem(theme['name'])
                self.custom_themes_list.item(self.custom_themes_list.count() - 1).setData(Qt.ItemDataRole.UserRole, key)
    
    def _on_custom_theme_selected(self, row: int):
        """Enable/disable edit and delete buttons based on selection."""
        has_selection = row >= 0
        self.edit_theme_btn.setEnabled(has_selection)
        self.delete_theme_btn.setEnabled(has_selection)
    
    def _create_custom_theme(self):
        """Open dialog to create a new custom theme."""
        current_key = self.get_theme()
        dialog = ThemeCreationDialog(self, base_theme_key=current_key)
        if dialog.exec():
            new_key = dialog.get_theme_key()
            self._populate_theme_combo()
            self._populate_custom_themes_list()
            # Select the new theme
            idx = self.theme_combo.findData(new_key)
            if idx >= 0:
                self.theme_combo.setCurrentIndex(idx)
    
    def _edit_custom_theme(self):
        """Open dialog to edit the selected custom theme."""
        item = self.custom_themes_list.currentItem()
        if not item:
            return
        theme_key = item.data(Qt.ItemDataRole.UserRole)
        if not theme_key:
            return
        
        dialog = ThemeCreationDialog(self, edit_theme_key=theme_key)
        if dialog.exec():
            self._populate_theme_combo()
            self._populate_custom_themes_list()
            # Refresh theme if editing the current theme
            if self.get_theme() == theme_key:
                self.theme_changed.emit(theme_key)
    
    def _delete_custom_theme(self):
        """Delete the selected custom theme."""
        item = self.custom_themes_list.currentItem()
        if not item:
            return
        theme_key = item.data(Qt.ItemDataRole.UserRole)
        if not theme_key:
            return
        
        # Confirm deletion
        reply = QMessageBox.question(
            self, "Delete Theme",
            f"Are you sure you want to delete the theme '{item.text()}'?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No
        )
        
        if reply == QMessageBox.StandardButton.Yes:
            # Switch to default theme if deleting current
            if self.get_theme() == theme_key:
                self.set_theme('light')
                self.theme_changed.emit('light')
            
            # Remove from THEMES dict
            if theme_key in THEMES:
                del THEMES[theme_key]
            
            # Remove from custom themes file
            save_custom_themes()
            
            self._populate_theme_combo()
            self._populate_custom_themes_list()
    
    def set_theme(self, theme_key: str):
        """Set the current theme in the combo box."""
        idx = self.theme_combo.findData(theme_key)
        if idx >= 0:
            self.theme_combo.blockSignals(True)
            self.theme_combo.setCurrentIndex(idx)
            self.theme_combo.blockSignals(False)
            self._update_preview()
    
    def get_theme(self) -> str:
        """Get the currently selected theme key."""
        return self.theme_combo.currentData() or 'light'
    
    def on_theme_changed(self):
        """Handle theme selection change."""
        theme_key = self.get_theme()
        self._update_preview()
        self.theme_changed.emit(theme_key)
    
    def _update_preview(self):
        """Update the theme preview swatches."""
        theme = get_current_theme()
        swatches = self.preview_container.findChildren(QLabel)
        color_keys = ['bg_primary', 'bg_secondary', 'accent', 'text_primary', 'success', 'danger']
        for swatch, color_key in zip(swatches, color_keys):
            swatch.setStyleSheet(f"background-color: {theme.get(color_key, '#000')}; border: 1px solid #888; border-radius: 4px;")
    
    def refresh_themes(self):
        """Refresh the theme lists (call after theme changes)."""
        self._populate_theme_combo()
        self._populate_custom_themes_list()


# === Settings Page ===
class SettingsPage(QWidget):
    """Settings page for app configuration."""
    settings_changed = pyqtSignal()
    reconfigure_requested = pyqtSignal()  # Signal for reconfigure button
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        
        header = QLabel("Settings")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(20)
        
        # GitHub Settings
        github_group = QGroupBox("GitHub Repository")
        github_layout = QFormLayout(github_group)
        
        self.repo_url_label = QLabel("Not configured")
        github_layout.addRow("Repository:", self.repo_url_label)
        
        self.reconfigure_btn = QPushButton("Reconfigure...")
        self.reconfigure_btn.clicked.connect(self.reconfigure_github)
        github_layout.addRow("", self.reconfigure_btn)
        
        scroll_layout.addWidget(github_group)
        
        # About
        about_group = QGroupBox("About")
        about_layout = QVBoxLayout(about_group)
        
        about_label = QLabel(f"""
<h3>{APP_NAME}</h3>
<p>Version {APP_VERSION}</p>
<p>A modern configuration editor for ModUpdater.</p>
<p>Features GitHub integration, version management, and theme support.</p>
        """)
        about_label.setWordWrap(True)
        about_layout.addWidget(about_label)
        
        scroll_layout.addWidget(about_group)
        scroll_layout.addStretch()
        
        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)
    
    def set_repo_url(self, url: str):
        self.repo_url_label.setText(url if url else "Not configured")
    
    def reconfigure_github(self):
        self.reconfigure_requested.emit()  # Emit specific signal for reconfigure



# === Main Window ===
class MainWindow(QMainWindow):
    """Main application window."""
    
    def __init__(self):
        super().__init__()
        self.github_api: Optional[GitHubAPI] = None
        self.editor_config: Dict[str, Any] = {}
        self.versions: Dict[str, VersionConfig] = {}
        self.current_theme = "dark"
        self.pending_changes: List[Tuple[str, str, str]] = []  # (path, content, sha)
        self._has_unsaved_deletions = False  # Track if any versions/mods/files were deleted
        
        # New data model: single files for all versions
        self.all_mods: List[ModEntry] = []
        self.all_files: List[FileEntry] = []
        self.all_deletes: Dict[str, List[DeleteEntry]] = {}  # version -> list of deletes
        self.modpack_config: Optional[ModpackConfig] = None
        self.file_shas: Dict[str, str] = {}  # filename -> sha for GitHub updates
        
        self.load_editor_config()
        self.setup_ui()
        self.apply_theme(self.current_theme)
        
        # Note: Icon preloading is now done in main() before showing the main window
        
        # Check for first-time setup
        QTimer.singleShot(100, self.check_setup)
    
    def setup_ui(self):
        self.setWindowTitle(APP_NAME)
        self.setMinimumSize(1200, 800)
        
        central = QWidget()
        self.setCentralWidget(central)
        
        main_layout = QHBoxLayout(central)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)
        
        # Sidebar
        self.sidebar = QWidget()
        self.sidebar.setFixedWidth(220)
        self.sidebar.setObjectName("sidebar")
        sidebar_layout = QVBoxLayout(self.sidebar)
        sidebar_layout.setContentsMargins(12, 16, 12, 16)
        sidebar_layout.setSpacing(8)
        
        # Logo
        logo_label = QLabel("ModUpdater")
        logo_label.setStyleSheet("font-size: 18px; font-weight: 700; padding: 8px; padding-bottom: 16px;")
        sidebar_layout.addWidget(logo_label)
        
        # Navigation
        self.nav_list = QListWidget()
        self.nav_list.addItem("📦 Versions")
        self.nav_list.addItem("🔧 Configuration")
        self.nav_list.addItem("🎨 Theme")
        self.nav_list.addItem("⚙️ Settings")
        self.nav_list.setCurrentRow(0)
        self.nav_list.currentRowChanged.connect(self.on_nav_changed)
        sidebar_layout.addWidget(self.nav_list)
        
        sidebar_layout.addStretch()
        
        # Status indicator
        self.status_label = QLabel("● Disconnected")
        theme = get_current_theme()
        self.status_label.setStyleSheet(f"color: {theme['danger']}; padding: 8px;")
        sidebar_layout.addWidget(self.status_label)
        
        # Save button
        save_btn = QPushButton("💾 Save All")
        save_btn.setObjectName("successButton")
        save_btn.clicked.connect(self.save_all)
        sidebar_layout.addWidget(save_btn)
        
        main_layout.addWidget(self.sidebar)
        
        # Content stack
        self.stack = QStackedWidget()
        
        # Version Selection Page
        self.version_selection_page = VersionSelectionPage()
        self.version_selection_page.version_selected.connect(self.open_version)
        self.version_selection_page.version_deleted.connect(self.on_version_deleted)
        self.stack.addWidget(self.version_selection_page)
        
        # Version Editor Page
        self.version_editor_page = VersionEditorPage()
        self.version_editor_page.version_modified.connect(self.on_version_modified)
        self.version_editor_page.back_requested.connect(self.show_version_selection)
        self.version_editor_page.create_requested.connect(self.on_create_version)
        self.stack.addWidget(self.version_editor_page)
        
        # Configuration Page (for main config.json)
        self.config_page = ConfigurationPage()
        self.config_page.config_changed.connect(self.on_config_changed)
        self.stack.addWidget(self.config_page)
        
        # Theme Page
        self.theme_page = ThemePage()
        self.theme_page.theme_changed.connect(self.on_theme_changed)
        self.stack.addWidget(self.theme_page)
        
        # Settings Page
        self.settings_page = SettingsPage()
        self.settings_page.reconfigure_requested.connect(self.reconfigure_github)
        self.stack.addWidget(self.settings_page)
        
        main_layout.addWidget(self.stack)
        
        # Menu bar
        self.setup_menu()
    
    def setup_menu(self):
        menubar = self.menuBar()
        
        file_menu = menubar.addMenu("File")
        
        refresh_action = QAction("Refresh from GitHub", self)
        refresh_action.setShortcut("Ctrl+R")
        refresh_action.triggered.connect(self.refresh_from_github)
        file_menu.addAction(refresh_action)
        
        file_menu.addSeparator()
        
        save_action = QAction("Save All", self)
        save_action.setShortcut("Ctrl+S")
        save_action.triggered.connect(self.save_all)
        file_menu.addAction(save_action)
        
        file_menu.addSeparator()
        
        exit_action = QAction("Exit", self)
        exit_action.setShortcut("Ctrl+Q")
        exit_action.triggered.connect(self.close)
        file_menu.addAction(exit_action)
        
        edit_menu = menubar.addMenu("Edit")
        
        validate_action = QAction("Validate All", self)
        validate_action.triggered.connect(self.validate_all)
        edit_menu.addAction(validate_action)
        
        # Theme menu removed - use the Theme page in sidebar instead
        # Initialize theme_actions as empty dict for compatibility
        self.theme_actions = {}
        
        help_menu = menubar.addMenu("Help")
        
        about_action = QAction("About", self)
        about_action.triggered.connect(self.show_about)
        help_menu.addAction(about_action)
    
    def load_editor_config(self):
        """Load editor configuration from file."""
        config_path = Path.home() / ".modupdater" / CONFIG_FILE
        if config_path.exists():
            try:
                with open(config_path, 'r') as f:
                    self.editor_config = json.load(f)
                    self.current_theme = self.editor_config.get('theme', 'light')
            except:
                pass
    
    def save_editor_config(self):
        """Save editor configuration to file."""
        config_dir = Path.home() / ".modupdater"
        config_dir.mkdir(parents=True, exist_ok=True)
        config_path = config_dir / CONFIG_FILE
        
        self.editor_config['theme'] = self.current_theme
        
        try:
            with open(config_path, 'w') as f:
                json.dump(self.editor_config, f, indent=2)
        except Exception as e:
            print(f"Failed to save config: {e}")
    
    def check_setup(self):
        """Check if first-time setup is needed."""
        if not self.editor_config.get('repo_url'):
            self.show_setup_dialog()
        else:
            self.connect_to_github()
    
    def show_setup_dialog(self, existing_config: dict = None):
        """Show the setup dialog."""
        config = existing_config or self.editor_config.get('github', {})
        # Include theme in config
        config['theme'] = self.editor_config.get('theme', self.current_theme)
        dialog = SetupDialog(self, config)
        if dialog.exec():
            github_config = dialog.get_config()
            self.editor_config['repo_url'] = github_config['repo_url']
            self.editor_config['github'] = github_config
            # Save theme from setup dialog
            if 'theme' in github_config:
                self.editor_config['theme'] = github_config['theme']
                self.current_theme = github_config['theme']
                self.apply_theme(self.current_theme)
            self.save_editor_config()
            self.connect_to_github()
        elif not self.editor_config.get('repo_url'):
            # User cancelled first-time setup
            QMessageBox.warning(self, "Setup Required", 
                "GitHub repository setup is required to use this editor.\n"
                "The application will close.")
            QTimer.singleShot(100, self.close)
    
    def _update_connection_status(self, status: str):
        """Update the connection status indicator."""
        theme = get_current_theme()
        if status == "connected":
            self.status_label.setText("● Connected")
            self.status_label.setStyleSheet(f"color: {theme['success']};")
        elif status == "failed":
            self.status_label.setText("● Connection failed")
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
        elif status == "not_configured":
            self.status_label.setText("● Not configured")
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
        else:
            self.status_label.setText("● Error")
            self.status_label.setStyleSheet(f"color: {theme['danger']};")
    
    def connect_to_github(self):
        """Connect to GitHub and fetch configs."""
        github_config = self.editor_config.get('github', {})
        repo_url = github_config.get('repo_url', '')
        token = github_config.get('token', '')
        branch = github_config.get('branch', 'main')
        config_path = github_config.get('config_path', '')
        
        if not repo_url:
            self._update_connection_status("not_configured")
            return
        
        try:
            self.github_api = GitHubAPI(repo_url, token)
            self.github_api.branch = branch
            
            if self.github_api.test_connection():
                self._update_connection_status("connected")
                self.settings_page.set_repo_url(repo_url)
                # Set repository info for automatic URL generation
                self.config_page.set_repository_info(repo_url, config_path, branch)
                self.fetch_configs()
            else:
                self._update_connection_status("failed")
        except Exception as e:
            self._update_connection_status("error")
            QMessageBox.warning(self, "Connection Error", f"Failed to connect to GitHub:\n{str(e)}")
    
    def fetch_configs(self):
        """Fetch config files from GitHub (single files, not per-version folders)."""
        if not self.github_api:
            return
        
        config_path = self.editor_config.get('github', {}).get('config_path', '')
        
        try:
            # Reset data
            self.all_mods = []
            self.all_files = []
            self.all_deletes = {}
            self.modpack_config = None
            self.file_shas = {}
            self.versions = {}
            self._has_unsaved_deletions = False  # Reset deletion flag
            
            # Fetch config.json (main config file)
            config_file = f"{config_path}/config.json" if config_path else "config.json"
            try:
                content, sha = self.github_api.get_file(config_file)
                if content:
                    data = json.loads(content)
                    self.modpack_config = ModpackConfig(data)
                    self.modpack_config._sha = sha
                    self.file_shas['config.json'] = sha
                    self.config_page.load_config(self.modpack_config)
            except Exception as e:
                print(f"No config.json found, creating default: {e}")
                self.modpack_config = ModpackConfig()
                self.config_page.load_config(self.modpack_config)
            
            # Fetch mods.json
            mods_file = f"{config_path}/mods.json" if config_path else "mods.json"
            try:
                content, sha = self.github_api.get_file(mods_file)
                if content:
                    data = json.loads(content)
                    if isinstance(data, list):
                        self.all_mods = [ModEntry(m) for m in data]
                    self.file_shas['mods.json'] = sha
            except Exception as e:
                print(f"No mods.json found: {e}")
            
            # Fetch files.json
            files_file = f"{config_path}/files.json" if config_path else "files.json"
            try:
                content, sha = self.github_api.get_file(files_file)
                if content:
                    data = json.loads(content)
                    files_data = data.get('files', []) if isinstance(data, dict) else data
                    if isinstance(files_data, list):
                        self.all_files = [FileEntry(f) for f in files_data]
                    self.file_shas['files.json'] = sha
            except Exception as e:
                print(f"No files.json found: {e}")
            
            # Fetch deletes.json (new format with version groups)
            deletes_file = f"{config_path}/deletes.json" if config_path else "deletes.json"
            try:
                content, sha = self.github_api.get_file(deletes_file)
                if content:
                    data = json.loads(content)
                    # Parse new format: { "safetyMode": true, "deletions": [{"version": "1.0.0", "paths": [...]}] }
                    deletions = data.get('deletions', [])
                    for deletion in deletions:
                        version = deletion.get('version', '')
                        if version:
                            paths = deletion.get('paths', [])
                            self.all_deletes[version] = [DeleteEntry(p) for p in paths]
                    self.file_shas['deletes.json'] = sha
            except Exception as e:
                print(f"No deletes.json found: {e}")
            
            # Build versions based on unique "since" values from mods and files
            self._build_versions_from_data()
            
            self.version_selection_page.set_versions(self.versions)
            
        except Exception as e:
            QMessageBox.warning(self, "Fetch Error", f"Failed to fetch configs:\n{str(e)}")
    
    def _build_versions_from_data(self):
        """Build version configs from loaded mods, files, and deletes."""
        # Collect all unique versions
        all_versions = set()
        
        # From modpack config
        if self.modpack_config and self.modpack_config.modpack_version:
            all_versions.add(self.modpack_config.modpack_version)
        
        # From mods
        for mod in self.all_mods:
            if mod.since:
                all_versions.add(mod.since)
        
        # From files
        for f in self.all_files:
            if f.since:
                all_versions.add(f.since)
        
        # From deletes
        for version in self.all_deletes.keys():
            all_versions.add(version)
        
        # Create VersionConfig for each version
        self.versions = {}
        for version in all_versions:
            version_config = VersionConfig(version)
            
            # Add mods that were introduced at or before this version
            for mod in self.all_mods:
                if self._compare_versions(mod.since, version) <= 0:
                    # Create a copy for this version
                    mod_copy = ModEntry(mod.to_dict())
                    mod_copy.since = mod.since
                    version_config.mods.append(mod_copy)
            
            # Add files that were introduced at or before this version
            for f in self.all_files:
                if self._compare_versions(f.since, version) <= 0:
                    file_copy = FileEntry(f.to_dict())
                    file_copy.since = f.since
                    version_config.files.append(file_copy)
            
            # Add deletes for this specific version
            if version in self.all_deletes:
                version_config.deletes = list(self.all_deletes[version])
            
            # Mark existing versions as locked
            version_config._is_locked = True
            version_config._is_new = False
            
            self.versions[version] = version_config
    
    def _compare_versions(self, v1: str, v2: str) -> int:
        """Compare two version strings. Returns positive if v1 > v2, negative if v1 < v2, 0 if equal."""
        def parse(v):
            if not v or not v.strip():
                return [0]
            parts = v.strip().split('.')
            nums = []
            for x in parts:
                x = x.strip()
                if not x:
                    nums.append(0)
                    continue
                try:
                    nums.append(int(x))
                except ValueError:
                    nums.append(0)
            return nums if nums else [0]
        
        p1, p2 = parse(v1), parse(v2)
        # Pad with zeros
        while len(p1) < len(p2):
            p1.append(0)
        while len(p2) < len(p1):
            p2.append(0)
        
        for a, b in zip(p1, p2):
            if a > b:
                return 1
            elif a < b:
                return -1
        return 0
    
    def apply_theme(self, theme_key: str):
        """Apply a theme to the application."""
        if theme_key not in THEMES:
            theme_key = "dark"
        
        self.current_theme = theme_key
        theme = THEMES[theme_key]
        
        # Update global theme for widget access
        set_current_theme(theme_key)
        
        # Generate and apply stylesheet
        stylesheet = generate_stylesheet(theme)
        
        # Add sidebar-specific styling
        stylesheet += f"""
        QWidget#sidebar {{
            background-color: {theme['bg_sidebar']};
        }}
        """
        
        QApplication.instance().setStyleSheet(stylesheet)
        
        # Update theme page if it exists
        if hasattr(self, 'theme_page'):
            self.theme_page.set_theme(theme_key)
        
        # Refresh any visible grids to update their styling
        self.version_selection_page.refresh_grid()
        if hasattr(self.version_editor_page, 'version_config') and self.version_editor_page.version_config:
            self.version_editor_page.refresh_mods_grid()
            self.version_editor_page.refresh_files_grid()
            self.version_editor_page.refresh_editor_panels_style()
    
    def on_nav_changed(self, index: int):
        """Handle navigation list selection change."""
        if index == 0:
            self.show_version_selection()
        elif index == 1:
            self.stack.setCurrentWidget(self.config_page)
        elif index == 2:
            self.stack.setCurrentWidget(self.theme_page)
        elif index == 3:
            self.stack.setCurrentWidget(self.settings_page)
    
    def show_version_selection(self):
        """Show the version selection page."""
        # Refresh the grid to show updated icons and version data
        self.version_selection_page.refresh_grid()
        self.stack.setCurrentWidget(self.version_selection_page)
        self.nav_list.setCurrentRow(0)
        self.sidebar.setVisible(True)  # Show sidebar when returning to version selection
    
    def open_version(self, version: str):
        """Open a version for editing."""
        if version in self.versions:
            self.version_editor_page.load_version(self.versions[version])
            self.stack.setCurrentWidget(self.version_editor_page)
            self.sidebar.setVisible(False)  # Hide sidebar when editing version
    
    def on_version_modified(self):
        """Handle version modification."""
        # Refresh the version selection page grid to show updated icons immediately
        self.version_selection_page.refresh_grid()
    
    def on_version_deleted(self, version: str):
        """Handle version deletion - update internal data model."""
        # Remove from versions dict
        if version in self.versions:
            del self.versions[version]
        
        # Mark that we have unsaved deletions
        self._has_unsaved_deletions = True
        
        # Note: We don't delete from GitHub here - that would be done in save_all or a separate operation
        # For now, just remove from local state and let the user save changes
        
        # Update all_mods to remove mods that were first introduced in this version
        self.all_mods = [m for m in self.all_mods if m.since != version]
        
        # Update all_files similarly
        self.all_files = [f for f in self.all_files if f.since != version]
        
        # Remove deletes for this version
        if version in self.all_deletes:
            del self.all_deletes[version]
        
        # If the deleted version was the modpack_version in config.json, update it
        if self.modpack_config and self.modpack_config.modpack_version == version:
            # Find the new latest version from remaining versions
            remaining_versions = list(self.versions.keys())
            if remaining_versions:
                # Sort to find the highest remaining version
                def version_sort_key(v: str):
                    parts = v.split('.')
                    nums = []
                    for x in parts:
                        try:
                            nums.append(int(x))
                        except ValueError:
                            nums.append(0)
                    return tuple(nums)
                remaining_versions.sort(key=version_sort_key, reverse=True)
                self.modpack_config.modpack_version = remaining_versions[0]
            else:
                # No versions left
                self.modpack_config.modpack_version = ""
            # Reload the config page to reflect the change
            self.config_page.load_config(self.modpack_config)
    
    def on_config_changed(self):
        """Handle configuration page changes."""
        # Configuration was saved, mark as needing to be pushed to GitHub
        pass
    
    def on_create_version(self, version_config: VersionConfig):
        """Handle Create Version button - save version to repo using single-file format."""
        if not self.github_api:
            QMessageBox.warning(self, "Not Connected", "Please configure GitHub connection first.")
            return
        
        version = version_config.version
        config_path = self.editor_config.get('github', {}).get('config_path', '')
        
        # Add new mods to all_mods with their since field
        for mod in version_config.mods:
            # Check if mod already exists
            existing = [m for m in self.all_mods if m.id == mod.id]
            if not existing:
                mod.since = version
                self.all_mods.append(mod)
        
        # Add new files to all_files
        for f in version_config.files:
            # Check if file already exists (by URL or name)
            existing = [ef for ef in self.all_files if ef.url == f.url]
            if not existing:
                f.since = version
                self.all_files.append(f)
        
        # Add deletes for this version
        if version_config.deletes:
            self.all_deletes[version] = version_config.deletes
        
        # Update modpack config version
        if not self.modpack_config:
            self.modpack_config = ModpackConfig()
        self.modpack_config.modpack_version = version
        
        # Prepare single files
        changes = []
        
        # Prepare config.json
        config_file = f"{config_path}/config.json" if config_path else "config.json"
        config_content = json.dumps(self.modpack_config.to_dict(), indent=2)
        changes.append((config_file, config_content, self.file_shas.get('config.json')))
        
        # Prepare mods.json (all mods)
        mods_file = f"{config_path}/mods.json" if config_path else "mods.json"
        mods_content = json.dumps([m.to_dict() for m in self.all_mods], indent=2)
        changes.append((mods_file, mods_content, self.file_shas.get('mods.json')))
        
        # Prepare files.json (all files)
        files_file = f"{config_path}/files.json" if config_path else "files.json"
        files_content = json.dumps({'files': [f.to_dict() for f in self.all_files]}, indent=2)
        changes.append((files_file, files_content, self.file_shas.get('files.json')))
        
        # Prepare deletes.json (all versions' deletes in new format)
        deletes_file = f"{config_path}/deletes.json" if config_path else "deletes.json"
        deletions_list = []
        for del_version, del_entries in self.all_deletes.items():
            if del_entries:
                deletions_list.append({
                    'version': del_version,
                    'paths': [d.to_dict() for d in del_entries]
                })
        deletes_obj = {
            'safetyMode': True,
            'deletions': deletions_list
        }
        deletes_content = json.dumps(deletes_obj, indent=2)
        changes.append((deletes_file, deletes_content, self.file_shas.get('deletes.json')))
        
        # Show progress (without cancel button - disable close)
        progress = QProgressDialog(f"Creating version {version}...", None, 0, len(changes), self)
        progress.setWindowModality(Qt.WindowModality.WindowModal)
        progress.setMinimumDuration(0)
        progress.setCancelButton(None)  # Remove cancel button
        progress.setWindowFlags(progress.windowFlags() & ~Qt.WindowType.WindowCloseButtonHint)
        
        errors = []
        
        for i, (path, content, sha) in enumerate(changes):
            progress.setValue(i)
            QApplication.processEvents()
            
            try:
                result = self.github_api.create_or_update_file(
                    path=path,
                    content=content,
                    message=f"Update to version {version}",
                    sha=sha
                )
                # Update SHA
                new_sha = result.get('content', {}).get('sha')
                if new_sha:
                    filename = path.split('/')[-1]
                    self.file_shas[filename] = new_sha
            except Exception as e:
                errors.append(f"{path}: {str(e)}")
        
        progress.setValue(len(changes))
        
        if errors:
            QMessageBox.warning(self, "Errors", "Some files failed to save:\n\n" + "\n".join(errors))
        else:
            # Lock the version so it can't be edited
            version_config.lock()
            version_config.modified = False
            
            # Update versions dict
            self.versions[version] = version_config
            
            # Refresh config page and update version
            self.config_page.update_version(version)
            self.config_page.load_config(self.modpack_config)
            
            QMessageBox.information(self, "Success", f"Version {version} created successfully!\n\nThis version is now locked and cannot be edited.")
            
            # Refresh the editor to show locked state
            self.version_editor_page.load_version(version_config)
    
    def save_version_locally(self, version_config: VersionConfig):
        """Save version config locally in versions folder."""
        try:
            versions_dir = Path("versions")
            versions_dir.mkdir(exist_ok=True)
            
            version_file = versions_dir / f"{version_config.version}.json"
            
            data = {
                'version': version_config.version,
                'mods': [m.to_dict() for m in version_config.mods],
                'files': [f.to_dict() for f in version_config.files],
                'deletes': [d.to_dict() for d in version_config.deletes],
                'locked': version_config.is_locked()
            }
            
            with open(version_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            print(f"Failed to save version locally: {e}")
    
    def on_theme_changed(self, theme_key: str):
        """Handle theme change from theme page."""
        if theme_key != self.current_theme:
            self.apply_theme(theme_key)
            self.save_editor_config()
    
    def reconfigure_github(self):
        """Open the setup dialog to reconfigure GitHub connection."""
        existing_config = self.editor_config.get('github', {})
        dialog = SetupDialog(self, existing_config)
        if dialog.exec():
            new_config = dialog.get_config()
            self.editor_config['github'] = new_config
            self.editor_config['repo_url'] = new_config.get('repo_url', '')
            self.save_editor_config()
            self.github_api = GitHubAPI(new_config.get('repo_url', ''), new_config.get('token', ''))
            self.github_api.branch = new_config.get('branch', 'main')
            # Update connection status
            if self.github_api.test_connection():
                self._update_connection_status("connected")
            else:
                self._update_connection_status("failed")
            self.settings_page.set_repo_url(new_config.get('repo_url', ''))
            self.fetch_configs()
    
    def refresh_from_github(self):
        """Refresh all data from GitHub."""
        reply = QMessageBox.question(
            self, "Refresh",
            "This will discard any unsaved changes. Continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.fetch_configs()
            self.show_version_selection()
    
    def save_all(self):
        """Save all changes to GitHub using single-file format."""
        if not self.github_api:
            QMessageBox.warning(self, "Not Connected", "Please configure GitHub connection first.")
            return
        
        config_path = self.editor_config.get('github', {}).get('config_path', '')
        
        # Prepare all files
        changes = []
        
        # Save config.json if modified
        if self.modpack_config:
            config_file = f"{config_path}/config.json" if config_path else "config.json"
            config_content = json.dumps(self.modpack_config.to_dict(), indent=2)
            changes.append((config_file, config_content, self.file_shas.get('config.json')))
        
        # Save mods.json (all mods)
        mods_file = f"{config_path}/mods.json" if config_path else "mods.json"
        mods_content = json.dumps([m.to_dict() for m in self.all_mods], indent=2)
        changes.append((mods_file, mods_content, self.file_shas.get('mods.json')))
        
        # Save files.json (all files)
        files_file = f"{config_path}/files.json" if config_path else "files.json"
        files_content = json.dumps({'files': [f.to_dict() for f in self.all_files]}, indent=2)
        changes.append((files_file, files_content, self.file_shas.get('files.json')))
        
        # Save deletes.json (all versions' deletes)
        deletes_file = f"{config_path}/deletes.json" if config_path else "deletes.json"
        deletions_list = []
        for del_version, del_entries in self.all_deletes.items():
            if del_entries:
                deletions_list.append({
                    'version': del_version,
                    'paths': [d.to_dict() for d in del_entries]
                })
        deletes_obj = {
            'safetyMode': True,
            'deletions': deletions_list
        }
        deletes_content = json.dumps(deletes_obj, indent=2)
        changes.append((deletes_file, deletes_content, self.file_shas.get('deletes.json')))
        
        if not changes:
            QMessageBox.information(self, "No Changes", "No changes to save.")
            return
        
        # Show progress (without cancel button - disable close)
        progress = QProgressDialog("Saving to GitHub...", None, 0, len(changes), self)
        progress.setWindowModality(Qt.WindowModality.WindowModal)
        progress.setMinimumDuration(0)
        progress.setCancelButton(None)  # Remove cancel button
        progress.setWindowFlags(progress.windowFlags() & ~Qt.WindowType.WindowCloseButtonHint)
        
        errors = []
        
        for i, (path, content, sha) in enumerate(changes):
            progress.setValue(i)
            progress.setLabelText(f"Saving {path}...")
            QApplication.processEvents()
            
            try:
                result = self.github_api.create_or_update_file(
                    path, content, f"Update {path} via Config Editor", sha
                )
                # Update SHA for future saves
                new_sha = result.get('content', {}).get('sha')
                if new_sha:
                    filename = path.split('/')[-1]
                    self.file_shas[filename] = new_sha
                    
            except Exception as e:
                errors.append(f"{path}: {str(e)}")
        
        progress.setValue(len(changes))
        
        if errors:
            QMessageBox.warning(self, "Save Errors", 
                f"Some files failed to save:\n\n" + "\n".join(errors))
        else:
            # Mark all versions as not modified
            for config in self.versions.values():
                config.modified = False
            # Clear the unsaved deletions flag
            self._has_unsaved_deletions = False
            QMessageBox.information(self, "Saved", "All changes saved to GitHub successfully!")
    
    def validate_all(self):
        """Validate all configurations."""
        errors = []
        
        for version, config in self.versions.items():
            # Check mods
            ids_seen = set()
            for i, mod in enumerate(config.mods):
                if not mod.id:
                    errors.append(f"[{version}] Mod {i+1}: Missing ID")
                elif mod.id in ids_seen:
                    errors.append(f"[{version}] Mod {i+1}: Duplicate ID '{mod.id}'")
                else:
                    ids_seen.add(mod.id)
                
                if not mod.display_name:
                    errors.append(f"[{version}] Mod {i+1}: Missing display name")
            
            # Check files
            for i, f in enumerate(config.files):
                if not f.url:
                    errors.append(f"[{version}] File {i+1}: Missing URL")
            
            # Check deletes
            for i, d in enumerate(config.deletes):
                if not d.path:
                    errors.append(f"[{version}] Delete {i+1}: Missing path")
        
        if errors:
            QMessageBox.warning(self, "Validation Errors",
                "The following issues were found:\n\n" + "\n".join(errors[:20]))
            if len(errors) > 20:
                QMessageBox.warning(self, "More Errors", 
                    f"...and {len(errors) - 20} more errors.")
        else:
            QMessageBox.information(self, "Valid", "All configurations are valid!")
    
    def show_about(self):
        """Show about dialog."""
        QMessageBox.about(self, f"About {APP_NAME}",
            f"""<h2>{APP_NAME}</h2>
            <p>Version {APP_VERSION}</p>
            <p>A modern GUI application for editing ModUpdater configuration files.</p>
            <p>Features:</p>
            <ul>
                <li>GitHub integration</li>
                <li>Version management</li>
                <li>Auto-fill hashes</li>
                <li>Theme support</li>
                <li>Mod icon fetching</li>
            </ul>
            """)
    
    def show_create_theme_dialog(self):
        """Show the theme creation dialog."""
        dialog = ThemeCreationDialog(self, self.current_theme)
        if dialog.exec():
            # Refresh theme page
            if hasattr(self, 'theme_page'):
                self.theme_page.refresh_themes()
            # Apply the new theme
            theme_key = dialog.get_theme_key()
            if theme_key in THEMES:
                self._on_theme_selected(theme_key)
    
    def _on_theme_selected(self, theme_key: str):
        """Handle theme selection."""
        if theme_key in THEMES:
            self.current_theme = theme_key
            self.apply_theme(theme_key)
            self.save_editor_config()
    
    def _shutdown_threads(self):
        """Gracefully stop all background threads before exit."""
        try:
            # Stop icon load threads in version editor page
            if hasattr(self, 'version_editor_page') and self.version_editor_page:
                try:
                    self.version_editor_page._cancel_icon_load_threads()
                except Exception:
                    pass
                
                # Clear mod and file editors to stop any hash calculators
                try:
                    if hasattr(self.version_editor_page, 'mod_editor'):
                        self.version_editor_page.mod_editor.clear()
                except Exception:
                    pass
                try:
                    if hasattr(self.version_editor_page, 'file_editor'):
                        self.version_editor_page.file_editor.clear()
                except Exception:
                    pass
        except Exception:
            pass
    
    def closeEvent(self, event):
        """Handle window close."""
        # Shutdown background threads first
        self._shutdown_threads()
        
        # Check for unsaved changes
        has_unsaved = any(v.modified for v in self.versions.values()) or self._has_unsaved_deletions
        
        if has_unsaved:
            # Create custom message box with only Back and Exit buttons
            msg_box = QMessageBox(self)
            msg_box.setWindowTitle("Unsaved Changes")
            msg_box.setText("Unsaved changes will be lost.")
            msg_box.setIcon(QMessageBox.Icon.Warning)
            
            back_btn = msg_box.addButton("Back", QMessageBox.ButtonRole.RejectRole)
            exit_btn = msg_box.addButton("Exit", QMessageBox.ButtonRole.AcceptRole)
            
            msg_box.exec()
            
            if msg_box.clickedButton() == exit_btn:
                # Shutdown threads again before accepting exit
                self._shutdown_threads()
                event.accept()
            else:
                event.ignore()
        else:
            event.accept()


def main():
    """Main entry point."""
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    
    # Load custom themes first
    load_custom_themes()
    
    # Load saved theme from config or default to light
    config_path = Path.home() / ".modupdater" / CONFIG_FILE
    saved_theme = "light"  # Default to light theme
    if config_path.exists():
        try:
            with open(config_path, 'r') as f:
                editor_config = json.load(f)
                saved_theme = editor_config.get('theme', 'light')
        except Exception:
            pass
    
    # Apply initial theme for loading dialog
    set_current_theme(saved_theme)
    initial_theme = THEMES.get(saved_theme, THEMES["light"])
    app.setStyleSheet(generate_stylesheet(initial_theme))

    # Start preloading icons immediately
    ModBrowserDialog.start_startup_preload()
    
    # Show loading dialog
    loading_dialog = LoadingDialog()
    
    # Set a maximum timeout for loading (10 seconds - allows time for icons to load)
    timeout_timer = QTimer()
    timeout_timer.setSingleShot(True)
    timeout_timer.timeout.connect(loading_dialog.force_close)
    timeout_timer.start(10000)
    
    loading_dialog.start_checking()
    loading_dialog.show()
    
    # Process events while loading with small delay to reduce CPU usage
    import time
    while loading_dialog.isVisible():
        app.processEvents()
        time.sleep(0.01)  # 10ms delay to reduce CPU consumption
    
    timeout_timer.stop()
    
    # Create and show main window
    window = MainWindow()
    window.show()

    sys.exit(app.exec() if PYQT_VERSION == 6 else app.exec_())


if __name__ == "__main__":
    main()
