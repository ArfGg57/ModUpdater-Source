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
PAGE_ICON_CACHE_SIZE = 8  # Number of icons to cache per page when navigating away
MAX_CACHED_PAGES = 10  # Maximum number of pages to keep in cache

# Image scaling settings
MAX_DESCRIPTION_IMAGE_WIDTH = 400  # Maximum width for images in mod descriptions

# Icon loading settings
ICON_MAX_CONCURRENT_LOADS = 4  # Maximum number of concurrent icon downloads (reduced for stability)
ICON_LOAD_DEBOUNCE_MS = 50  # Debounce delay for scroll events (ms)
ICON_PRELOAD_DELAY_MS = 300  # Delay before starting background preload (ms)
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
    """Dialog for browsing and selecting mods from CurseForge/Modrinth (like Prism Launcher)."""
    
    def __init__(self, existing_ids: List[str], current_version: str = "1.0.0", parent=None):
        super().__init__(parent)
        self.existing_ids = existing_ids
        self.current_version = current_version
        self.search_thread: Optional[ModSearchThread] = None
        self.version_thread: Optional[ModVersionFetchThread] = None
        self.description_thread: Optional[ModDescriptionFetchThread] = None  # Thread for fetching description
        self.icon_threads: List[QThread] = []  # Track icon loading threads
        self.selected_mod = None
        self.selected_version = None
        self.all_search_results = []  # Store all results for current search
        
        # Pagination state
        self.current_page = 0  # Current page index (0-based)
        self.total_results = 0  # Total results from API (if available)
        self.has_more_results = True  # Whether more results exist on the server
        self._is_loading_page = False  # Whether a page is currently loading
        
        # Simplified icon loading system
        # Global icon cache: source -> {mod_id -> icon_bytes}
        self._icon_cache = {}  # Cache: source -> {mod_id -> icon_bytes}
        
        # Thread-safety helpers
        self._thread_guard_timer = QTimer(self)
        self._thread_guard_timer.setSingleShot(True)

    def _safe_is_running(self, thread: Optional[QThread]) -> bool:
        """Return True if the QThread is alive; guard against deleted C++ wrapper errors."""
        if not thread:
            return False
        try:
            return thread.isRunning()
        except RuntimeError:
            # Wrapped C/C++ object may already be deleted
            return False

    def _stop_thread_safe(self, thread_attr: str, wait_ms: int = 1000):
        """Safely stop and clear a QThread attribute on this object.
        thread_attr: name of attribute holding the thread (e.g., 'search_thread')
        """
        t = getattr(self, thread_attr, None)
        if not t:
            setattr(self, thread_attr, None)
            return
        try:
            if self._safe_is_running(t):
                # cooperative stop if available
                if hasattr(t, 'stop'):
                    try:
                        t.stop()
                    except Exception:
                        pass
                try:
                    t.wait(wait_ms)
                except Exception:
                    pass
        except RuntimeError:
            # already deleted
            pass
        finally:
            try:
                # ensure deletion queued
                t.deleteLater()
            except Exception:
                pass
            setattr(self, thread_attr, None)

        # Update navigation and pagination state
        self.current_page = 0
        self.total_results = 0
        self.has_more_results = True
        self.all_search_results = []

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
        # Show confirmation dialog
        dialog = ConfirmDeleteDialog(version, "version", self)
        if dialog.exec():
            # Remove version from local storage
            if version in self.versions:
                del self.versions[version]
                self.refresh_grid()
                self.version_deleted.emit(version)
    
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
<p>Features:</p>
<ul>
    <li>GitHub integration</li>
    <li>Version management</li>
    <li>Auto-fill hashes</li>
    <li>Theme support</li>
    <li>Mod icon fetching</li>
</ul>
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
        
        # New data model: single files for all versions
        self.all_mods: List[ModEntry] = []
        self.all_files: List[FileEntry] = []
        self.all_deletes: Dict[str, List[DeleteEntry]] = {}  # version -> list of deletes
        self.modpack_config: Optional[ModpackConfig] = None
        self.file_shas: Dict[str, str] = {}  # filename -> sha for GitHub updates
        
        self.load_editor_config()
        self.setup_ui()
        self.apply_theme(self.current_theme)
        
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
        has_unsaved = any(v.modified for v in self.versions.values())
        
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

