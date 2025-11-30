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
        QLineEdit, QTextEdit, QSpinBox, QCheckBox, QComboBox, QGroupBox,
        QFormLayout, QFileDialog, QMessageBox, QScrollArea, QFrame,
        QSplitter, QTabWidget, QTableWidget, QTableWidgetItem, QHeaderView,
        QDialog, QDialogButtonBox, QTreeWidget, QTreeWidgetItem, QToolButton,
        QStyle, QSizePolicy, QGridLayout, QProgressDialog, QInputDialog,
        QMenu, QWidgetAction, QProgressBar
    )
    from PyQt6.QtCore import Qt, QSize, pyqtSignal, QThread, QTimer, QByteArray
    from PyQt6.QtGui import QFont, QColor, QPalette, QIcon, QAction, QPixmap, QPainter, QImage
    PYQT_VERSION = 6
except ImportError:
    try:
        from PyQt5.QtWidgets import (
            QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
            QListWidget, QListWidgetItem, QStackedWidget, QLabel, QPushButton,
            QLineEdit, QTextEdit, QSpinBox, QCheckBox, QComboBox, QGroupBox,
            QFormLayout, QFileDialog, QMessageBox, QScrollArea, QFrame,
            QSplitter, QTabWidget, QTableWidget, QTableWidgetItem, QHeaderView,
            QDialog, QDialogButtonBox, QTreeWidget, QTreeWidgetItem, QToolButton,
            QStyle, QSizePolicy, QGridLayout, QProgressDialog, QInputDialog,
            QMenu, QWidgetAction, QProgressBar
        )
        from PyQt5.QtCore import Qt, QSize, pyqtSignal, QThread, QTimer, QByteArray
        from PyQt5.QtGui import QFont, QColor, QPalette, QIcon, QPixmap, QPainter, QImage
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
}


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
}}

QListWidget::item {{
    background-color: transparent;
    border-radius: 6px;
    padding: 12px 16px;
    margin: 2px 4px;
}}

QListWidget::item:selected {{
    background-color: {theme['bg_tertiary']};
    color: {theme['text_primary']};
}}

QListWidget::item:hover {{
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
        """Parse owner and repo from GitHub URL."""
        patterns = [
            r'github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$',
            r'github\.com/([^/]+)/([^/]+)',
        ]
        for pattern in patterns:
            match = re.search(pattern, url)
            if match:
                return match.group(1), match.group(2).replace('.git', '')
        raise ValueError(f"Invalid GitHub URL: {url}")
    
    def _request(self, method: str, endpoint: str, data: dict = None) -> dict:
        """Make a request to GitHub API."""
        url = f"{self.api_base}{endpoint}"
        headers = {
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "ModUpdater-ConfigEditor"
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
            raise Exception(f"GitHub API error {e.code}: {error_body}")
    
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


# === Icon Fetcher ===
class IconFetcher(QThread):
    """Background thread for fetching mod icons."""
    icon_fetched = pyqtSignal(str, bytes)
    fetch_complete = pyqtSignal()
    
    def __init__(self, mod_info: dict):
        super().__init__()
        self.mod_info = mod_info
        self._running = True
    
    def run(self):
        """Fetch icon from CurseForge or Modrinth."""
        source = self.mod_info.get('source', {})
        source_type = source.get('type', '')
        mod_id = self.mod_info.get('numberId', '')
        
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
            req = urllib.request.Request(url, headers={"User-Agent": "ModUpdater-ConfigEditor"})
            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read())
                icon_url = data.get('icon_url')
                if icon_url:
                    with urllib.request.urlopen(icon_url, timeout=10) as img_response:
                        return img_response.read()
        except:
            pass
        return None
    
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
            req = urllib.request.Request(self.url, headers={"User-Agent": "ModUpdater-ConfigEditor"})
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
        self.number_id = data.get('numberId', '')
        self.hash = data.get('hash', '')
        self.install_location = data.get('installLocation', 'mods')
        self.source = data.get('source', {'type': 'url', 'url': ''})
        self.icon_path = data.get('icon_path', '')
        self._is_new = not bool(data.get('numberId', ''))
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'display_name': self.display_name,
            'file_name': self.file_name,
            'numberId': self.number_id,
            'hash': self.hash,
            'installLocation': self.install_location,
            'source': self.source
        }
        if self.icon_path:
            result['icon_path'] = self.icon_path
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
        self.icon_path = data.get('icon_path', '')
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'display_name': self.display_name,
            'file_name': self.file_name,
            'url': self.url,
            'downloadPath': self.download_path,
            'hash': self.hash,
            'overwrite': self.overwrite,
            'extract': self.extract
        }
        if self.icon_path:
            result['icon_path'] = self.icon_path
        return result


class DeleteEntry:
    """Represents a delete entry in deletes.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.path = data.get('path', '')
        self.type = data.get('type', 'file')
        self.reason = data.get('reason', '')
        self.icon_path = data.get('icon_path', '')
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'path': self.path,
            'type': self.type
        }
        if self.reason:
            result['reason'] = self.reason
        if self.icon_path:
            result['icon_path'] = self.icon_path
        return result


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
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'mods': [m.to_dict() for m in self.mods],
            'files': [f.to_dict() for f in self.files],
            'deletes': [d.to_dict() for d in self.deletes]
        }



# === Dialogs ===
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
                      "The editor will fetch and save configs directly to GitHub.")
        desc.setWordWrap(True)
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(desc)
        
        form_group = QGroupBox("Repository Settings")
        form_layout = QFormLayout(form_group)
        form_layout.setSpacing(15)
        
        self.repo_url_edit = QLineEdit(self.config.get('repo_url', ''))
        self.repo_url_edit.setPlaceholderText("https://github.com/username/repo")
        form_layout.addRow("Repository URL:", self.repo_url_edit)
        
        self.token_edit = QLineEdit(self.config.get('token', ''))
        self.token_edit.setPlaceholderText("ghp_xxxxxxxxxxxx (optional for public repos)")
        self.token_edit.setEchoMode(QLineEdit.EchoMode.Password)
        form_layout.addRow("API Token:", self.token_edit)
        
        self.branch_edit = QLineEdit(self.config.get('branch', 'main'))
        self.branch_edit.setPlaceholderText("main")
        form_layout.addRow("Branch:", self.branch_edit)
        
        self.config_path_edit = QLineEdit(self.config.get('config_path', ''))
        self.config_path_edit.setPlaceholderText("configs/ (path to configs in repo)")
        form_layout.addRow("Config Path:", self.config_path_edit)
        
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
        save_btn = QPushButton("Save & Continue")
        save_btn.setObjectName("primaryButton")
        save_btn.clicked.connect(self.accept)
        button_layout.addWidget(save_btn)
        layout.addLayout(button_layout)
    
    def test_connection(self):
        repo_url = self.repo_url_edit.text().strip()
        token = self.token_edit.text().strip()
        
        if not repo_url:
            self.status_label.setText("Please enter a repository URL")
            self.status_label.setStyleSheet("color: #f38ba8;")
            return
        
        self.status_label.setText("Testing connection...")
        self.status_label.setStyleSheet("color: #f9e2af;")
        QApplication.processEvents()
        
        try:
            api = GitHubAPI(repo_url, token)
            api.branch = self.branch_edit.text().strip() or "main"
            if api.test_connection():
                self.status_label.setText("Connection successful!")
                self.status_label.setStyleSheet("color: #a6e3a1;")
            else:
                self.status_label.setText("Could not connect to repository")
                self.status_label.setStyleSheet("color: #f38ba8;")
        except Exception as e:
            self.status_label.setText(f"Error: {str(e)[:50]}")
            self.status_label.setStyleSheet("color: #f38ba8;")
    
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
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("Add New Version")
        self.setMinimumSize(400, 200)
        self.setModal(True)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(20)
        layout.setContentsMargins(30, 30, 30, 30)
        
        header = QLabel("Add New Version")
        header.setStyleSheet("font-size: 18px; font-weight: bold;")
        layout.addWidget(header)
        
        form_layout = QFormLayout()
        form_layout.setSpacing(15)
        self.version_edit = QLineEdit()
        self.version_edit.setPlaceholderText("e.g., 1.0.0")
        form_layout.addRow("Version Number:", self.version_edit)
        layout.addLayout(form_layout)
        
        self.error_label = QLabel("")
        self.error_label.setStyleSheet("color: #f38ba8;")
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
    
    def validate_and_accept(self):
        version = self.version_edit.text().strip()
        if not version:
            self.error_label.setText("Please enter a version number")
            return
        if not re.match(r'^[\d.]+$', version):
            self.error_label.setText("Version should only contain numbers and dots")
            return
        if version in self.existing_versions:
            self.error_label.setText("This version already exists")
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
        self.icon_preview.setStyleSheet("border: 2px dashed #45475a; border-radius: 8px;")
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
        self.error_label.setStyleSheet("color: #f38ba8;")
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
        mod.number_id = self.id_edit.text().strip()
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
        header.setStyleSheet("font-size: 18px; font-weight: bold; color: #f38ba8;")
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



# === Grid Item Widget ===
class ItemCard(QFrame):
    """Clickable card widget for grid display."""
    clicked = pyqtSignal()
    double_clicked = pyqtSignal()
    
    def __init__(self, name: str, icon_path: str = "", is_add_button: bool = False, parent=None):
        super().__init__(parent)
        self.name = name
        self.icon_path = icon_path
        self.is_add_button = is_add_button
        self.selected = False
        self.setup_ui()
    
    def setup_ui(self):
        self.setFixedSize(100, 100)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.update_style()
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(4)
        
        self.icon_label = QLabel()
        self.icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.icon_label.setFixedSize(48, 48)
        
        if self.is_add_button:
            self.icon_label.setText("+")
            self.icon_label.setStyleSheet("font-size: 32px; font-weight: bold; color: #89b4fa;")
        elif self.icon_path and os.path.exists(self.icon_path):
            pixmap = QPixmap(self.icon_path)
            if not pixmap.isNull():
                self.icon_label.setPixmap(pixmap.scaled(48, 48, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
        else:
            self.icon_label.setText("📦")
            self.icon_label.setStyleSheet("font-size: 24px;")
        
        layout.addWidget(self.icon_label, alignment=Qt.AlignmentFlag.AlignCenter)
        
        self.name_label = QLabel(self.name if not self.is_add_button else "Add")
        self.name_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.name_label.setWordWrap(True)
        self.name_label.setStyleSheet("font-size: 11px;")
        layout.addWidget(self.name_label)
    
    def update_style(self):
        if self.selected:
            self.setStyleSheet("""
                ItemCard {
                    background-color: #45475a;
                    border: 2px solid #89b4fa;
                    border-radius: 8px;
                }
            """)
        else:
            self.setStyleSheet("""
                ItemCard {
                    background-color: #313244;
                    border: 2px solid #45475a;
                    border-radius: 8px;
                }
                ItemCard:hover {
                    border-color: #89b4fa;
                }
            """)
    
    def set_selected(self, selected: bool):
        self.selected = selected
        self.update_style()
    
    def set_icon(self, pixmap: QPixmap):
        if not pixmap.isNull():
            self.icon_label.setPixmap(pixmap.scaled(48, 48, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
    
    def mousePressEvent(self, event):
        self.clicked.emit()
    
    def mouseDoubleClickEvent(self, event):
        self.double_clicked.emit()


# === Mod Editor Panel ===
class ModEditorPanel(QWidget):
    """Right panel for editing a selected mod."""
    mod_changed = pyqtSignal()
    hash_requested = pyqtSignal(str)
    
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
        
        # Hash Section
        hash_group = QGroupBox("Hash")
        hash_layout = QVBoxLayout(hash_group)
        self.hash_edit = QLineEdit()
        self.hash_edit.setPlaceholderText("SHA-256 hash (auto-filled)")
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
        
        self.display_name_edit = QLineEdit()
        self.display_name_edit.setPlaceholderText("Display name")
        location_layout.addRow("Display Name:", self.display_name_edit)
        
        scroll_layout.addWidget(location_group)
        
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
    
    def set_source_type(self, source_type: str):
        self.curseforge_btn.setChecked(source_type == 'curseforge')
        self.modrinth_btn.setChecked(source_type == 'modrinth')
        self.url_btn.setChecked(source_type == 'url')
        
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
        
        self.id_edit.setText(mod.number_id)
        self.id_edit.setEnabled(mod.is_new())  # Only editable for new mods
        
        self.hash_edit.setText(mod.hash)
        self.display_name_edit.setText(mod.display_name)
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
        
        self.blockSignals(False)
    
    def save_changes(self):
        if not self.current_mod:
            return
        
        self.current_mod.number_id = self.id_edit.text().strip()
        self.current_mod.hash = self.hash_edit.text().strip()
        self.current_mod.display_name = self.display_name_edit.text().strip()
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
    
    def auto_fill_hash(self):
        url = ""
        if self.url_btn.isChecked():
            url = self.url_edit.text().strip()
        
        if not url:
            QMessageBox.warning(self, "No URL", "Please enter a URL first to auto-fill the hash.")
            return
        
        self.hash_progress.setVisible(True)
        self.hash_progress.setValue(0)
        self.auto_hash_btn.setEnabled(False)
        
        self.hash_calculator = HashCalculator(url)
        self.hash_calculator.hash_calculated.connect(self.on_hash_calculated)
        self.hash_calculator.progress_updated.connect(self.hash_progress.setValue)
        self.hash_calculator.error_occurred.connect(self.on_hash_error)
        self.hash_calculator.start()
    
    def on_hash_calculated(self, hash_value: str):
        self.hash_edit.setText(hash_value)
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)
    
    def on_hash_error(self, error: str):
        QMessageBox.warning(self, "Hash Error", f"Failed to calculate hash:\n{error}")
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)
    
    def on_field_changed(self):
        pass  # Could add unsaved indicator
    
    def request_delete(self):
        if self.current_mod:
            dialog = ConfirmDeleteDialog(self.current_mod.display_name or self.current_mod.number_id, "mod", self)
            if dialog.exec():
                self.mod_changed.emit()
    
    def clear(self):
        self.current_mod = None
        self.id_edit.clear()
        self.hash_edit.clear()
        self.display_name_edit.clear()
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
        
        # Hash Section
        hash_group = QGroupBox("Hash")
        hash_layout = QVBoxLayout(hash_group)
        self.hash_edit = QLineEdit()
        self.hash_edit.setPlaceholderText("SHA-256 hash (auto-filled)")
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
    
    def auto_fill_hash(self):
        url = self.url_edit.text().strip()
        if not url:
            QMessageBox.warning(self, "No URL", "Please enter a URL first.")
            return
        
        self.hash_progress.setVisible(True)
        self.hash_progress.setValue(0)
        self.auto_hash_btn.setEnabled(False)
        
        self.hash_calculator = HashCalculator(url)
        self.hash_calculator.hash_calculated.connect(self.on_hash_calculated)
        self.hash_calculator.progress_updated.connect(self.hash_progress.setValue)
        self.hash_calculator.error_occurred.connect(self.on_hash_error)
        self.hash_calculator.start()
    
    def on_hash_calculated(self, hash_value: str):
        self.hash_edit.setText(hash_value)
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)
    
    def on_hash_error(self, error: str):
        QMessageBox.warning(self, "Hash Error", f"Failed to calculate hash:\n{error}")
        self.hash_progress.setVisible(False)
        self.auto_hash_btn.setEnabled(True)
    
    def request_delete(self):
        if self.current_file:
            dialog = ConfirmDeleteDialog(self.current_file.display_name, "file", self)
            if dialog.exec():
                self.file_changed.emit()
    
    def clear(self):
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
    
    def save_changes(self):
        if not self.current_delete:
            return
        self.current_delete.path = self.path_edit.text().strip()
        self.current_delete.type = self.type_combo.currentText()
        self.current_delete.reason = self.reason_edit.text().strip()
        self.delete_changed.emit()
    
    def request_delete(self):
        if self.current_delete:
            dialog = ConfirmDeleteDialog(self.current_delete.path, "delete entry", self)
            if dialog.exec():
                self.delete_changed.emit()
    
    def clear(self):
        self.current_delete = None
        self.path_edit.clear()
        self.type_combo.setCurrentIndex(0)
        self.reason_edit.clear()



# === Version Editor Page ===
class VersionEditorPage(QWidget):
    """Page for editing a specific version (mods, files, deletes)."""
    version_modified = pyqtSignal()
    back_requested = pyqtSignal()
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.version_config: Optional[VersionConfig] = None
        self.selected_mod_index = -1
        self.selected_file_index = -1
        self.selected_delete_index = -1
        self.icon_cache = {}
        self.setup_ui()
    
    def setup_ui(self):
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(0, 0, 0, 0)
        
        # Header with back button
        header_layout = QHBoxLayout()
        header_layout.setContentsMargins(16, 12, 16, 12)
        
        self.back_btn = QPushButton("← Back")
        self.back_btn.clicked.connect(self.back_requested.emit)
        header_layout.addWidget(self.back_btn)
        
        self.version_label = QLabel("Version")
        self.version_label.setStyleSheet("font-size: 18px; font-weight: bold;")
        header_layout.addWidget(self.version_label)
        
        header_layout.addStretch()
        main_layout.addLayout(header_layout)
        
        # Tab widget
        self.tabs = QTabWidget()
        
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
        self.mods_grid.setSpacing(12)
        self.mods_scroll.setWidget(self.mods_grid_widget)
        
        left_layout.addWidget(self.mods_scroll)
        
        # Right: Editor panel
        self.mod_editor = ModEditorPanel()
        self.mod_editor.mod_changed.connect(self.on_mod_changed)
        
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(self.mod_editor)
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
        self.files_grid.setSpacing(12)
        self.files_scroll.setWidget(self.files_grid_widget)
        
        left_layout.addWidget(self.files_scroll)
        
        # Right: Editor panel
        self.file_editor = FileEditorPanel()
        self.file_editor.file_changed.connect(self.on_file_changed)
        
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(self.file_editor)
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
        
        # Right: Editor panel
        self.delete_editor = DeleteEditorPanel()
        self.delete_editor.delete_changed.connect(self.on_delete_changed)
        
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(self.delete_editor)
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
        
        self.version_icon_preview = QLabel()
        self.version_icon_preview.setFixedSize(64, 64)
        self.version_icon_preview.setStyleSheet("border: 2px dashed #45475a; border-radius: 8px;")
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
        self.refresh_mods_grid()
        self.refresh_files_grid()
        self.refresh_deletes_list()
        self.mod_editor.clear()
        self.file_editor.clear()
        self.delete_editor.clear()
        
        # Load version icon
        if version_config.icon_path and os.path.exists(version_config.icon_path):
            pixmap = QPixmap(version_config.icon_path)
            if not pixmap.isNull():
                self.version_icon_preview.setPixmap(pixmap.scaled(60, 60, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
    
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
            card = ItemCard(mod.display_name or mod.number_id, mod.icon_path)
            card.clicked.connect(lambda idx=i: self.select_mod(idx))
            card.double_clicked.connect(lambda idx=i: self.select_mod(idx))
            self.mods_grid.addWidget(card, row, col)
            
            col += 1
            if col >= max_cols:
                col = 0
                row += 1
        
        # Add "Add" button
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
        
        # Add "Add" button
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
    
    def on_delete_selected(self, item):
        index = self.deletes_list.row(item)
        if not self.version_config or index < 0 or index >= len(self.version_config.deletes):
            return
        self.selected_delete_index = index
        self.delete_editor.load_delete(self.version_config.deletes[index])
    
    def add_mod(self):
        if not self.version_config:
            return
        
        existing_ids = [m.number_id for m in self.version_config.mods]
        dialog = AddModDialog(existing_ids, self)
        if dialog.exec():
            mod = dialog.get_mod()
            self.version_config.mods.append(mod)
            self.version_config.modified = True
            self.refresh_mods_grid()
            self.select_mod(len(self.version_config.mods) - 1)
            self.version_modified.emit()
    
    def add_file(self):
        if not self.version_config:
            return
        
        file_entry = FileEntry()
        self.version_config.files.append(file_entry)
        self.version_config.modified = True
        self.refresh_files_grid()
        self.select_file(len(self.version_config.files) - 1)
        self.version_modified.emit()
    
    def add_delete(self):
        if not self.version_config:
            return
        
        delete_entry = DeleteEntry()
        self.version_config.deletes.append(delete_entry)
        self.version_config.modified = True
        self.refresh_deletes_list()
        self.deletes_list.setCurrentRow(len(self.version_config.deletes) - 1)
        self.delete_editor.load_delete(delete_entry)
        self.version_modified.emit()
    
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



# === Version Selection Page ===
class VersionSelectionPage(QWidget):
    """Page for selecting/creating versions."""
    version_selected = pyqtSignal(str)
    
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
        
        desc = QLabel("Choose a version to edit or create a new one.")
        layout.addWidget(desc)
        
        layout.addSpacing(20)
        
        # Grid of versions
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        self.grid_widget = QWidget()
        self.grid = QGridLayout(self.grid_widget)
        self.grid.setSpacing(16)
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
        
        # Sort versions
        sorted_versions = sorted(self.versions.keys(), key=lambda v: [int(x) if x.isdigit() else 0 for x in v.split('.')], reverse=True)
        
        # Add version cards
        for version in sorted_versions:
            config = self.versions[version]
            icon_path = config.icon_path if hasattr(config, 'icon_path') else ""
            card = ItemCard(version, icon_path)
            card.clicked.connect(lambda v=version: self.version_selected.emit(v))
            card.double_clicked.connect(lambda v=version: self.version_selected.emit(v))
            self.grid.addWidget(card, row, col)
            
            col += 1
            if col >= max_cols:
                col = 0
                row += 1
        
        # Add "Add" button
        add_card = ItemCard("", "", is_add_button=True)
        add_card.clicked.connect(self.add_version)
        self.grid.addWidget(add_card, row, col)
    
    def add_version(self):
        existing = list(self.versions.keys())
        dialog = AddVersionDialog(existing, self)
        if dialog.exec():
            version = dialog.get_version()
            self.versions[version] = VersionConfig(version)
            self.refresh_grid()
            self.version_selected.emit(version)


# === Settings Page ===
class SettingsPage(QWidget):
    """Settings page for app configuration."""
    settings_changed = pyqtSignal()
    
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
        self.settings_changed.emit()



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
        sidebar = QWidget()
        sidebar.setFixedWidth(220)
        sidebar.setObjectName("sidebar")
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(12, 16, 12, 16)
        sidebar_layout.setSpacing(8)
        
        # Logo
        logo_label = QLabel("ModUpdater")
        logo_label.setStyleSheet("font-size: 18px; font-weight: 700; padding: 8px; padding-bottom: 16px;")
        sidebar_layout.addWidget(logo_label)
        
        # Navigation
        self.nav_list = QListWidget()
        self.nav_list.addItem("📦 Versions")
        self.nav_list.addItem("⚙️ Settings")
        self.nav_list.setCurrentRow(0)
        self.nav_list.currentRowChanged.connect(self.on_nav_changed)
        sidebar_layout.addWidget(self.nav_list)
        
        sidebar_layout.addStretch()
        
        # Status indicator
        self.status_label = QLabel("● Disconnected")
        self.status_label.setStyleSheet("color: #f38ba8; padding: 8px;")
        sidebar_layout.addWidget(self.status_label)
        
        # Save button
        save_btn = QPushButton("💾 Save All")
        save_btn.setObjectName("successButton")
        save_btn.clicked.connect(self.save_all)
        sidebar_layout.addWidget(save_btn)
        
        main_layout.addWidget(sidebar)
        
        # Content stack
        self.stack = QStackedWidget()
        
        # Version Selection Page
        self.version_selection_page = VersionSelectionPage()
        self.version_selection_page.version_selected.connect(self.open_version)
        self.stack.addWidget(self.version_selection_page)
        
        # Version Editor Page
        self.version_editor_page = VersionEditorPage()
        self.version_editor_page.version_modified.connect(self.on_version_modified)
        self.version_editor_page.back_requested.connect(self.show_version_selection)
        self.stack.addWidget(self.version_editor_page)
        
        # Settings Page
        self.settings_page = SettingsPage()
        self.settings_page.settings_changed.connect(self.on_settings_changed)
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
    
    def connect_to_github(self):
        """Connect to GitHub and fetch configs."""
        github_config = self.editor_config.get('github', {})
        repo_url = github_config.get('repo_url', '')
        token = github_config.get('token', '')
        branch = github_config.get('branch', 'main')
        
        if not repo_url:
            self.status_label.setText("● Not configured")
            self.status_label.setStyleSheet("color: #f38ba8;")
            return
        
        try:
            self.github_api = GitHubAPI(repo_url, token)
            self.github_api.branch = branch
            
            if self.github_api.test_connection():
                self.status_label.setText("● Connected")
                self.status_label.setStyleSheet("color: #a6e3a1;")
                self.settings_page.set_repo_url(repo_url)
                self.fetch_versions()
            else:
                self.status_label.setText("● Connection failed")
                self.status_label.setStyleSheet("color: #f38ba8;")
        except Exception as e:
            self.status_label.setText("● Error")
            self.status_label.setStyleSheet("color: #f38ba8;")
            QMessageBox.warning(self, "Connection Error", f"Failed to connect to GitHub:\n{str(e)}")
    
    def fetch_versions(self):
        """Fetch version configs from GitHub."""
        if not self.github_api:
            return
        
        config_path = self.editor_config.get('github', {}).get('config_path', '')
        
        try:
            # List directories in config path
            items = self.github_api.list_directory(config_path)
            
            self.versions = {}
            
            for item in items:
                if item.get('type') == 'dir':
                    version = item['name']
                    version_config = VersionConfig(version)
                    
                    # Fetch mods.json
                    mods_path = f"{config_path}/{version}/mods.json" if config_path else f"{version}/mods.json"
                    try:
                        content, sha = self.github_api.get_file(mods_path)
                        if content:
                            data = json.loads(content)
                            if isinstance(data, list):
                                version_config.mods = [ModEntry(m) for m in data]
                            version_config._file_shas['mods.json'] = sha
                    except:
                        pass
                    
                    # Fetch files.json
                    files_path = f"{config_path}/{version}/files.json" if config_path else f"{version}/files.json"
                    try:
                        content, sha = self.github_api.get_file(files_path)
                        if content:
                            data = json.loads(content)
                            files_data = data.get('files', []) if isinstance(data, dict) else []
                            version_config.files = [FileEntry(f) for f in files_data]
                            version_config._file_shas['files.json'] = sha
                    except:
                        pass
                    
                    # Fetch deletes.json
                    deletes_path = f"{config_path}/{version}/deletes.json" if config_path else f"{version}/deletes.json"
                    try:
                        content, sha = self.github_api.get_file(deletes_path)
                        if content:
                            data = json.loads(content)
                            deletes_data = data.get('deletes', []) if isinstance(data, dict) else []
                            version_config.deletes = [DeleteEntry(d) for d in deletes_data]
                            version_config._file_shas['deletes.json'] = sha
                    except:
                        pass
                    
                    self.versions[version] = version_config
            
            self.version_selection_page.set_versions(self.versions)
            
        except Exception as e:
            QMessageBox.warning(self, "Fetch Error", f"Failed to fetch versions:\n{str(e)}")
    
    def apply_theme(self, theme_key: str):
        """Apply a theme to the application."""
        if theme_key not in THEMES:
            theme_key = "dark"
        
        self.current_theme = theme_key
        theme = THEMES[theme_key]
        
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
    
    def on_nav_changed(self, index: int):
        """Handle navigation list selection change."""
        if index == 0:
            self.show_version_selection()
        elif index == 1:
            self.stack.setCurrentWidget(self.settings_page)
    
    def show_version_selection(self):
        """Show the version selection page."""
        self.stack.setCurrentWidget(self.version_selection_page)
        self.nav_list.setCurrentRow(0)
    
    def open_version(self, version: str):
        """Open a version for editing."""
        if version in self.versions:
            self.version_editor_page.load_version(self.versions[version])
            self.stack.setCurrentWidget(self.version_editor_page)
    
    def on_version_modified(self):
        """Handle version modification."""
        # Update status or indicator
        pass
    
    def on_settings_changed(self):
        """Handle settings change."""
        new_theme = self.settings_page.get_theme()
        if new_theme != self.current_theme:
            self.apply_theme(new_theme)
            self.save_editor_config()
    
    def refresh_from_github(self):
        """Refresh all data from GitHub."""
        reply = QMessageBox.question(
            self, "Refresh",
            "This will discard any unsaved changes. Continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.fetch_versions()
            self.show_version_selection()
    
    def save_all(self):
        """Save all changes to GitHub."""
        if not self.github_api:
            QMessageBox.warning(self, "Not Connected", "Please configure GitHub connection first.")
            return
        
        config_path = self.editor_config.get('github', {}).get('config_path', '')
        
        # Collect all changes
        changes = []
        
        for version, config in self.versions.items():
            if not config.modified:
                continue
            
            base_path = f"{config_path}/{version}" if config_path else version
            
            # Prepare mods.json
            mods_content = json.dumps([m.to_dict() for m in config.mods], indent=2)
            mods_sha = config._file_shas.get('mods.json')
            changes.append((f"{base_path}/mods.json", mods_content, mods_sha))
            
            # Prepare files.json
            files_content = json.dumps({'files': [f.to_dict() for f in config.files]}, indent=2)
            files_sha = config._file_shas.get('files.json')
            changes.append((f"{base_path}/files.json", files_content, files_sha))
            
            # Prepare deletes.json
            deletes_content = json.dumps({'deletes': [d.to_dict() for d in config.deletes]}, indent=2)
            deletes_sha = config._file_shas.get('deletes.json')
            changes.append((f"{base_path}/deletes.json", deletes_content, deletes_sha))
        
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
                
                # Update version config with new SHA
                version = path.split('/')[1] if config_path else path.split('/')[0]
                filename = path.split('/')[-1]
                if version in self.versions and new_sha:
                    self.versions[version]._file_shas[filename] = new_sha
                    self.versions[version].modified = False
                    
            except Exception as e:
                errors.append(f"{path}: {str(e)}")
        
        progress.setValue(len(changes))
        
        if errors:
            QMessageBox.warning(self, "Save Errors", 
                f"Some files failed to save:\n\n" + "\n".join(errors))
        else:
            QMessageBox.information(self, "Saved", "All changes saved to GitHub successfully!")
    
    def validate_all(self):
        """Validate all configurations."""
        errors = []
        
        for version, config in self.versions.items():
            # Check mods
            ids_seen = set()
            for i, mod in enumerate(config.mods):
                if not mod.number_id:
                    errors.append(f"[{version}] Mod {i+1}: Missing ID")
                elif mod.number_id in ids_seen:
                    errors.append(f"[{version}] Mod {i+1}: Duplicate ID '{mod.number_id}'")
                else:
                    ids_seen.add(mod.number_id)
                
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
    
    def closeEvent(self, event):
        """Handle window close."""
        # Check for unsaved changes
        has_unsaved = any(v.modified for v in self.versions.values())
        
        if has_unsaved:
            reply = QMessageBox.question(
                self, "Unsaved Changes",
                "You have unsaved changes. Save before closing?",
                QMessageBox.StandardButton.Save | 
                QMessageBox.StandardButton.Discard | 
                QMessageBox.StandardButton.Cancel
            )
            
            if reply == QMessageBox.StandardButton.Save:
                self.save_all()
                event.accept()
            elif reply == QMessageBox.StandardButton.Discard:
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
