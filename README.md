ModUpdater

ModUpdater is a Forge 1.7.10 Java mod that automates modpack updates. It downloads mods, deletes obsolete files, extracts new configs, and handles multi-version updates.

The project uses a two-repo system:

ModUpdater – the lightweight updater mod installed in Minecraft.

ModpackRepo – contains all mods, configs, zips, and JSON manifests for each version.

Repository Structure
1️⃣ ModUpdater (Updater Mod Repository)
ModUpdater/
├── build.gradle                     # Gradle build file
├── settings.gradle                  # Gradle settings
├── src/
│   └── main/
│       ├── java/
│       │   └── com/yourname/modupdater/
│       │       ├── ModUpdater.java        # Forge @Mod entry point
│       │       ├── UpdaterCore.java       # Main orchestrator
│       │       ├── Downloader.java        # HTTP downloads + CurseForge/Modrinth stubs
│       │       ├── FileUtils.java         # Delete, backup, unzip, prune
│       │       └── GuiUpdater.java        # Swing GUI for progress/status
│       └── resources/
│           └── META-INF/mods.toml        # Forge mod metadata
├── config_example/
│   └── modupdater_config.json           # Local config pointing to ModpackRepo
├── README.md
└── LICENSE


modupdater_config.json points to the remote latest_config.json in the modpack repo:

{
"remote_config_url": "https://raw.githubusercontent.com/YourName/ModpackRepo/main/latest_config.json"
}

2️⃣ ModpackRepo (Modpack Repository)
ModpackRepo/
├── latest_config.json                   # Central manifest for the updater
└── versions/
├── 1.1/
│   ├── mods.json
│   ├── delete.json
│   └── configs.zip
├── 1.2/
│   ├── mods.json
│   ├── delete.json
│   └── configs.zip
└── 1.3/
├── mods.json
├── delete.json
└── configs.zip

3️⃣ latest_config.json Example
{
"version": "1.3",
"mods_json_url": "https://raw.githubusercontent.com/YourName/ModpackRepo/main/versions/1.3/mods.json",
"configs_zip_url": "https://raw.githubusercontent.com/YourName/ModpackRepo/main/versions/1.3/configs.zip",
"delete_history_urls": [
"https://raw.githubusercontent.com/YourName/ModpackRepo/main/versions/1.1/delete.json",
"https://raw.githubusercontent.com/YourName/ModpackRepo/main/versions/1.2/delete.json",
"https://raw.githubusercontent.com/YourName/ModpackRepo/main/versions/1.3/delete.json"
],
"files": [
{
"url": "https://raw.githubusercontent.com/YourName/ModpackRepo/main/versions/1.3/resources.zip",
"destination": "resourcepacks/",
"extract": true,
"overwrite": true
}
],
"backup_before_delete": true,
"prune_mods": false
}


Fields explained:

version → latest version string

mods_json_url → points to the latest mods.json

configs_zip_url → points to the latest configs.zip

delete_history_urls → handles multi-version cleanup (oldest → newest)

files → optional extra files or zips

backup_before_delete → backup old files before deletion

prune_mods → remove mods not listed in mods.json (optional)

4️⃣ mods.json Example (per version)
[
{
"source": "url",
"url": "https://example.com/mods/jei-5.0.jar",
"hash": ""
},
{
"source": "curseforge",
"curseforge": { "addonId": 123456, "fileId": 654321 }
},
{
"source": "modrinth",
"modrinth": { "addonId": "modern-ui", "fileId": "version-uuid" }
}
]


Must include all mods for that version to ensure clients skipping versions get fully updated.

5️⃣ delete.json Example (per version)
[
"mods/oldmod-1.0.jar",
"mods/legacy_folder/",
"config/oldmod.cfg"
]


Lists files/directories to delete before extracting new configs/mods.

Multi-version delete ensures skipped versions are cleaned properly.

6️⃣ configs.zip Contents

Contains all updated/new config files for that version.

Extraction rules:

overwrite=true → replaces existing configs

overwrite=false → keeps existing configs

Works with delete.json to remove obsolete configs.

7️⃣ Update Flow

Check local version (modpack_version.txt)

Download latest_config.json from GitHub

Compare versions → if local < remote:

Apply multi-version deletes (delete_history_urls)

Download & install mods from mods.json

Download & extract configs.zip

Download extra files (optional)

Optionally prune mods

Write local version = remote version

GUI shows: Update complete

Else → GUI shows: Up to date

8️⃣ File Classes / Responsibilities
Class	Responsibility
ModUpdater.java	Forge entry point, launches updater + GUI
UpdaterCore.java	Orchestrates full update workflow
Downloader.java	Downloads HTTP files, CurseForge/Modrinth stubs
FileUtils.java	Delete, backup, unzip, prune, extra files
GuiUpdater.java	Swing GUI: status + progress
9️⃣ Backups

Stored in modupdater_backups/backup-<timestamp>.zip

Only deletes files that exist and are listed in delete.json

Protects user-edited configs/mods before removal

🔟 Extra Files (from latest_config.json)

Can include zips, single files, or resource packs

Fields: url, destination, extract, overwrite

Flexible system for installing additional files beyond mods/configs