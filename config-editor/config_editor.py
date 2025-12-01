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

# === Icon Preloading Configuration ===
# Configurable preload sizes
LARGE_LOAD_AMOUNT = 25   # How many icons count as a "large" preload
SMALL_LOAD_AMOUNT = 8    # How many icons count as a "small" preload

# Configurable preload range (number of pages before/after current page)
MAX_LARGE_PROFILE_RANGE = 1   # How many pages before/after get LARGE_LOAD_AMOUNT preloaded
MAX_SMALL_PROFILE_RANGE = 1   # How many pages before/after get SMALL_LOAD_AMOUNT preloaded

# Legacy settings (kept for backward compatibility)
PAGE_ICON_CACHE_SIZE = SMALL_LOAD_AMOUNT  # Alias for backward compatibility
MAX_CACHED_PAGES = 2 * (MAX_LARGE_PROFILE_RANGE + MAX_SMALL_PROFILE_RANGE) + 1  # Total pages in cache range

# Image scaling settings
MAX_DESCRIPTION_IMAGE_WIDTH = 400  # Maximum width for images in mod descriptions

# Icon loading settings
ICON_MAX_CONCURRENT_LOADS = 6  # Maximum number of concurrent icon downloads (increased for faster preloading)
ICON_LOAD_DEBOUNCE_MS = 50  # Debounce delay for scroll events (ms)
ICON_PRELOAD_DELAY_MS = 100  # Delay before starting background preload (ms) - reduced for faster initial load
ICON_CASCADE_THRESHOLD_DIVISOR = 2  # Divisor for max concurrent loads to determine cascade trigger threshold
ICON_LOAD_RETRY_DELAY_MS = 100  # Delay before retrying icon loads (ms)
ICON_LOAD_INITIAL_DELAY_MS = 10  # Small delay before starting icon loads (ms)

# Placeholder icon (base64-encoded gray box with package emoji concept)
# This is a simple 48x48 gray placeholder that indicates "loading"
PLACEHOLDER_ICON_COLOR = "#3d3d4d"  # Subtle gray that works on dark themes

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
THEMES = {
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
}

# Store current theme globally for access by widgets
_current_theme = THEMES["dark"]

def get_current_theme() -> dict:
    """Get the currently active theme."""
    return _current_theme

def set_current_theme(theme_key: str):
    """Set the current theme."""
    global _current_theme
    if theme_key in THEMES:
        _current_theme = THEMES[theme_key]


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
    padding: 8px 12px;
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
}}

QComboBox QAbstractItemView::item {{
    padding: 4px 8px;
    min-height: 20px;
    margin: 0px;
}}

QComboBox QAbstractItemView::item:hover {{
    background-color: {theme['bg_tertiary']};
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
    margin-top: 12px;
    padding: 16px;
    padding-top: 24px;
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
        # Force a repaint to show the newly loaded image
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
# Simplified icon loading with placeholder support and efficient caching

def create_placeholder_pixmap(size: int = 48) -> QPixmap:
    """Create a simple placeholder pixmap for loading state."""
    pixmap = QPixmap(size, size)
    pixmap.fill(QColor(PLACEHOLDER_ICON_COLOR))
    
    # Draw a subtle loading indicator (circle)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    pen_color = QColor("#5a5a6a")
    painter.setPen(pen_color)
    painter.setBrush(Qt.BrushStyle.NoBrush)
    margin = size // 6
    painter.drawEllipse(margin, margin, size - 2 * margin, size - 2 * margin)
    painter.end()
    
    return pixmap

# Global placeholder icon (created once and reused)
_placeholder_pixmap = None

def get_placeholder_icon() -> QIcon:
    """Get the shared placeholder icon."""
    global _placeholder_pixmap
    if _placeholder_pixmap is None:
        _placeholder_pixmap = create_placeholder_pixmap(48)
    return QIcon(_placeholder_pixmap)


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
        # Support both "id" (new) and "numberId" (legacy)
        mod_id = self.mod_info.get('id', self.mod_info.get('numberId', ''))
        
        try:
            icon_data = None
            if source_type == 'modrinth':
                project_slug = source.get('projectSlug')
                if project_slug:
                    icon_data = self._fetch_modrinth_icon(project_slug)
            
            if icon_data and self._running:
                self.icon_fetched.emit(mod_id, icon_data)
        except Exception as e:
            print(f"Error fetching icon for {mod_id}: {e}")
        
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
        except Exception:
            pass
        return None
    
    def stop(self):
        self._running = False


class SimpleIconFetcher(QThread):
    """Simplified icon fetcher that emits mod_id and source for cache lookup."""
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
        except Exception:
            pass  # Silently ignore network errors
        
        self.finished_loading.emit(self.mod_id)
    
    def stop(self):
        self._running = False


# Legacy classes for backward compatibility
class ModIconFetcher(QThread):
    """Background thread for fetching mod icons from URLs (used in mod browser)."""
    icon_fetched = pyqtSignal(object, bytes)  # QListWidgetItem, icon_bytes
    
    def __init__(self, item, icon_url: str):
        super().__init__()
        self.item = item
        self.icon_url = icon_url
        self._running = True
        # Additional attributes for improved caching
        self.mod_id = None
        self.source = None
        self.idx = -1
    
    def run(self):
        """Fetch icon from URL."""
        if not self._running:
            return
        try:
            req = urllib.request.Request(self.icon_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=5) as response:
                data = response.read()
                if data and self._running:
                    self.icon_fetched.emit(self.item, data)
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, Exception):
            pass  # Silently ignore network errors during icon fetch
    
    def stop(self):
        self._running = False


class _BackgroundIconFetcher(QThread):
    """Background thread for preloading icons without a list item (legacy)."""
    icon_fetched = pyqtSignal(str, str, bytes)  # mod_id, source, icon_bytes
    
    def __init__(self, mod_id: str, icon_url: str, source: str):
        super().__init__()
        self.mod_id = mod_id
        self.icon_url = icon_url
        self.source = source
        self._running = True
    
    def run(self):
        """Fetch icon from URL."""
        if not self._running:
            return
        try:
            req = urllib.request.Request(self.icon_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=5) as response:
                data = response.read()
                if data and self._running:
                    self.icon_fetched.emit(self.mod_id, self.source, data)
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, Exception):
            pass  # Silently ignore network errors during icon fetch
    
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


class SetupDialog(QDialog):
    """First-time setup dialog for GitHub configuration."""
    
    def __init__(self, parent=None, existing_config: dict = None):
        super().__init__(parent)
        self.config = existing_config or {}
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("GitHub Repository Setup")
        self.setMinimumSize(550, 400)
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
        form_layout.addRow("Repository URL:", self.repo_url_edit)
        
        # Token with help button
        token_layout = QHBoxLayout()
        self.token_edit = QLineEdit(self.config.get('token', ''))
        self.token_edit.setPlaceholderText("ghp_xxxxxxxxxxxx (REQUIRED)")
        self.token_edit.setEchoMode(QLineEdit.EchoMode.Password)
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
        form_layout.addRow("Branch:", self.branch_edit)
        
        # Hidden config_path field for backward compatibility
        self.config_path_edit = QLineEdit(self.config.get('config_path', ''))
        self.config_path_edit.setVisible(False)
        
        layout.addWidget(form_group)
        
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
            'config_path': self.config_path_edit.text().strip()
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
        form_layout.addRow("Name:", self.name_edit)
        
        self.id_edit = QLineEdit()
        self.id_edit.setPlaceholderText("Unique ID (e.g., jei)")
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
    
    def __init__(self, source: str, query: str, version_filter: str = ""):
        super().__init__()
        self.source = source
        self.query = query
        self.version_filter = version_filter
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
        params = {
            'gameId': '432',  # Minecraft
            'classId': '6',   # Mods
            'sortField': '2',  # Popularity (download count)
            'sortOrder': 'desc',
            'pageSize': str(SEARCH_PAGE_SIZE),
            'index': str(self.offset)  # For pagination
        }
        # Only add search filter if query is not empty
        if self.query:
            params['searchFilter'] = self.query
        if self.version_filter:
            params['gameVersion'] = self.version_filter
        
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
        
        params = {
            'facets': json.dumps(facets),
            'limit': str(SEARCH_PAGE_SIZE),
            'offset': str(self.offset),  # For pagination
            'index': 'downloads'  # Sort by downloads
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
    """Dialog for browsing and selecting mods from CurseForge/Modrinth (like Prism Launcher).
    
    Icon Preloading System:
    - CurseForge and Modrinth have INDEPENDENT icon caches
    - On dialog open: Page 1 icons are already preloaded (LARGE_LOAD_AMOUNT)
    - Preload strategy per source:
      - Page 1: LARGE_LOAD_AMOUNT icons preloaded at startup
      - Page 2: LARGE_LOAD_AMOUNT icons preloaded 
      - Page 3: SMALL_LOAD_AMOUNT icons preloaded
    - When navigating to page X:
      - Load remaining (SEARCH_PAGE_SIZE - LARGE_LOAD_AMOUNT) icons for page X
      - Preload LARGE_LOAD_AMOUNT for pages X-1 and X+1
      - Preload SMALL_LOAD_AMOUNT for pages X-2 and X+2
      - Unload pages outside the configured range
    """
    
    # Class-level shared icon caches (persist across dialog instances)
    # Separate caches for each source to maintain isolation
    _shared_icon_cache = {
        'curseforge': {},  # mod_id -> icon_bytes
        'modrinth': {}     # mod_id -> icon_bytes
    }
    
    # Track which pages have been preloaded for each source
    # Structure: {source: {page: preload_count}} where preload_count = number of icons loaded
    _page_preload_state = {
        'curseforge': {},
        'modrinth': {}
    }
    
    # Store mod data for pages (needed to apply icons when displaying)
    # Structure: {source: {page: [mod_data_list]}}
    _page_mod_data = {
        'curseforge': {},
        'modrinth': {}
    }
    
    # Track active preload threads per source
    _preload_threads = {
        'curseforge': [],
        'modrinth': []
    }
    
    # Loading state per source
    _loading_mod_ids_per_source = {
        'curseforge': set(),
        'modrinth': set()
    }
    
    # Flag to track if startup preloading has been initiated
    _startup_preload_started = False
    
    @classmethod
    def start_startup_preload(cls):
        """Start preloading icons for both CurseForge and Modrinth at program startup.
        
        This should be called when the program first launches (not when the user opens the dialog).
        Preloads:
        - Page 1: LARGE_LOAD_AMOUNT icons for both sources
        - Page 2: LARGE_LOAD_AMOUNT icons for both sources
        - Page 3: SMALL_LOAD_AMOUNT icons for both sources
        """
        if cls._startup_preload_started:
            return  # Already started
        
        cls._startup_preload_started = True
        
        # Preload for both sources independently
        for source in ['curseforge', 'modrinth']:
            # Page 0 (first page): LARGE_LOAD_AMOUNT
            cls._fetch_and_preload_page_static(source, 0, LARGE_LOAD_AMOUNT)
            # Page 1 (second page): LARGE_LOAD_AMOUNT 
            cls._fetch_and_preload_page_static(source, 1, LARGE_LOAD_AMOUNT)
            # Page 2 (third page): SMALL_LOAD_AMOUNT
            cls._fetch_and_preload_page_static(source, 2, SMALL_LOAD_AMOUNT)
    
    @classmethod
    def _fetch_and_preload_page_static(cls, source: str, page: int, preload_amount: int):
        """Fetch page data and start preloading icons (class method for startup use)."""
        # Create a search thread to fetch page data
        thread = ModSearchThread(source, "", "")
        thread.offset = page * SEARCH_PAGE_SIZE
        thread.search_complete.connect(
            lambda results, total, s=source, p=page, pa=preload_amount:
                cls._on_startup_page_fetched(results, s, p, pa))
        thread.finished.connect(thread.deleteLater)
        
        if source not in cls._preload_threads:
            cls._preload_threads[source] = []
        cls._preload_threads[source].append(thread)
        thread.start()
    
    @classmethod
    def _on_startup_page_fetched(cls, results: list, source: str, page: int, preload_amount: int):
        """Handle page data fetched during startup preloading."""
        if not results:
            return
        
        # Store page data
        if source not in cls._page_mod_data:
            cls._page_mod_data[source] = {}
        cls._page_mod_data[source][page] = results
        
        # Start preloading icons for this page
        cls._preload_icons_for_page_static(source, page, preload_amount, 0)
    
    @classmethod
    def _preload_icons_for_page_static(cls, source: str, page: int, target_count: int, start_index: int):
        """Preload icons for a page (class method for startup use)."""
        mods = cls._page_mod_data.get(source, {}).get(page, [])
        if not mods:
            return
        
        icons_cache = cls._shared_icon_cache.get(source, {})
        loading_ids = cls._loading_mod_ids_per_source.get(source, set())
        current_preload_count = cls._page_preload_state.get(source, {}).get(page, 0)
        
        # Check if we've reached target
        if current_preload_count >= target_count:
            return
        
        icons_started = 0
        max_concurrent = ICON_MAX_CONCURRENT_LOADS
        
        for i in range(start_index, min(len(mods), target_count)):
            mod = mods[i]
            mod_id = mod.get('id', mod.get('slug', ''))
            icon_url = mod.get('icon_url', '')
            
            if not mod_id or not icon_url:
                continue
            
            # Skip if already cached
            if mod_id in icons_cache:
                continue
            
            # Skip if already loading
            if mod_id in loading_ids:
                continue
            
            # Check concurrent load limit
            active_threads = cls._preload_threads.get(source, [])
            active_count = sum(1 for t in active_threads if cls._thread_is_running_static(t))
            
            if active_count >= max_concurrent:
                # Schedule retry
                QTimer.singleShot(100, lambda s=source, p=page, tc=target_count, si=i: 
                    cls._preload_icons_for_page_static(s, p, tc, si))
                return
            
            # Start loading this icon
            if source not in cls._loading_mod_ids_per_source:
                cls._loading_mod_ids_per_source[source] = set()
            cls._loading_mod_ids_per_source[source].add(mod_id)
            
            try:
                thread = SimpleIconFetcher(mod_id, icon_url, source)
                thread.icon_fetched.connect(cls._on_startup_icon_fetched)
                thread.finished_loading.connect(
                    lambda mid=mod_id, s=source, p=page, tc=target_count, ci=i:
                        cls._on_startup_icon_complete(mid, s, p, tc, ci))
                thread.finished.connect(thread.deleteLater)
                
                if source not in cls._preload_threads:
                    cls._preload_threads[source] = []
                cls._preload_threads[source].append(thread)
                thread.start()
                icons_started += 1
            except Exception:
                cls._loading_mod_ids_per_source[source].discard(mod_id)
            
            # Allow multiple parallel loads up to the limit
            if icons_started >= max_concurrent:
                return
    
    @staticmethod
    def _on_startup_icon_fetched(mod_id: str, source: str, data: bytes):
        """Handle icon fetched during startup preloading."""
        ModBrowserDialog._shared_icon_cache[source][mod_id] = data
    
    @classmethod
    def _on_startup_icon_complete(cls, mod_id: str, source: str, page: int, target_count: int, current_index: int):
        """Handle completion of a startup preload icon fetch."""
        if source in cls._loading_mod_ids_per_source:
            cls._loading_mod_ids_per_source[source].discard(mod_id)
        
        # Update preload state
        if source not in cls._page_preload_state:
            cls._page_preload_state[source] = {}
        current_count = cls._page_preload_state[source].get(page, 0)
        cls._page_preload_state[source][page] = current_count + 1
        
        # Clean up finished threads
        if source in cls._preload_threads:
            cls._preload_threads[source] = [
                t for t in cls._preload_threads[source] 
                if cls._thread_is_running_static(t)
            ]
        
        # Continue preloading remaining icons for this page
        mods = cls._page_mod_data.get(source, {}).get(page, [])
        if mods and current_index + 1 < len(mods) and current_count + 1 < target_count:
            QTimer.singleShot(20, lambda: cls._preload_icons_for_page_static(
                source, page, target_count, current_index + 1))
    
    @staticmethod
    def _thread_is_running_static(t) -> bool:
        """Return True if QThread is running, without crashing on deleted C++ objects."""
        try:
            return t is not None and t.isRunning()
        except RuntimeError:
            return False
    
    def __init__(self, existing_ids: List[str], current_version: str = "1.0.0", parent=None):
        super().__init__(parent)
        self.existing_ids = existing_ids
        self.current_version = current_version
        self.search_thread: Optional[ModSearchThread] = None
        self.version_thread: Optional[ModVersionFetchThread] = None
        self.description_thread: Optional[ModDescriptionFetchThread] = None  # Thread for fetching description
        self.icon_threads: List[QThread] = []  # Track icon loading threads for current page
        self.selected_mod = None
        self.selected_version = None
        self.all_search_results = []  # Store all results for current search
        
        # Pagination state (per source)
        self.current_page = 0  # Current page index (0-based)
        self.total_results = 0  # Total results from API (if available)
        self.has_more_results = True  # Whether more results exist on the server
        self._is_loading_page = False  # Whether a page is currently loading
        
        # Track page state per source
        self._source_page_state = {
            'curseforge': {'page': 0, 'total': 0, 'has_more': True},
            'modrinth': {'page': 0, 'total': 0, 'has_more': True}
        }
        
        # Local reference to shared cache for easier access
        self._icon_cache = ModBrowserDialog._shared_icon_cache
        
        # Loading state tracking (use class-level for source isolation)
        self._max_concurrent_loads = ICON_MAX_CONCURRENT_LOADS

        # Debounce timer for scroll events
        self._scroll_debounce_timer = None

        # Background preload state
        self._preload_source = None
        self._preload_thread = None

        self.search_in_progress = False

        self.setup_ui()
        # Load popular mods on startup - icons should already be preloaded
        QTimer.singleShot(100, self._initialize_with_preloaded_data)

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
        self.search_edit.setPlaceholderText("🔍 Search mods... (leave empty for most popular)")
        self.search_edit.returnPressed.connect(self.search_mods)
        search_layout.addWidget(self.search_edit, 1)

        version_lbl = QLabel("MC Version:")
        version_lbl.setStyleSheet("margin:0; padding:0;")
        search_layout.addWidget(version_lbl)

        self.version_filter = QLineEdit()
        self.version_filter.setPlaceholderText("e.g., 1.12.2")
        self.version_filter.setFixedWidth(100)
        self.version_filter.returnPressed.connect(self.search_mods)
        search_layout.addWidget(self.version_filter)

        search_btn = QPushButton("Search")
        search_btn.setObjectName("primaryButton")
        search_btn.clicked.connect(self.search_mods)
        search_layout.addWidget(search_btn)

        layout.addLayout(search_layout)

        # Splitter for results / description
        splitter = QSplitter(Qt.Orientation.Horizontal)

        # Left panel (results)
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(0, 0, 0, 0)
        left_layout.setSpacing(4)

        self.results_header = QLabel("📋 Popular Mods (sorted by downloads):")
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

        # Pagination controls - improved layout and styling
        pagination_container = QWidget()
        pagination_container.setStyleSheet(f"""
            QWidget {{
                background-color: {theme['bg_tertiary']};
                border-radius: 8px;
                padding: 4px;
            }}
        """)
        pagination_layout = QHBoxLayout(pagination_container)
        pagination_layout.setSpacing(8)
        pagination_layout.setContentsMargins(8, 6, 8, 6)

        # First page button
        self.first_page_btn = QPushButton("⏮")
        self.first_page_btn.setFixedSize(36, 32)
        self.first_page_btn.setToolTip("Go to first page")
        self.first_page_btn.clicked.connect(self._go_to_first_page)
        pagination_layout.addWidget(self.first_page_btn)

        # Previous page button
        self.prev_page_btn = QPushButton("◀")
        self.prev_page_btn.setFixedSize(36, 32)
        self.prev_page_btn.setToolTip("Go to previous page")
        self.prev_page_btn.clicked.connect(self._go_to_prev_page)
        pagination_layout.addWidget(self.prev_page_btn)

        pagination_layout.addStretch()

        # Page indicator/selector - styled as a group
        page_select_widget = QWidget()
        page_select_widget.setStyleSheet(f"""
            QWidget {{
                background-color: {theme['bg_secondary']};
                border-radius: 6px;
                padding: 2px 4px;
            }}
        """)
        page_select_layout = QHBoxLayout(page_select_widget)
        page_select_layout.setSpacing(6)
        page_select_layout.setContentsMargins(8, 2, 8, 2)

        page_label = QLabel("Page")
        page_label.setStyleSheet(f"font-weight: 600; font-size: 12px; color: {theme['text_secondary']};")
        page_select_layout.addWidget(page_label)

        self.page_spin = QSpinBox()
        self.page_spin.setMinimum(1)
        self.page_spin.setMaximum(1)
        self.page_spin.setValue(1)
        self.page_spin.setFixedWidth(60)
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
        page_select_layout.addWidget(self.page_spin)

        self.page_total_label = QLabel("of 1")
        self.page_total_label.setStyleSheet(f"color: {theme['text_secondary']}; font-size: 12px;")
        page_select_layout.addWidget(self.page_total_label)

        pagination_layout.addWidget(page_select_widget)

        pagination_layout.addStretch()

        # Next page button
        self.next_page_btn = QPushButton("▶")
        self.next_page_btn.setFixedSize(36, 32)
        self.next_page_btn.setToolTip("Go to next page")
        self.next_page_btn.clicked.connect(self._go_to_next_page)
        pagination_layout.addWidget(self.next_page_btn)

        # Last page button
        self.last_page_btn = QPushButton("⏭")
        self.last_page_btn.setFixedSize(36, 32)
        self.last_page_btn.setToolTip("Go to last page")
        self.last_page_btn.clicked.connect(self._go_to_last_page)
        pagination_layout.addWidget(self.last_page_btn)

        left_layout.addWidget(pagination_container)

        # Style pagination buttons - modern compact design
        pagination_btn_style = f"""
            QPushButton {{
                background-color: {theme['bg_secondary']};
                border: none;
                border-radius: 6px;
                padding: 4px;
                font-size: 14px;
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
        self.versions_combo.setMaxVisibleItems(10)
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
        """Load icons for currently visible items (simplified approach).

        This replaces the complex queue-based system with a simpler approach:
        1. Find visible items
        2. Check cache first
        3. Start loading for items not in cache (up to concurrency limit)
        """
        if self.results_list.count() == 0:
            return

        first_visible, last_visible = self._get_visible_range()
        source = self._get_selected_source()
        loading_ids = self._get_loading_mod_ids(source)

        # Calculate buffer zone
        visible_count = last_visible - first_visible + 1
        buffer_size = min(visible_count, 4)  # Small buffer
        load_start = max(0, first_visible - buffer_size)
        load_end = min(self.results_list.count() - 1, last_visible + buffer_size)

        # Count active threads safely
        active_count = sum(1 for t in self.icon_threads if self._thread_is_running(t))

        # Process items in visible range first, then buffer
        items_to_load = []

        # Visible items first
        for i in range(first_visible, last_visible + 1):
            if i >= self.results_list.count():
                break
            items_to_load.append(i)

        # Then buffer items
        for i in list(range(load_start, first_visible)) + list(range(last_visible + 1, load_end + 1)):
            if 0 <= i < self.results_list.count():
                items_to_load.append(i)

        for i in items_to_load:
            if active_count >= self._max_concurrent_loads:
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
            if mod_id in loading_ids:
                continue

            # Check cache
            if source in self._icon_cache and mod_id in self._icon_cache[source]:
                self._apply_icon_to_item(item, self._icon_cache[source][mod_id])
                continue

            # Start loading - pass item index for visibility tracking
            self._start_icon_load(item, mod_id, icon_url, source, item_idx=i)
            active_count += 1

    def _start_icon_load(self, item: QListWidgetItem, mod_id: str, icon_url: str, source: str, item_idx: int = -1):
        """Start loading an icon in a background thread.

        Args:
            item: The QListWidgetItem to update with the icon
            mod_id: The mod identifier for caching
            icon_url: URL to fetch the icon from
            source: Source platform (curseforge/modrinth)
            item_idx: Index of the item in the list (for visibility tracking)
        """
        self._add_loading_mod_id(source, mod_id)

        # Set placeholder icon immediately
        item.setIcon(get_placeholder_icon())

        try:
            thread = SimpleIconFetcher(mod_id, icon_url, source)
            thread.icon_fetched.connect(self._on_simple_icon_fetched)
            thread.finished_loading.connect(lambda mid=mod_id, s=source: self._on_icon_load_complete(mid, s))
            thread.finished.connect(thread.deleteLater)
            # Store item reference and index for callback and cancellation
            thread.item = item
            thread.item_idx = item_idx
            self.icon_threads.append(thread)
            thread.start()
        except Exception:
            self._remove_loading_mod_id(source, mod_id)

    def _on_simple_icon_fetched(self, mod_id: str, source: str, data: bytes):
        """Handle icon fetched from background thread."""
        # Cache the icon in the shared cache
        ModBrowserDialog._shared_icon_cache[source][mod_id] = data

        # Find and update the item (sender may be deleted, so use try-except)
        try:
            thread = self.sender()
            if thread and hasattr(thread, 'item') and thread.item:
                self._apply_icon_to_item(thread.item, data)
        except RuntimeError:
            # Handle case where Qt C++ object was deleted
            pass

    def _on_icon_load_complete(self, mod_id: str, source: str):
        """Handle when an icon load completes (success or failure)."""
        self._remove_loading_mod_id(source, mod_id)

        # Clean up finished threads (safely)
        self.icon_threads = [t for t in self.icon_threads if self._thread_is_running(t)]

        # Only trigger loading more icons if we have few active loads
        active_count = len(self.icon_threads)
        cascade_threshold = self._max_concurrent_loads // ICON_CASCADE_THRESHOLD_DIVISOR
        if active_count < cascade_threshold:
            # Load more icons after a short delay, but only if not scrolling
            if self._scroll_debounce_timer and not self._scroll_debounce_timer.isActive():
                QTimer.singleShot(ICON_LOAD_DEBOUNCE_MS, self._load_visible_icons)

    # Legacy method for backward compatibility
    def _update_visible_icons(self):
        """Legacy method - redirects to new implementation."""
        self._load_visible_icons()

    def _get_selected_source(self) -> str:
        """Get the currently selected source."""
        return 'curseforge' if self.curseforge_source_btn.isChecked() else 'modrinth'

    def _select_source(self, source: str):
        """Select a source and update UI."""
        self.curseforge_source_btn.setChecked(source == 'curseforge')
        self.modrinth_source_btn.setChecked(source == 'modrinth')
        self._update_source_button_styles()
        self.on_source_changed()

    def _update_source_button_styles(self):
        """Update source button styles to show selected state."""
        # Use theme colors directly for proper theming
        theme = get_current_theme()
        selected_style = f"background-color: {theme['accent']}; border: 2px solid {theme['accent']}; font-weight: bold; color: {theme['bg_primary']};"
        normal_style = ""

        self.curseforge_source_btn.setStyleSheet(selected_style if self.curseforge_source_btn.isChecked() else normal_style)
        self.modrinth_source_btn.setStyleSheet(selected_style if self.modrinth_source_btn.isChecked() else normal_style)

    # Pagination methods
    def _update_pagination_controls(self):
        """Update pagination controls based on current state."""
        # Calculate total pages based on total_results if available
        total_pages = self._estimate_total_pages()

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
        self.next_page_btn.setEnabled(self.has_more_results or self.current_page < total_pages - 1)
        # Last button is enabled when we know the total results from API
        # This allows navigation to the last page even when we haven't loaded all pages
        has_known_total = self.total_results > 0
        self.last_page_btn.setEnabled(has_known_total and self.current_page < total_pages - 1)

    def _estimate_total_pages(self) -> int:
        """Estimate total number of pages based on current data."""
        if self.total_results > 0:
            return (self.total_results + SEARCH_PAGE_SIZE - 1) // SEARCH_PAGE_SIZE
        elif len(self.all_search_results) > 0:
            # If we have results but no total, estimate from current count
            pages_loaded = self.current_page + 1
            if self.has_more_results:
                return pages_loaded + 1  # At least one more page
            return pages_loaded
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
        """Navigate to a specific page with intelligent preloading.
        
        Implements the new preload strategy:
        - Current page X: Fully loaded
        - Pages X±1: LARGE_LOAD_AMOUNT preloaded
        - Pages X±2: SMALL_LOAD_AMOUNT preloaded
        - Pages outside range: Unloaded
        """
        if self._is_loading_page:
            return

        source = self._get_selected_source()
        old_page = self.current_page
        
        # Save source page state
        self._source_page_state[source] = {
            'page': target_page,
            'total': self.total_results,
            'has_more': self.has_more_results
        }

        self.current_page = target_page
        
        # Check if we have preloaded data for the target page
        if source in ModBrowserDialog._page_mod_data and target_page in ModBrowserDialog._page_mod_data[source]:
            results = ModBrowserDialog._page_mod_data[source][target_page]
            if results:
                # Use preloaded data
                self._display_preloaded_page(results, source, target_page)
                # Trigger preloading for pages around this one
                QTimer.singleShot(ICON_PRELOAD_DELAY_MS, lambda: self._preload_around_page(source, target_page))
                return
        
        # Load the page fresh
        self._load_page(target_page)
        
        # Trigger preloading for pages around this one after a delay
        QTimer.singleShot(ICON_PRELOAD_DELAY_MS, lambda: self._preload_around_page(source, target_page))

    def _cache_current_page_icons(self):
        """Cache icons from current page - now handled by the shared cache system."""
        # The new system uses ModBrowserDialog._shared_icon_cache which persists
        # across page navigations, so explicit caching is no longer needed
        pass

    def _load_page(self, page: int):
        """Load a specific page of results."""
        if self._is_loading_page:
            return

        self._is_loading_page = True

        # Cancel any pending icon loads
        self._cancel_all_icon_loads()

        source = self._get_selected_source()
        query = self.search_edit.text().strip()
        version_filter = self.version_filter.text().strip()

        # Clear current results
        self.results_list.clear()
        self.selected_mod = None
        self.selected_version = None
        self.versions_combo.clear()
        self.add_btn.setEnabled(False)

        self.search_status.setText(f"Loading page {page + 1}...")

        # Stop any running search thread (safely)
        if self.search_thread:
            try:
                if self._thread_is_running(self.search_thread):
                    self.search_thread.stop()
                    self.search_thread.wait()
            except Exception:
                pass
            # Always drop the reference; the object may be deleted by Qt
            self.search_thread = None
            self.search_in_progress = False

        # Create search thread with offset for the target page
        self.search_thread = ModSearchThread(source, query, version_filter)
        self.search_thread.offset = page * SEARCH_PAGE_SIZE
        # Track running state without calling into the thread later
        self.search_thread.started.connect(lambda: setattr(self, "search_in_progress", True))
        self.search_thread.search_complete.connect(self._on_page_loaded)
        self.search_thread.error_occurred.connect(self._on_page_load_error)
        self.search_thread.finished.connect(self._on_search_thread_finished)
        self.search_thread.finished.connect(self.search_thread.deleteLater)
        self.search_thread.start()

    def _on_page_loaded(self, results: list, total_count: int = 0):
        """Handle page load completion."""
        self._is_loading_page = False
        source = self._get_selected_source()

        # Store total results from API
        if total_count > 0:
            self.total_results = total_count
            # Update source page state
            state = self._source_page_state.get(source, {})
            state['total'] = total_count
            self._source_page_state[source] = state

        # Check if there are more results
        self.has_more_results = len(results) >= SEARCH_PAGE_SIZE

        # Store results in shared page data cache
        if source not in ModBrowserDialog._page_mod_data:
            ModBrowserDialog._page_mod_data[source] = {}
        ModBrowserDialog._page_mod_data[source][self.current_page] = results
        
        # Store results locally
        self.all_search_results = results

        # Display results with icons
        self._display_page_results(results, source)

        # Update pagination
        self._update_pagination_controls()

        # Update status
        page_start = self.current_page * SEARCH_PAGE_SIZE + 1
        page_end = page_start + len(results) - 1
        status_text = f"Showing {page_start}-{page_end}"
        if self.total_results > 0:
            status_text += f" of {self.total_results}"
        self.search_status.setText(status_text)

        # Load remaining icons (beyond what was preloaded) and trigger preloading for nearby pages
        QTimer.singleShot(ICON_LOAD_INITIAL_DELAY_MS, lambda: self._load_remaining_icons_for_page(source))
        QTimer.singleShot(ICON_PRELOAD_DELAY_MS, lambda: self._preload_around_page(source, self.current_page))

    def _on_page_load_error(self, error: str):
        """Handle page load error."""
        self._is_loading_page = False
        self.search_status.setText(f"Error: {error}")
        self._update_pagination_controls()

    def _display_page_results(self, results: list, source: str):
        """Display page results with cached icons applied immediately."""
        self.results_list.clear()

        for i, mod in enumerate(results):
            item = QListWidgetItem()
            item.setText(f"{mod['name']}\nby {mod['author']} • {mod['downloads']:,} downloads")
            item.setData(Qt.ItemDataRole.UserRole, mod)

            # Check if icon is already cached
            mod_id = mod.get('id', mod.get('slug', ''))
            if source in self._icon_cache and mod_id in self._icon_cache[source]:
                # Apply cached icon immediately
                self._apply_icon_to_item(item, self._icon_cache[source][mod_id])
            else:
                # Set placeholder icon
                item.setIcon(get_placeholder_icon())

            self.results_list.addItem(item)

    def _load_page_icons_sequential(self):
        """Legacy method - redirects to new implementation."""
        source = self._get_selected_source()
        self._load_remaining_icons_for_page(source)

    def _cancel_all_icon_loads(self):
        """Cancel all pending icon load threads and wait for them to finish."""
        for thread in list(self.icon_threads):
            try:
                if self._thread_is_running(thread):
                    thread.stop()
                    thread.wait(500)  # Wait up to 500ms for each thread
            except Exception:
                pass
        self.icon_threads.clear()
        # Note: We don't clear the shared loading state as it's per-source

    def _load_mod_icon(self, item: QListWidgetItem, icon_url: str):
        """Queue mod icon for lazy loading (legacy method - redirects to new system)."""
        source = self._get_selected_source()
        mod = item.data(Qt.ItemDataRole.UserRole) if item else None
        mod_id = mod.get('id', mod.get('slug', '')) if mod else None
        loading_ids = self._get_loading_mod_ids(source)

        if mod_id and mod_id not in loading_ids:
            self._start_icon_load(item, mod_id, icon_url, source)

    def _apply_icon_to_item(self, item: QListWidgetItem, data: bytes):
        """Apply icon data to a list item."""
        try:
            pixmap = QPixmap()
            if pixmap.loadFromData(data):
                icon = QIcon(pixmap)
                item.setIcon(icon)
        except:
            pass

    def _on_icon_fetched(self, item: QListWidgetItem, data: bytes):
        """Handle icon fetched from background thread - called on main thread (legacy support)."""
        source = self._get_selected_source()
        mod = item.data(Qt.ItemDataRole.UserRole) if item else None
        mod_id = mod.get('id', mod.get('slug', '')) if mod else None

        # Cache by mod_id for reliable caching
        if mod_id:
            if source not in self._icon_cache:
                self._icon_cache[source] = {}
            self._icon_cache[source][mod_id] = data

        # Apply icon to item
        self._apply_icon_to_item(item, data)

    def _initialize_with_preloaded_data(self):
        """Initialize dialog using preloaded data if available."""
        source = self._get_selected_source()
        
        # Check if we have preloaded page data
        if source in ModBrowserDialog._page_mod_data and 0 in ModBrowserDialog._page_mod_data[source]:
            # We have preloaded data for page 0, use it
            results = ModBrowserDialog._page_mod_data[source][0]
            if results:
                self._display_preloaded_page(results, source, 0)
                return
        
        # Fallback to loading data fresh
        self.load_popular_mods()
    
    def _display_preloaded_page(self, results: list, source: str, page: int):
        """Display a page with preloaded icons."""
        self.all_search_results = results
        self.current_page = page
        self.has_more_results = len(results) >= SEARCH_PAGE_SIZE
        
        # Display results with cached icons
        self._display_page_results(results, source)
        
        # Update pagination
        self._update_pagination_controls()
        
        # Update status
        page_start = page * SEARCH_PAGE_SIZE + 1
        page_end = page_start + len(results) - 1
        status_text = f"Showing {page_start}-{page_end}"
        if self.total_results > 0:
            status_text += f" of {self.total_results}"
        self.search_status.setText(status_text)
        
        # Load any remaining icons that weren't preloaded
        self._load_remaining_icons_for_page(source)
    
    def _get_loading_mod_ids(self, source: str) -> set:
        """Get the set of mod_ids currently loading for a source."""
        return ModBrowserDialog._loading_mod_ids_per_source.get(source, set())
    
    def _add_loading_mod_id(self, source: str, mod_id: str):
        """Mark a mod_id as loading for a source."""
        if source not in ModBrowserDialog._loading_mod_ids_per_source:
            ModBrowserDialog._loading_mod_ids_per_source[source] = set()
        ModBrowserDialog._loading_mod_ids_per_source[source].add(mod_id)
    
    def _remove_loading_mod_id(self, source: str, mod_id: str):
        """Mark a mod_id as no longer loading for a source."""
        if source in ModBrowserDialog._loading_mod_ids_per_source:
            ModBrowserDialog._loading_mod_ids_per_source[source].discard(mod_id)
    
    def _load_remaining_icons_for_page(self, source: str):
        """Load icons for items on current page that weren't preloaded."""
        if self.results_list.count() == 0:
            return
        
        icons_cache = self._icon_cache.get(source, {})
        loading_ids = self._get_loading_mod_ids(source)
        
        for i in range(self.results_list.count()):
            item = self.results_list.item(i)
            if not item:
                continue
            
            mod = item.data(Qt.ItemDataRole.UserRole)
            if not mod:
                continue
            
            mod_id = mod.get('id', mod.get('slug', ''))
            icon_url = mod.get('icon_url', '')
            
            if not mod_id or not icon_url:
                continue
            
            # Skip if already cached
            if mod_id in icons_cache:
                self._apply_icon_to_item(item, icons_cache[mod_id])
                continue
            
            # Skip if already loading
            if mod_id in loading_ids:
                continue
            
            # Check concurrent load limit
            active_count = sum(1 for t in self.icon_threads if self._thread_is_running(t))
            if active_count >= self._max_concurrent_loads:
                # Schedule retry
                QTimer.singleShot(ICON_LOAD_RETRY_DELAY_MS, lambda s=source: self._load_remaining_icons_for_page(s))
                return
            
            # Start loading this icon
            self._start_icon_load(item, mod_id, icon_url, source, item_idx=i)
    
    def _on_background_icon_fetched(self, mod_id: str, source: str, data: bytes):
        """Handle background icon fetch completion - store in shared cache."""
        ModBrowserDialog._shared_icon_cache[source][mod_id] = data
    
    def _on_preload_icon_complete(self, mod_id: str, source: str, page: int, target_count: int, current_index: int):
        """Handle completion of a preload icon fetch."""
        self._remove_loading_mod_id(source, mod_id)
        
        # Update preload state
        if source not in ModBrowserDialog._page_preload_state:
            ModBrowserDialog._page_preload_state[source] = {}
        current_count = ModBrowserDialog._page_preload_state[source].get(page, 0)
        ModBrowserDialog._page_preload_state[source][page] = current_count + 1
        
        # Clean up threads
        self._cleanup_finished_threads(source)
        
        # Continue preloading remaining icons for this page
        mods = ModBrowserDialog._page_mod_data.get(source, {}).get(page, [])
        if mods and current_index + 1 < len(mods) and current_count + 1 < target_count:
            QTimer.singleShot(20, lambda: self._preload_page_icons_batch(
                source, page, target_count, current_index + 1))
    
    def _cleanup_finished_threads(self, source: str):
        """Clean up finished threads for a source."""
        if source in ModBrowserDialog._preload_threads:
            ModBrowserDialog._preload_threads[source] = [
                t for t in ModBrowserDialog._preload_threads[source] 
                if self._thread_is_running(t)
            ]
        self.icon_threads = [t for t in self.icon_threads if self._thread_is_running(t)]
    
    def _preload_page_icons_batch(self, source: str, page: int, target_count: int, start_index: int = 0):
        """Preload icons for a page up to target_count.
        
        Args:
            source: 'curseforge' or 'modrinth'
            page: Page number (0-based)
            target_count: Number of icons to preload (LARGE_LOAD_AMOUNT or SMALL_LOAD_AMOUNT)
            start_index: Index to start loading from
        """
        mods = ModBrowserDialog._page_mod_data.get(source, {}).get(page, [])
        if not mods:
            return
        
        icons_cache = ModBrowserDialog._shared_icon_cache.get(source, {})
        loading_ids = self._get_loading_mod_ids(source)
        current_preload_count = ModBrowserDialog._page_preload_state.get(source, {}).get(page, 0)
        
        # Check if we've reached target
        if current_preload_count >= target_count:
            return
        
        icons_started = 0
        
        for i in range(start_index, min(len(mods), target_count)):
            mod = mods[i]
            mod_id = mod.get('id', mod.get('slug', ''))
            icon_url = mod.get('icon_url', '')
            
            if not mod_id or not icon_url:
                continue
            
            # Skip if already cached
            if mod_id in icons_cache:
                continue
            
            # Skip if already loading
            if mod_id in loading_ids:
                continue
            
            # Check concurrent load limit
            active_threads = ModBrowserDialog._preload_threads.get(source, [])
            active_count = sum(1 for t in active_threads if self._thread_is_running(t))
            
            if active_count >= self._max_concurrent_loads:
                # Schedule retry
                QTimer.singleShot(100, lambda s=source, p=page, tc=target_count, si=i: 
                    self._preload_page_icons_batch(s, p, tc, si))
                return
            
            # Start loading this icon
            self._add_loading_mod_id(source, mod_id)
            try:
                thread = SimpleIconFetcher(mod_id, icon_url, source)
                thread.icon_fetched.connect(self._on_background_icon_fetched)
                thread.finished_loading.connect(
                    lambda mid=mod_id, s=source, p=page, tc=target_count, ci=i:
                        self._on_preload_icon_complete(mid, s, p, tc, ci))
                thread.finished.connect(thread.deleteLater)
                
                if source not in ModBrowserDialog._preload_threads:
                    ModBrowserDialog._preload_threads[source] = []
                ModBrowserDialog._preload_threads[source].append(thread)
                thread.start()
                icons_started += 1
            except Exception:
                self._remove_loading_mod_id(source, mod_id)
            
            # Allow multiple parallel loads up to the limit
            if icons_started >= self._max_concurrent_loads:
                return
    
    def _on_page_data_fetched(self, results: list, total_count: int, source: str, page: int, preload_amount: int):
        """Handle fetched page data for preloading."""
        # Store the mod data for this page
        if source not in ModBrowserDialog._page_mod_data:
            ModBrowserDialog._page_mod_data[source] = {}
        ModBrowserDialog._page_mod_data[source][page] = results
        
        # Store total count
        if total_count > 0:
            state = self._source_page_state.get(source, {})
            state['total'] = total_count
            self._source_page_state[source] = state
        
        # Start preloading icons for this page
        self._preload_page_icons_batch(source, page, preload_amount, 0)
    
    def _fetch_page_for_preload(self, source: str, page: int, preload_amount: int):
        """Fetch page data and preload icons."""
        # Check if we already have data for this page
        if source in ModBrowserDialog._page_mod_data and page in ModBrowserDialog._page_mod_data[source]:
            # Already have data, just preload icons
            self._preload_page_icons_batch(source, page, preload_amount, 0)
            return
        
        # Fetch page data
        thread = ModSearchThread(source, "", "")
        thread.offset = page * SEARCH_PAGE_SIZE
        thread.search_complete.connect(
            lambda results, total, s=source, p=page, pa=preload_amount:
                self._on_page_data_fetched(results, total, s, p, pa))
        thread.finished.connect(thread.deleteLater)
        
        if source not in ModBrowserDialog._preload_threads:
            ModBrowserDialog._preload_threads[source] = []
        ModBrowserDialog._preload_threads[source].append(thread)
        thread.start()
    
    def _preload_around_page(self, source: str, current_page: int):
        """Preload icons for pages around the current page based on configured ranges.
        
        This implements the new preload strategy:
        - Current page: already loaded fully
        - Pages within MAX_LARGE_PROFILE_RANGE: preload LARGE_LOAD_AMOUNT icons
        - Pages within MAX_SMALL_PROFILE_RANGE (beyond large range): preload SMALL_LOAD_AMOUNT icons
        - Pages outside the range: unload from cache
        """
        # Calculate page ranges
        total_range = MAX_LARGE_PROFILE_RANGE + MAX_SMALL_PROFILE_RANGE
        
        # Preload large range (pages immediately before/after)
        for offset in range(1, MAX_LARGE_PROFILE_RANGE + 1):
            for page in [current_page - offset, current_page + offset]:
                if page >= 0:
                    self._fetch_page_for_preload(source, page, LARGE_LOAD_AMOUNT)
        
        # Preload small range (pages further out)
        for offset in range(MAX_LARGE_PROFILE_RANGE + 1, total_range + 1):
            for page in [current_page - offset, current_page + offset]:
                if page >= 0:
                    self._fetch_page_for_preload(source, page, SMALL_LOAD_AMOUNT)
        
        # Unload pages outside the range (to save memory)
        self._unload_pages_outside_range(source, current_page, total_range)
    
    def _unload_pages_outside_range(self, source: str, current_page: int, total_range: int):
        """Unload mod data and icons for pages outside the configured range."""
        if source not in ModBrowserDialog._page_mod_data:
            return
        
        pages_to_keep = set()
        for offset in range(-total_range, total_range + 1):
            page = current_page + offset
            if page >= 0:
                pages_to_keep.add(page)
        
        # Remove pages outside range
        pages_to_remove = [p for p in ModBrowserDialog._page_mod_data[source].keys() 
                          if p not in pages_to_keep]
        
        for page in pages_to_remove:
            # Get mod IDs for this page before removing
            mods = ModBrowserDialog._page_mod_data[source].get(page, [])
            mod_ids_to_check = [m.get('id', m.get('slug', '')) for m in mods]
            
            # Remove page data
            del ModBrowserDialog._page_mod_data[source][page]
            
            # Remove from preload state
            if source in ModBrowserDialog._page_preload_state:
                ModBrowserDialog._page_preload_state[source].pop(page, None)
            
            # Only remove icons if they're not used by other cached pages
            all_cached_mod_ids = set()
            for p, page_mods in ModBrowserDialog._page_mod_data.get(source, {}).items():
                for m in page_mods:
                    all_cached_mod_ids.add(m.get('id', m.get('slug', '')))
            
            for mod_id in mod_ids_to_check:
                if mod_id and mod_id not in all_cached_mod_ids:
                    ModBrowserDialog._shared_icon_cache[source].pop(mod_id, None)

    def load_popular_mods(self):
        """Load popular mods without search query - using pagination."""
        # Reset pagination state
        self.current_page = 0
        self.total_results = 0
        self.has_more_results = True
        self.all_search_results = []

        # Cancel any pending icon loads for this dialog instance
        self._cancel_all_icon_loads()

        self.results_list.clear()
        self.versions_combo.clear()
        self.selected_mod = None
        self.selected_version = None
        self.add_btn.setEnabled(False)
        self.search_status.setText("Loading mods...")
        self.results_header.setText(f"📋 Popular Mods (sorted by downloads):")

        # Update pagination controls
        self._update_pagination_controls()

        if self.search_thread:
            try:
                if self._thread_is_running(self.search_thread):
                    self.search_thread.stop()
                    self.search_thread.wait()
            except Exception:
                pass
            self.search_thread = None
            self.search_in_progress = False

        source = self._get_selected_source()

        # Check if we have preloaded data for page 0
        if source in ModBrowserDialog._page_mod_data and 0 in ModBrowserDialog._page_mod_data[source]:
            results = ModBrowserDialog._page_mod_data[source][0]
            if results:
                state = self._source_page_state.get(source, {})
                total_count = state.get('total', 0)
                self._on_page_loaded(results, total_count)
                return

        # Load first page
        self._load_page(0)

    def on_source_changed(self):
        """Handle source tab change with improved icon caching.

        When switching tabs:
        - Preserves icon cache for both sources (they're independent)
        - Applies cached icons immediately for better UX
        - Only loads icons that aren't already cached
        - Does NOT affect the other source's preloads
        """
        source = self._get_selected_source()
        
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

        # Update pagination controls
        self._update_pagination_controls()

        # Reload mods for new source (will use preloaded data if available)
        self.load_popular_mods()

    def search_mods(self):
        """Search for mods on the selected platform using pagination."""
        query = self.search_edit.text().strip()

        # Reset pagination state
        self.current_page = 0
        self.total_results = 0
        self.has_more_results = True
        self.all_search_results = []

        # Cancel any pending icon loads
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
            self.results_header.setText(f"📋 Popular Mods (sorted by downloads):")

        # Update pagination controls
        self._update_pagination_controls()

        if self.search_thread:
            try:
                if self._thread_is_running(self.search_thread):
                    self.search_thread.stop()
                    self.search_thread.wait()
            except Exception:
                pass
            self.search_thread = None
            self.search_in_progress = False

        # Load first page with current query
        self._load_page(0)

    def on_search_complete(self, results: list, total_count: int = 0):
        """Handle search results with intelligent icon caching (legacy compatibility)."""
        # Redirect to page loaded handler
        self._on_page_loaded(results, total_count)

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

        game_version = self.version_filter.text().strip()
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
                self.search_thread.wait(1000)  # Wait up to 1 second
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

        # Stop preload thread
        if hasattr(self, '_preload_thread') and self._preload_thread:
            try:
                if self._thread_is_running(self._preload_thread):
                    self._preload_thread.stop()
                    self._preload_thread.wait(1000)
            except Exception:
                pass
            finally:
                self._preload_thread = None

        # Stop preload page threads
        if hasattr(self, '_preload_page_threads'):
            for thread in self._preload_page_threads:
                try:
                    if self._thread_is_running(thread):
                        thread.stop()
                        thread.wait(500)
                except Exception:
                    pass
            self._preload_page_threads.clear()

        # Stop icon threads
        for thread in self.icon_threads:
            try:
                if self._thread_is_running(thread):
                    thread.stop()
                    thread.wait(100)  # Brief wait per thread
            except Exception:
                pass
        self.icon_threads.clear()

        # Shutdown description browser image threads
        if hasattr(self, 'description_browser'):
            try:
                self.description_browser.shutdown()
            except Exception:
                pass

        # Clear loading state (not per-source since we don't know current source at shutdown)
        for source_set in ModBrowserDialog._loading_mod_ids_per_source.values():
            source_set.clear()

    def _thread_is_running(self, t) -> bool:
        """Return True if QThread is running, without crashing on deleted C++ objects."""
        try:
            return t is not None and t.isRunning()
        except RuntimeError:
            return False

    def _on_search_thread_finished(self):
        """Reset state when the search thread finishes or is destroyed."""
        self.search_in_progress = False
        self.search_thread = None

    def _on_version_thread_finished(self):
        """Reset state when the version thread finishes or is destroyed."""
        self.version_thread = None

    def _on_description_thread_finished(self):
        """Reset state when the description thread finishes or is destroyed."""
        self.description_thread = None

    def _on_page_preload_complete(self, results: list, page: int, source: str):
        """Handle background preload results for a specific page without touching the UI.
        
        This is now integrated into the new preloading system.
        """
        if not results:
            return

        # Store page data
        if source not in ModBrowserDialog._page_mod_data:
            ModBrowserDialog._page_mod_data[source] = {}
        ModBrowserDialog._page_mod_data[source][page] = results

        # Preload icons for this page based on the preload amount
        # Determine preload amount based on distance from current page
        current_page = self.current_page
        distance = abs(page - current_page)
        
        if distance <= MAX_LARGE_PROFILE_RANGE:
            preload_amount = LARGE_LOAD_AMOUNT
        else:
            preload_amount = SMALL_LOAD_AMOUNT
        
        self._preload_page_icons_batch(source, page, preload_amount, 0)


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

        # Load icon preview
        self._update_icon_preview()

        # If mod has icon URL but no icon data, try to fetch it
        if hasattr(mod, '_icon_url') and mod._icon_url and not mod._icon_data:
            self.fetch_source_icon()

        self.blockSignals(False)

        # Auto-fill hash if from curseforge/modrinth and no hash is set
        # Use short delay to allow UI to update first before starting the hash calculation
        if (source_type in ['curseforge', 'modrinth']) and not mod.hash:
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

        self.display_name_edit = QLineEdit()
        self.display_name_edit.setPlaceholderText("Display name")
        info_layout.addRow("Display Name:", self.display_name_edit)

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
        self.display_name_edit.setText(file_entry.display_name)
        self.file_name_edit.setText(file_entry.file_name)
        self.url_edit.setText(file_entry.url)
        self.download_path_edit.setText(file_entry.download_path or 'config/')
        self.hash_edit.setText(file_entry.hash)
        self.overwrite_check.setChecked(file_entry.overwrite)
        self.extract_check.setChecked(file_entry.extract)

    def save_changes(self):
        if not self.current_file:
            return
        self.current_file.display_name = self.display_name_edit.text().strip()
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

        # Version icon
        icon_group = QGroupBox("Version Icon (Optional)")
        icon_layout = QHBoxLayout(icon_group)

        theme = get_current_theme()
        self.version_icon_preview = QLabel()
        self.version_icon_preview.setFixedSize(64, 64)
        self.version_icon_preview.setStyleSheet(f"border: 2px dashed {theme['border']}; border-radius: 8px;")
        self.version_icon_preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.version_icon_preview.setText("No Icon")
        icon_layout.addWidget(self.version_icon_preview)

        icon_btn_layout = QVBoxLayout()
        select_icon_btn = QPushButton("Select Icon...")
        select_icon_btn.clicked.connect(self.select_version_icon)
        icon_btn_layout.addWidget(select_icon_btn)
        clear_icon_btn = QPushButton("Clear")
        clear_icon_btn.clicked.connect(self.clear_version_icon)
        icon_btn_layout.addWidget(clear_icon_btn)
        icon_btn_layout.addStretch()
        icon_layout.addLayout(icon_btn_layout)
        icon_layout.addStretch()

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
            card = ItemCard(file.display_name or file.file_name, file.icon_path)
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
            self.version_modified.emit()

    def clear_version_icon(self):
        if not self.version_config:
            return
        self.version_config.icon_path = ""
        self.version_icon_preview.clear()
        self.version_icon_preview.setText("No Icon")
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
            # Remove version from local storage
            if version in self.versions:
                del self.versions[version]
                self.refresh_grid()
                self.version_deleted.emit(version)
                # Inform user that they need to save changes to persist the deletion
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
        
        # Theme Selection
        theme_group = QGroupBox("Theme")
        theme_layout = QFormLayout(theme_group)
        
        self.theme_combo = QComboBox()
        for key, theme in THEMES.items():
            self.theme_combo.addItem(theme['name'], key)
        self.theme_combo.currentIndexChanged.connect(self.on_theme_changed)
        theme_layout.addRow("Color Theme:", self.theme_combo)
        
        scroll_layout.addWidget(theme_group)
        
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
    
    def set_theme(self, theme_key: str):
        index = self.theme_combo.findData(theme_key)
        if index >= 0:
            self.theme_combo.setCurrentIndex(index)
    
    def get_theme(self) -> str:
        return self.theme_combo.currentData()
    
    def on_theme_changed(self):
        self.settings_changed.emit()
    
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
        
        # Start preloading mod browser icons in background (runs before user opens dialog)
        # This ensures icons are ready when user opens "Find and Add Mods"
        QTimer.singleShot(500, ModBrowserDialog.start_startup_preload)
        
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
        
        # Settings Page
        self.settings_page = SettingsPage()
        self.settings_page.settings_changed.connect(self.on_settings_changed)
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
                    self.current_theme = self.editor_config.get('theme', 'dark')
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
        dialog = SetupDialog(self, config)
        if dialog.exec():
            github_config = dialog.get_config()
            self.editor_config['repo_url'] = github_config['repo_url']
            self.editor_config['github'] = github_config
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
        self.settings_page.set_theme(theme_key)
        
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
            self.stack.setCurrentWidget(self.settings_page)
    
    def show_version_selection(self):
        """Show the version selection page."""
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
        # Update status or indicator
        pass
    
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
        
        # Show progress
        progress = QProgressDialog(f"Creating version {version}...", "Cancel", 0, len(changes), self)
        progress.setWindowModality(Qt.WindowModality.WindowModal)
        progress.setMinimumDuration(0)
        
        errors = []
        
        for i, (path, content, sha) in enumerate(changes):
            progress.setValue(i)
            QApplication.processEvents()
            
            if progress.wasCanceled():
                break
            
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
    
    def on_settings_changed(self):
        """Handle settings change."""
        new_theme = self.settings_page.get_theme()
        if new_theme != self.current_theme:
            self.apply_theme(new_theme)
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
        
        # Show progress
        progress = QProgressDialog("Saving to GitHub...", "Cancel", 0, len(changes), self)
        progress.setWindowModality(Qt.WindowModality.WindowModal)
        progress.setMinimumDuration(0)
        
        errors = []
        
        for i, (path, content, sha) in enumerate(changes):
            if progress.wasCanceled():
                break
            
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

    window = MainWindow()
    window.show()

    sys.exit(app.exec() if PYQT_VERSION == 6 else app.exec_())


if __name__ == "__main__":
    main()
