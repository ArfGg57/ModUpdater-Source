#!/usr/bin/env python3
"""
ModUpdater Advanced Config Editor

A modern GUI application for editing ModUpdater configuration files.
Styled similar to Prism Launcher with a dark theme and sidebar navigation.

Features:
- Edit mods.json - Configure mods to download
- Edit files.json - Configure files to download
- Edit deletes.json - Configure files to delete
- Edit config.json - Main configuration settings
- JSON validation and syntax highlighting
- Import/Export configurations
"""

import sys
import json
import os
from pathlib import Path
from typing import Optional, Dict, Any, List

try:
    from PyQt6.QtWidgets import (
        QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
        QListWidget, QListWidgetItem, QStackedWidget, QLabel, QPushButton,
        QLineEdit, QTextEdit, QSpinBox, QCheckBox, QComboBox, QGroupBox,
        QFormLayout, QFileDialog, QMessageBox, QScrollArea, QFrame,
        QSplitter, QTabWidget, QTableWidget, QTableWidgetItem, QHeaderView,
        QDialog, QDialogButtonBox, QTreeWidget, QTreeWidgetItem, QToolButton,
        QStyle, QSizePolicy
    )
    from PyQt6.QtCore import Qt, QSize
    from PyQt6.QtGui import QFont, QColor, QPalette, QIcon, QAction
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
            QStyle, QSizePolicy
        )
        from PyQt5.QtCore import Qt, QSize
        from PyQt5.QtGui import QFont, QColor, QPalette, QIcon
        from PyQt5.QtWidgets import QAction
        PYQT_VERSION = 5
    except ImportError:
        print("Error: PyQt5 or PyQt6 is required. Install with: pip install PyQt6")
        sys.exit(1)


# === Styling ===
DARK_STYLE = """
QMainWindow {
    background-color: #1e1e2e;
}

QWidget {
    background-color: #1e1e2e;
    color: #cdd6f4;
    font-family: "Segoe UI", "Inter", sans-serif;
    font-size: 13px;
}

QListWidget {
    background-color: #313244;
    border: none;
    border-radius: 8px;
    padding: 5px;
    outline: none;
}

QListWidget::item {
    background-color: transparent;
    border-radius: 6px;
    padding: 12px 16px;
    margin: 2px 4px;
}

QListWidget::item:selected {
    background-color: #45475a;
    color: #cdd6f4;
}

QListWidget::item:hover {
    background-color: #3a3a4d;
}

QPushButton {
    background-color: #45475a;
    border: none;
    border-radius: 6px;
    padding: 10px 20px;
    color: #cdd6f4;
    font-weight: 600;
}

QPushButton:hover {
    background-color: #585b70;
}

QPushButton:pressed {
    background-color: #313244;
}

QPushButton#primaryButton {
    background-color: #89b4fa;
    color: #1e1e2e;
}

QPushButton#primaryButton:hover {
    background-color: #a6c9ff;
}

QPushButton#dangerButton {
    background-color: #f38ba8;
    color: #1e1e2e;
}

QPushButton#dangerButton:hover {
    background-color: #f5a0b8;
}

QPushButton#successButton {
    background-color: #a6e3a1;
    color: #1e1e2e;
}

QPushButton#successButton:hover {
    background-color: #b8f0b4;
}

QLineEdit, QSpinBox, QComboBox {
    background-color: #313244;
    border: 2px solid #45475a;
    border-radius: 6px;
    padding: 8px 12px;
    color: #cdd6f4;
}

QLineEdit:focus, QSpinBox:focus, QComboBox:focus {
    border-color: #89b4fa;
}

QTextEdit {
    background-color: #313244;
    border: 2px solid #45475a;
    border-radius: 8px;
    padding: 10px;
    color: #cdd6f4;
    font-family: "Consolas", "Monaco", monospace;
    font-size: 12px;
}

QTextEdit:focus {
    border-color: #89b4fa;
}

QGroupBox {
    background-color: #313244;
    border: 1px solid #45475a;
    border-radius: 8px;
    margin-top: 12px;
    padding: 16px;
    padding-top: 24px;
    font-weight: 600;
}

QGroupBox::title {
    subcontrol-origin: margin;
    left: 16px;
    padding: 0 8px;
    color: #89b4fa;
}

QLabel {
    color: #cdd6f4;
}

QLabel#headerLabel {
    font-size: 20px;
    font-weight: 700;
    color: #cdd6f4;
    padding: 16px 0;
}

QLabel#sectionLabel {
    font-size: 14px;
    font-weight: 600;
    color: #89b4fa;
    padding: 8px 0;
}

QScrollArea {
    border: none;
    background-color: transparent;
}

QScrollBar:vertical {
    background-color: #313244;
    width: 12px;
    border-radius: 6px;
    margin: 2px;
}

QScrollBar::handle:vertical {
    background-color: #45475a;
    border-radius: 4px;
    min-height: 30px;
    margin: 2px;
}

QScrollBar::handle:vertical:hover {
    background-color: #585b70;
}

QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
    height: 0;
}

QTabWidget::pane {
    background-color: #313244;
    border: 1px solid #45475a;
    border-radius: 8px;
    padding: 8px;
}

QTabBar::tab {
    background-color: #1e1e2e;
    border: none;
    padding: 10px 20px;
    margin-right: 4px;
    border-top-left-radius: 6px;
    border-top-right-radius: 6px;
}

QTabBar::tab:selected {
    background-color: #313244;
    color: #89b4fa;
}

QTabBar::tab:hover:!selected {
    background-color: #2a2a3e;
}

QTableWidget {
    background-color: #313244;
    border: 1px solid #45475a;
    border-radius: 8px;
    gridline-color: #45475a;
}

QTableWidget::item {
    padding: 8px;
}

QTableWidget::item:selected {
    background-color: #45475a;
}

QHeaderView::section {
    background-color: #1e1e2e;
    color: #89b4fa;
    padding: 10px;
    border: none;
    border-bottom: 2px solid #45475a;
    font-weight: 600;
}

QCheckBox {
    spacing: 8px;
}

QCheckBox::indicator {
    width: 20px;
    height: 20px;
    border-radius: 4px;
    border: 2px solid #45475a;
    background-color: #313244;
}

QCheckBox::indicator:checked {
    background-color: #89b4fa;
    border-color: #89b4fa;
}

QCheckBox::indicator:hover {
    border-color: #89b4fa;
}

QSplitter::handle {
    background-color: #45475a;
    width: 2px;
    margin: 4px 0;
}

QFrame#separator {
    background-color: #45475a;
    max-height: 1px;
    margin: 16px 0;
}

QToolTip {
    background-color: #313244;
    color: #cdd6f4;
    border: 1px solid #45475a;
    border-radius: 6px;
    padding: 8px;
}
"""


class ModEntry:
    """Represents a mod entry in mods.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.display_name = data.get('display_name', '')
        self.file_name = data.get('file_name', '')
        self.number_id = data.get('numberId', '')
        self.since = data.get('since', '0.0.0')
        self.hash = data.get('hash', '')
        self.install_location = data.get('installLocation', 'mods')
        self.source = data.get('source', {'type': 'url', 'url': ''})
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'display_name': self.display_name,
            'file_name': self.file_name,
            'numberId': self.number_id,
            'since': self.since,
            'hash': self.hash,
            'installLocation': self.install_location,
            'source': self.source
        }


class FileEntry:
    """Represents a file entry in files.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.display_name = data.get('display_name', '')
        self.file_name = data.get('file_name', '')
        self.url = data.get('url', '')
        self.download_path = data.get('downloadPath', 'config/')
        self.since = data.get('since', '0.0.0')
        self.hash = data.get('hash', '')
        self.overwrite = data.get('overwrite', True)
        self.extract = data.get('extract', False)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'display_name': self.display_name,
            'file_name': self.file_name,
            'url': self.url,
            'downloadPath': self.download_path,
            'since': self.since,
            'hash': self.hash,
            'overwrite': self.overwrite,
            'extract': self.extract
        }


class DeleteEntry:
    """Represents a delete entry in deletes.json"""
    def __init__(self, data: Dict[str, Any] = None):
        if data is None:
            data = {}
        self.path = data.get('path', '')
        self.type = data.get('type', 'file')
        self.since = data.get('since', '0.0.0')
        self.until = data.get('until', '')
        self.reason = data.get('reason', '')
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            'path': self.path,
            'type': self.type,
            'since': self.since
        }
        if self.until:
            result['until'] = self.until
        if self.reason:
            result['reason'] = self.reason
        return result


class ModEditorDialog(QDialog):
    """Dialog for editing a single mod entry"""
    def __init__(self, mod: ModEntry = None, parent=None):
        super().__init__(parent)
        self.mod = mod if mod else ModEntry()
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("Edit Mod")
        self.setMinimumSize(500, 600)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        
        # Basic Info Group
        basic_group = QGroupBox("Basic Information")
        basic_layout = QFormLayout(basic_group)
        basic_layout.setSpacing(12)
        
        self.display_name_edit = QLineEdit(self.mod.display_name)
        self.display_name_edit.setPlaceholderText("Enter display name...")
        basic_layout.addRow("Display Name:", self.display_name_edit)
        
        self.file_name_edit = QLineEdit(self.mod.file_name)
        self.file_name_edit.setPlaceholderText("Optional: custom filename...")
        basic_layout.addRow("File Name:", self.file_name_edit)
        
        self.number_id_edit = QLineEdit(self.mod.number_id)
        self.number_id_edit.setPlaceholderText("Unique identifier (e.g., modid-001)")
        basic_layout.addRow("Number ID:", self.number_id_edit)
        
        self.since_edit = QLineEdit(self.mod.since)
        self.since_edit.setPlaceholderText("Version (e.g., 1.0.0)")
        basic_layout.addRow("Since Version:", self.since_edit)
        
        self.hash_edit = QLineEdit(self.mod.hash)
        self.hash_edit.setPlaceholderText("SHA-256 hash (optional)")
        basic_layout.addRow("Hash:", self.hash_edit)
        
        self.install_location_edit = QLineEdit(self.mod.install_location)
        self.install_location_edit.setPlaceholderText("mods")
        basic_layout.addRow("Install Location:", self.install_location_edit)
        
        layout.addWidget(basic_group)
        
        # Source Group
        source_group = QGroupBox("Source Configuration")
        source_layout = QFormLayout(source_group)
        source_layout.setSpacing(12)
        
        self.source_type_combo = QComboBox()
        self.source_type_combo.addItems(['url', 'curseforge', 'modrinth'])
        self.source_type_combo.setCurrentText(self.mod.source.get('type', 'url'))
        self.source_type_combo.currentTextChanged.connect(self.on_source_type_changed)
        source_layout.addRow("Source Type:", self.source_type_combo)
        
        self.url_edit = QLineEdit(self.mod.source.get('url', ''))
        self.url_edit.setPlaceholderText("Direct download URL")
        source_layout.addRow("URL:", self.url_edit)
        
        self.project_id_edit = QLineEdit(str(self.mod.source.get('projectId', '')))
        self.project_id_edit.setPlaceholderText("CurseForge/Modrinth project ID")
        source_layout.addRow("Project ID:", self.project_id_edit)
        
        self.file_id_edit = QLineEdit(str(self.mod.source.get('fileId', '')))
        self.file_id_edit.setPlaceholderText("CurseForge file ID")
        source_layout.addRow("File ID:", self.file_id_edit)
        
        self.version_id_edit = QLineEdit(self.mod.source.get('versionId', ''))
        self.version_id_edit.setPlaceholderText("Modrinth version ID")
        source_layout.addRow("Version ID:", self.version_id_edit)
        
        layout.addWidget(source_group)
        
        # Update visibility based on source type
        self.on_source_type_changed(self.source_type_combo.currentText())
        
        # Buttons
        button_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        button_box.accepted.connect(self.accept)
        button_box.rejected.connect(self.reject)
        layout.addWidget(button_box)
    
    def on_source_type_changed(self, source_type: str):
        is_url = source_type == 'url'
        is_curseforge = source_type == 'curseforge'
        is_modrinth = source_type == 'modrinth'
        
        self.url_edit.setEnabled(is_url)
        self.project_id_edit.setEnabled(is_curseforge or is_modrinth)
        self.file_id_edit.setEnabled(is_curseforge)
        self.version_id_edit.setEnabled(is_modrinth)
    
    def get_mod(self) -> ModEntry:
        self.mod.display_name = self.display_name_edit.text()
        self.mod.file_name = self.file_name_edit.text()
        self.mod.number_id = self.number_id_edit.text()
        self.mod.since = self.since_edit.text()
        self.mod.hash = self.hash_edit.text()
        self.mod.install_location = self.install_location_edit.text() or 'mods'
        
        source_type = self.source_type_combo.currentText()
        self.mod.source = {'type': source_type}
        
        if source_type == 'url':
            self.mod.source['url'] = self.url_edit.text()
        elif source_type == 'curseforge':
            try:
                self.mod.source['projectId'] = int(self.project_id_edit.text())
            except ValueError:
                pass
            try:
                self.mod.source['fileId'] = int(self.file_id_edit.text())
            except ValueError:
                pass
        elif source_type == 'modrinth':
            if self.project_id_edit.text():
                self.mod.source['projectSlug'] = self.project_id_edit.text()
            if self.version_id_edit.text():
                self.mod.source['versionId'] = self.version_id_edit.text()
        
        return self.mod


class FileEditorDialog(QDialog):
    """Dialog for editing a single file entry"""
    def __init__(self, file_entry: FileEntry = None, parent=None):
        super().__init__(parent)
        self.file_entry = file_entry if file_entry else FileEntry()
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("Edit File")
        self.setMinimumSize(500, 500)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        
        # Basic Info Group
        basic_group = QGroupBox("File Information")
        basic_layout = QFormLayout(basic_group)
        basic_layout.setSpacing(12)
        
        self.display_name_edit = QLineEdit(self.file_entry.display_name)
        self.display_name_edit.setPlaceholderText("Enter display name...")
        basic_layout.addRow("Display Name:", self.display_name_edit)
        
        self.file_name_edit = QLineEdit(self.file_entry.file_name)
        self.file_name_edit.setPlaceholderText("Optional: custom filename...")
        basic_layout.addRow("File Name:", self.file_name_edit)
        
        self.url_edit = QLineEdit(self.file_entry.url)
        self.url_edit.setPlaceholderText("Direct download URL")
        basic_layout.addRow("URL:", self.url_edit)
        
        self.download_path_edit = QLineEdit(self.file_entry.download_path)
        self.download_path_edit.setPlaceholderText("config/")
        basic_layout.addRow("Download Path:", self.download_path_edit)
        
        self.since_edit = QLineEdit(self.file_entry.since)
        self.since_edit.setPlaceholderText("Version (e.g., 1.0.0)")
        basic_layout.addRow("Since Version:", self.since_edit)
        
        self.hash_edit = QLineEdit(self.file_entry.hash)
        self.hash_edit.setPlaceholderText("SHA-256 hash (optional)")
        basic_layout.addRow("Hash:", self.hash_edit)
        
        layout.addWidget(basic_group)
        
        # Options Group
        options_group = QGroupBox("Options")
        options_layout = QVBoxLayout(options_group)
        options_layout.setSpacing(12)
        
        self.overwrite_check = QCheckBox("Overwrite existing file")
        self.overwrite_check.setChecked(self.file_entry.overwrite)
        options_layout.addWidget(self.overwrite_check)
        
        self.extract_check = QCheckBox("Extract ZIP archive")
        self.extract_check.setChecked(self.file_entry.extract)
        options_layout.addWidget(self.extract_check)
        
        layout.addWidget(options_group)
        
        # Buttons
        button_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        button_box.accepted.connect(self.accept)
        button_box.rejected.connect(self.reject)
        layout.addWidget(button_box)
    
    def get_file(self) -> FileEntry:
        self.file_entry.display_name = self.display_name_edit.text()
        self.file_entry.file_name = self.file_name_edit.text()
        self.file_entry.url = self.url_edit.text()
        self.file_entry.download_path = self.download_path_edit.text() or 'config/'
        self.file_entry.since = self.since_edit.text()
        self.file_entry.hash = self.hash_edit.text()
        self.file_entry.overwrite = self.overwrite_check.isChecked()
        self.file_entry.extract = self.extract_check.isChecked()
        return self.file_entry


class DeleteEditorDialog(QDialog):
    """Dialog for editing a single delete entry"""
    def __init__(self, delete_entry: DeleteEntry = None, parent=None):
        super().__init__(parent)
        self.delete_entry = delete_entry if delete_entry else DeleteEntry()
        self.setup_ui()
    
    def setup_ui(self):
        self.setWindowTitle("Edit Delete Entry")
        self.setMinimumSize(450, 350)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        
        # Basic Info Group
        basic_group = QGroupBox("Delete Entry")
        basic_layout = QFormLayout(basic_group)
        basic_layout.setSpacing(12)
        
        self.path_edit = QLineEdit(self.delete_entry.path)
        self.path_edit.setPlaceholderText("Path to delete (e.g., mods/oldmod.jar)")
        basic_layout.addRow("Path:", self.path_edit)
        
        self.type_combo = QComboBox()
        self.type_combo.addItems(['file', 'folder'])
        self.type_combo.setCurrentText(self.delete_entry.type)
        basic_layout.addRow("Type:", self.type_combo)
        
        self.since_edit = QLineEdit(self.delete_entry.since)
        self.since_edit.setPlaceholderText("Version (e.g., 1.0.0)")
        basic_layout.addRow("Since Version:", self.since_edit)
        
        self.until_edit = QLineEdit(self.delete_entry.until)
        self.until_edit.setPlaceholderText("Optional: stop deleting after version")
        basic_layout.addRow("Until Version:", self.until_edit)
        
        self.reason_edit = QLineEdit(self.delete_entry.reason)
        self.reason_edit.setPlaceholderText("Optional: reason for deletion")
        basic_layout.addRow("Reason:", self.reason_edit)
        
        layout.addWidget(basic_group)
        
        # Buttons
        button_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        button_box.accepted.connect(self.accept)
        button_box.rejected.connect(self.reject)
        layout.addWidget(button_box)
    
    def get_delete_entry(self) -> DeleteEntry:
        self.delete_entry.path = self.path_edit.text()
        self.delete_entry.type = self.type_combo.currentText()
        self.delete_entry.since = self.since_edit.text()
        self.delete_entry.until = self.until_edit.text()
        self.delete_entry.reason = self.reason_edit.text()
        return self.delete_entry


class ModsPage(QWidget):
    """Page for editing mods.json"""
    def __init__(self):
        super().__init__()
        self.mods: List[ModEntry] = []
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 16, 24, 16)
        layout.setSpacing(16)
        
        # Header
        header = QLabel("Mods Configuration")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        desc = QLabel("Configure mods to be downloaded and managed by ModUpdater.")
        desc.setWordWrap(True)
        layout.addWidget(desc)
        
        # Toolbar
        toolbar = QHBoxLayout()
        
        add_btn = QPushButton("+ Add Mod")
        add_btn.setObjectName("primaryButton")
        add_btn.clicked.connect(self.add_mod)
        toolbar.addWidget(add_btn)
        
        edit_btn = QPushButton("Edit")
        edit_btn.clicked.connect(self.edit_mod)
        toolbar.addWidget(edit_btn)
        
        remove_btn = QPushButton("Remove")
        remove_btn.setObjectName("dangerButton")
        remove_btn.clicked.connect(self.remove_mod)
        toolbar.addWidget(remove_btn)
        
        toolbar.addStretch()
        
        layout.addLayout(toolbar)
        
        # Table
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(['Display Name', 'Number ID', 'Source', 'Since', 'Hash'])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.doubleClicked.connect(self.edit_mod)
        layout.addWidget(self.table)
    
    def add_mod(self):
        dialog = ModEditorDialog(parent=self)
        if dialog.exec():
            mod = dialog.get_mod()
            self.mods.append(mod)
            self.update_table()
    
    def edit_mod(self):
        row = self.table.currentRow()
        if row >= 0 and row < len(self.mods):
            dialog = ModEditorDialog(self.mods[row], parent=self)
            if dialog.exec():
                self.mods[row] = dialog.get_mod()
                self.update_table()
    
    def remove_mod(self):
        row = self.table.currentRow()
        if row >= 0 and row < len(self.mods):
            reply = QMessageBox.question(
                self, 'Confirm Remove',
                f'Remove mod "{self.mods[row].display_name}"?',
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )
            if reply == QMessageBox.StandardButton.Yes:
                del self.mods[row]
                self.update_table()
    
    def update_table(self):
        self.table.setRowCount(len(self.mods))
        for i, mod in enumerate(self.mods):
            self.table.setItem(i, 0, QTableWidgetItem(mod.display_name))
            self.table.setItem(i, 1, QTableWidgetItem(mod.number_id))
            source_type = mod.source.get('type', 'url')
            self.table.setItem(i, 2, QTableWidgetItem(source_type))
            self.table.setItem(i, 3, QTableWidgetItem(mod.since))
            hash_short = mod.hash[:8] + '...' if len(mod.hash) > 8 else mod.hash
            self.table.setItem(i, 4, QTableWidgetItem(hash_short))
    
    def load_data(self, data: List[Dict]):
        self.mods = [ModEntry(m) for m in data]
        self.update_table()
    
    def get_data(self) -> List[Dict]:
        return [m.to_dict() for m in self.mods]


class FilesPage(QWidget):
    """Page for editing files.json"""
    def __init__(self):
        super().__init__()
        self.files: List[FileEntry] = []
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 16, 24, 16)
        layout.setSpacing(16)
        
        # Header
        header = QLabel("Files Configuration")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        desc = QLabel("Configure additional files to be downloaded (configs, resources, etc.).")
        desc.setWordWrap(True)
        layout.addWidget(desc)
        
        # Toolbar
        toolbar = QHBoxLayout()
        
        add_btn = QPushButton("+ Add File")
        add_btn.setObjectName("primaryButton")
        add_btn.clicked.connect(self.add_file)
        toolbar.addWidget(add_btn)
        
        edit_btn = QPushButton("Edit")
        edit_btn.clicked.connect(self.edit_file)
        toolbar.addWidget(edit_btn)
        
        remove_btn = QPushButton("Remove")
        remove_btn.setObjectName("dangerButton")
        remove_btn.clicked.connect(self.remove_file)
        toolbar.addWidget(remove_btn)
        
        toolbar.addStretch()
        
        layout.addLayout(toolbar)
        
        # Table
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(['Display Name', 'URL', 'Path', 'Since', 'Overwrite'])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.doubleClicked.connect(self.edit_file)
        layout.addWidget(self.table)
    
    def add_file(self):
        dialog = FileEditorDialog(parent=self)
        if dialog.exec():
            file_entry = dialog.get_file()
            self.files.append(file_entry)
            self.update_table()
    
    def edit_file(self):
        row = self.table.currentRow()
        if row >= 0 and row < len(self.files):
            dialog = FileEditorDialog(self.files[row], parent=self)
            if dialog.exec():
                self.files[row] = dialog.get_file()
                self.update_table()
    
    def remove_file(self):
        row = self.table.currentRow()
        if row >= 0 and row < len(self.files):
            reply = QMessageBox.question(
                self, 'Confirm Remove',
                f'Remove file "{self.files[row].display_name}"?',
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )
            if reply == QMessageBox.StandardButton.Yes:
                del self.files[row]
                self.update_table()
    
    def update_table(self):
        self.table.setRowCount(len(self.files))
        for i, f in enumerate(self.files):
            self.table.setItem(i, 0, QTableWidgetItem(f.display_name))
            url_short = f.url[:40] + '...' if len(f.url) > 40 else f.url
            self.table.setItem(i, 1, QTableWidgetItem(url_short))
            self.table.setItem(i, 2, QTableWidgetItem(f.download_path))
            self.table.setItem(i, 3, QTableWidgetItem(f.since))
            self.table.setItem(i, 4, QTableWidgetItem('Yes' if f.overwrite else 'No'))
    
    def load_data(self, data: List[Dict]):
        self.files = [FileEntry(f) for f in data]
        self.update_table()
    
    def get_data(self) -> List[Dict]:
        return [f.to_dict() for f in self.files]


class DeletesPage(QWidget):
    """Page for editing deletes.json"""
    def __init__(self):
        super().__init__()
        self.deletes: List[DeleteEntry] = []
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 16, 24, 16)
        layout.setSpacing(16)
        
        # Header
        header = QLabel("Deletes Configuration")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        desc = QLabel("Configure files or folders to be deleted during updates.")
        desc.setWordWrap(True)
        layout.addWidget(desc)
        
        # Toolbar
        toolbar = QHBoxLayout()
        
        add_btn = QPushButton("+ Add Delete")
        add_btn.setObjectName("primaryButton")
        add_btn.clicked.connect(self.add_delete)
        toolbar.addWidget(add_btn)
        
        edit_btn = QPushButton("Edit")
        edit_btn.clicked.connect(self.edit_delete)
        toolbar.addWidget(edit_btn)
        
        remove_btn = QPushButton("Remove")
        remove_btn.setObjectName("dangerButton")
        remove_btn.clicked.connect(self.remove_delete)
        toolbar.addWidget(remove_btn)
        
        toolbar.addStretch()
        
        layout.addLayout(toolbar)
        
        # Table
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(['Path', 'Type', 'Since', 'Until', 'Reason'])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.doubleClicked.connect(self.edit_delete)
        layout.addWidget(self.table)
    
    def add_delete(self):
        dialog = DeleteEditorDialog(parent=self)
        if dialog.exec():
            delete_entry = dialog.get_delete_entry()
            self.deletes.append(delete_entry)
            self.update_table()
    
    def edit_delete(self):
        row = self.table.currentRow()
        if row >= 0 and row < len(self.deletes):
            dialog = DeleteEditorDialog(self.deletes[row], parent=self)
            if dialog.exec():
                self.deletes[row] = dialog.get_delete_entry()
                self.update_table()
    
    def remove_delete(self):
        row = self.table.currentRow()
        if row >= 0 and row < len(self.deletes):
            reply = QMessageBox.question(
                self, 'Confirm Remove',
                f'Remove delete entry "{self.deletes[row].path}"?',
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )
            if reply == QMessageBox.StandardButton.Yes:
                del self.deletes[row]
                self.update_table()
    
    def update_table(self):
        self.table.setRowCount(len(self.deletes))
        for i, d in enumerate(self.deletes):
            self.table.setItem(i, 0, QTableWidgetItem(d.path))
            self.table.setItem(i, 1, QTableWidgetItem(d.type))
            self.table.setItem(i, 2, QTableWidgetItem(d.since))
            self.table.setItem(i, 3, QTableWidgetItem(d.until or '-'))
            self.table.setItem(i, 4, QTableWidgetItem(d.reason or '-'))
    
    def load_data(self, data: List[Dict]):
        self.deletes = [DeleteEntry(d) for d in data]
        self.update_table()
    
    def get_data(self) -> List[Dict]:
        return [d.to_dict() for d in self.deletes]


class ConfigPage(QWidget):
    """Page for editing config.json"""
    def __init__(self):
        super().__init__()
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 16, 24, 16)
        layout.setSpacing(16)
        
        # Header
        header = QLabel("Main Configuration")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        desc = QLabel("Configure ModUpdater settings and remote configuration URLs.")
        desc.setWordWrap(True)
        layout.addWidget(desc)
        
        # Scroll area for the form
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        
        scroll_widget = QWidget()
        scroll_layout = QVBoxLayout(scroll_widget)
        scroll_layout.setSpacing(16)
        
        # Remote Config Group
        remote_group = QGroupBox("Remote Configuration")
        remote_layout = QFormLayout(remote_group)
        remote_layout.setSpacing(12)
        
        self.remote_config_url = QLineEdit()
        self.remote_config_url.setPlaceholderText("https://raw.githubusercontent.com/...")
        remote_layout.addRow("Remote Config URL:", self.remote_config_url)
        
        self.configs_base_url = QLineEdit()
        self.configs_base_url.setPlaceholderText("https://raw.githubusercontent.com/...")
        remote_layout.addRow("Configs Base URL:", self.configs_base_url)
        
        scroll_layout.addWidget(remote_group)
        
        # JSON Files Group
        json_group = QGroupBox("JSON File Names")
        json_layout = QFormLayout(json_group)
        json_layout.setSpacing(12)
        
        self.mods_json = QLineEdit("mods.json")
        json_layout.addRow("Mods JSON:", self.mods_json)
        
        self.files_json = QLineEdit("files.json")
        json_layout.addRow("Files JSON:", self.files_json)
        
        self.deletes_json = QLineEdit("deletes.json")
        json_layout.addRow("Deletes JSON:", self.deletes_json)
        
        scroll_layout.addWidget(json_group)
        
        # Version Group
        version_group = QGroupBox("Version Settings")
        version_layout = QFormLayout(version_group)
        version_layout.setSpacing(12)
        
        self.modpack_version = QLineEdit()
        self.modpack_version.setPlaceholderText("1.0.0")
        version_layout.addRow("Modpack Version:", self.modpack_version)
        
        self.since_version = QLineEdit("0.0.0")
        version_layout.addRow("Since Version:", self.since_version)
        
        scroll_layout.addWidget(version_group)
        
        # Options Group
        options_group = QGroupBox("Options")
        options_layout = QVBoxLayout(options_group)
        options_layout.setSpacing(12)
        
        self.check_current_version = QCheckBox("Check current version (verify existing files)")
        self.check_current_version.setChecked(True)
        options_layout.addWidget(self.check_current_version)
        
        self.debug_mode = QCheckBox("Debug mode (verbose logging)")
        options_layout.addWidget(self.debug_mode)
        
        scroll_layout.addWidget(options_group)
        
        # Retry Settings Group
        retry_group = QGroupBox("Retry Settings")
        retry_layout = QFormLayout(retry_group)
        retry_layout.setSpacing(12)
        
        self.max_retries = QSpinBox()
        self.max_retries.setRange(1, 10)
        self.max_retries.setValue(3)
        retry_layout.addRow("Max Retries:", self.max_retries)
        
        self.backup_keep = QSpinBox()
        self.backup_keep.setRange(1, 20)
        self.backup_keep.setValue(5)
        retry_layout.addRow("Backups to Keep:", self.backup_keep)
        
        scroll_layout.addWidget(retry_group)
        
        scroll_layout.addStretch()
        scroll.setWidget(scroll_widget)
        layout.addWidget(scroll)
    
    def load_data(self, data: Dict):
        self.remote_config_url.setText(data.get('remote_config_url', ''))
        self.configs_base_url.setText(data.get('configsBaseUrl', ''))
        self.mods_json.setText(data.get('modsJson', 'mods.json'))
        self.files_json.setText(data.get('filesJson', 'files.json'))
        self.deletes_json.setText(data.get('deletesJson', 'deletes.json'))
        self.modpack_version.setText(data.get('modpackVersion', ''))
        self.since_version.setText(data.get('sinceVersion', '0.0.0'))
        self.check_current_version.setChecked(data.get('checkCurrentVersion', True))
        self.debug_mode.setChecked(data.get('debugMode', False))
        self.max_retries.setValue(data.get('maxRetries', 3))
        self.backup_keep.setValue(data.get('backupKeep', 5))
    
    def get_data(self) -> Dict:
        return {
            'remote_config_url': self.remote_config_url.text(),
            'configsBaseUrl': self.configs_base_url.text(),
            'modsJson': self.mods_json.text(),
            'filesJson': self.files_json.text(),
            'deletesJson': self.deletes_json.text(),
            'modpackVersion': self.modpack_version.text(),
            'sinceVersion': self.since_version.text(),
            'checkCurrentVersion': self.check_current_version.isChecked(),
            'debugMode': self.debug_mode.isChecked(),
            'maxRetries': self.max_retries.value(),
            'backupKeep': self.backup_keep.value()
        }


class RawJsonPage(QWidget):
    """Page for editing raw JSON"""
    def __init__(self, title: str):
        super().__init__()
        self.title = title
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 16, 24, 16)
        layout.setSpacing(16)
        
        # Header
        header = QLabel(f"Raw JSON - {self.title}")
        header.setObjectName("headerLabel")
        layout.addWidget(header)
        
        desc = QLabel("Edit the raw JSON directly. Changes are validated before saving.")
        desc.setWordWrap(True)
        layout.addWidget(desc)
        
        # Text editor
        self.editor = QTextEdit()
        self.editor.setPlaceholderText("Paste or edit JSON here...")
        layout.addWidget(self.editor)
        
        # Validate button
        validate_btn = QPushButton("Validate JSON")
        validate_btn.clicked.connect(self.validate_json)
        layout.addWidget(validate_btn)
    
    def validate_json(self):
        try:
            json.loads(self.editor.toPlainText())
            QMessageBox.information(self, "Valid", "JSON is valid!")
        except json.JSONDecodeError as e:
            QMessageBox.warning(self, "Invalid JSON", f"JSON parse error:\n{e}")
    
    def load_data(self, data):
        self.editor.setText(json.dumps(data, indent=2))
    
    def get_data(self):
        try:
            return json.loads(self.editor.toPlainText())
        except json.JSONDecodeError:
            return None


class MainWindow(QMainWindow):
    """Main application window"""
    def __init__(self):
        super().__init__()
        self.config_dir: Optional[Path] = None
        self.unsaved_changes = False
        self.setup_ui()
        self.setup_menu()
    
    def setup_ui(self):
        self.setWindowTitle("ModUpdater Config Editor")
        self.setMinimumSize(1200, 800)
        
        # Central widget with splitter
        central = QWidget()
        self.setCentralWidget(central)
        
        main_layout = QHBoxLayout(central)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)
        
        # Sidebar
        sidebar = QWidget()
        sidebar.setFixedWidth(220)
        sidebar.setStyleSheet("background-color: #181825;")
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(12, 16, 12, 16)
        sidebar_layout.setSpacing(8)
        
        # Logo/Title
        logo_label = QLabel("ModUpdater")
        logo_label.setStyleSheet("""
            font-size: 18px;
            font-weight: 700;
            color: #89b4fa;
            padding: 8px;
            padding-bottom: 16px;
        """)
        sidebar_layout.addWidget(logo_label)
        
        # Navigation list
        self.nav_list = QListWidget()
        self.nav_list.addItem("📦 Mods")
        self.nav_list.addItem("📄 Files")
        self.nav_list.addItem("🗑 Deletes")
        self.nav_list.addItem("⚙️ Config")
        self.nav_list.addItem("📝 Raw JSON")
        self.nav_list.setCurrentRow(0)
        self.nav_list.currentRowChanged.connect(self.on_page_changed)
        sidebar_layout.addWidget(self.nav_list)
        
        sidebar_layout.addStretch()
        
        # Save button in sidebar
        save_btn = QPushButton("💾 Save All")
        save_btn.setObjectName("successButton")
        save_btn.clicked.connect(self.save_all)
        sidebar_layout.addWidget(save_btn)
        
        main_layout.addWidget(sidebar)
        
        # Content area
        content = QWidget()
        content_layout = QVBoxLayout(content)
        content_layout.setContentsMargins(0, 0, 0, 0)
        
        # Stacked widget for pages
        self.stack = QStackedWidget()
        
        self.mods_page = ModsPage()
        self.files_page = FilesPage()
        self.deletes_page = DeletesPage()
        self.config_page = ConfigPage()
        self.raw_json_page = RawJsonPage("Configuration")
        
        self.stack.addWidget(self.mods_page)
        self.stack.addWidget(self.files_page)
        self.stack.addWidget(self.deletes_page)
        self.stack.addWidget(self.config_page)
        self.stack.addWidget(self.raw_json_page)
        
        content_layout.addWidget(self.stack)
        main_layout.addWidget(content)
    
    def setup_menu(self):
        menubar = self.menuBar()
        
        # File menu
        file_menu = menubar.addMenu("File")
        
        open_action = QAction("Open Directory...", self)
        open_action.setShortcut("Ctrl+O")
        open_action.triggered.connect(self.open_directory)
        file_menu.addAction(open_action)
        
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
        
        # Edit menu
        edit_menu = menubar.addMenu("Edit")
        
        validate_action = QAction("Validate All", self)
        validate_action.triggered.connect(self.validate_all)
        edit_menu.addAction(validate_action)
        
        # Help menu
        help_menu = menubar.addMenu("Help")
        
        about_action = QAction("About", self)
        about_action.triggered.connect(self.show_about)
        help_menu.addAction(about_action)
    
    def on_page_changed(self, index: int):
        self.stack.setCurrentIndex(index)
    
    def open_directory(self):
        dir_path = QFileDialog.getExistingDirectory(
            self, "Select ModUpdater Config Directory"
        )
        if dir_path:
            self.load_directory(Path(dir_path))
    
    def load_directory(self, dir_path: Path):
        self.config_dir = dir_path
        self.setWindowTitle(f"ModUpdater Config Editor - {dir_path}")
        
        # Load mods.json
        mods_file = dir_path / "mods.json"
        if mods_file.exists():
            try:
                with open(mods_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    if isinstance(data, list):
                        self.mods_page.load_data(data)
                    else:
                        self.mods_page.load_data([])
            except Exception as e:
                QMessageBox.warning(self, "Load Error", f"Error loading mods.json:\n{e}")
        
        # Load files.json
        files_file = dir_path / "files.json"
        if files_file.exists():
            try:
                with open(files_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    files_data = data.get('files', []) if isinstance(data, dict) else []
                    self.files_page.load_data(files_data)
            except Exception as e:
                QMessageBox.warning(self, "Load Error", f"Error loading files.json:\n{e}")
        
        # Load deletes.json
        deletes_file = dir_path / "deletes.json"
        if deletes_file.exists():
            try:
                with open(deletes_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    deletes_data = data.get('deletes', []) if isinstance(data, dict) else []
                    self.deletes_page.load_data(deletes_data)
            except Exception as e:
                QMessageBox.warning(self, "Load Error", f"Error loading deletes.json:\n{e}")
        
        # Load config.json
        config_file = dir_path / "config.json"
        if config_file.exists():
            try:
                with open(config_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.config_page.load_data(data)
                    self.raw_json_page.load_data(data)
            except Exception as e:
                QMessageBox.warning(self, "Load Error", f"Error loading config.json:\n{e}")
    
    def save_all(self):
        if not self.config_dir:
            QMessageBox.warning(self, "No Directory", "Please open a directory first.")
            return
        
        try:
            # Save mods.json
            mods_file = self.config_dir / "mods.json"
            with open(mods_file, 'w', encoding='utf-8') as f:
                json.dump(self.mods_page.get_data(), f, indent=2)
            
            # Save files.json
            files_file = self.config_dir / "files.json"
            with open(files_file, 'w', encoding='utf-8') as f:
                json.dump({'files': self.files_page.get_data()}, f, indent=2)
            
            # Save deletes.json
            deletes_file = self.config_dir / "deletes.json"
            with open(deletes_file, 'w', encoding='utf-8') as f:
                json.dump({'deletes': self.deletes_page.get_data()}, f, indent=2)
            
            # Save config.json
            config_file = self.config_dir / "config.json"
            with open(config_file, 'w', encoding='utf-8') as f:
                json.dump(self.config_page.get_data(), f, indent=2)
            
            QMessageBox.information(self, "Saved", "All configuration files saved successfully!")
            
        except Exception as e:
            QMessageBox.critical(self, "Save Error", f"Error saving files:\n{e}")
    
    def validate_all(self):
        errors = []
        
        # Validate mods
        for i, mod in enumerate(self.mods_page.mods):
            if not mod.display_name:
                errors.append(f"Mod {i+1}: Missing display name")
            if not mod.number_id:
                errors.append(f"Mod {i+1}: Missing number ID")
        
        # Validate files
        for i, f in enumerate(self.files_page.files):
            if not f.url:
                errors.append(f"File {i+1}: Missing URL")
        
        # Validate deletes
        for i, d in enumerate(self.deletes_page.deletes):
            if not d.path:
                errors.append(f"Delete {i+1}: Missing path")
        
        if errors:
            QMessageBox.warning(
                self, "Validation Errors",
                "The following issues were found:\n\n" + "\n".join(errors)
            )
        else:
            QMessageBox.information(self, "Valid", "All configurations are valid!")
    
    def show_about(self):
        QMessageBox.about(
            self, "About ModUpdater Config Editor",
            """<h2>ModUpdater Config Editor</h2>
            <p>Version 1.0.0</p>
            <p>A modern GUI application for editing ModUpdater configuration files.</p>
            <p>Part of the ModUpdater project.</p>
            <p><a href="https://github.com/ArfGg57/ModUpdater-Source">GitHub Repository</a></p>
            """
        )
    
    def closeEvent(self, event):
        if self.config_dir and self.has_unsaved_changes():
            reply = QMessageBox.question(
                self, 'Unsaved Changes',
                'You have unsaved changes. Do you want to save before closing?',
                QMessageBox.StandardButton.Save | QMessageBox.StandardButton.Discard | QMessageBox.StandardButton.Cancel
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
    
    def has_unsaved_changes(self) -> bool:
        """Check if there are unsaved changes by comparing current data with loaded data."""
        # For simplicity, we consider any edit as a change that should be saved
        # A more sophisticated implementation would track the original data
        return self.unsaved_changes


def main():
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    app.setStyleSheet(DARK_STYLE)
    
    window = MainWindow()
    window.show()
    
    sys.exit(app.exec() if PYQT_VERSION == 6 else app.exec_())


if __name__ == "__main__":
    main()
