ModUpdater

ModUpdater is a Minecraft/Forge 1.7.10 updater that automatically installs mods, removes obsolete files, and updates configuration files.

It relies on a Modpack Repository containing all mods, configs, and manifests.

Configuration
Local Config (config/modupdater_config.json)
{
"remote_config_url": "https://raw.githubusercontent.com/YourName/ModpackRepo/main/latest_config.json"
}


Purpose: Points the updater to the remote latest_config.json.

Only this file is needed locally to let ModUpdater know where to fetch the latest version.

Version Tracking (modpack_version.txt)

Contains the current installed modpack version, e.g.:

1.2


ModUpdater uses this to determine if an update is needed.

Remote Manifest (latest_config.json)
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


Fields:

version → Latest modpack version

mods_json_url → URL to the version-specific mods.json

configs_zip_url → URL to the version-specific configs zip

delete_history_urls → URLs to delete.json for old versions (multi-version cleanup)

files → Optional extra files (zips, resource packs)

backup_before_delete → Backup files before deletion

prune_mods → Remove mods not listed in mods.json

Mods List (mods.json per version)
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


Must list all mods for that version to ensure clients skipping versions update correctly.

Supports url, curseforge, and modrinth sources.

Delete List (delete.json per version)
[
"mods/oldmod-1.0.jar",
"mods/legacy_folder/",
"config/oldmod.cfg"
]


Lists files and directories to remove before installing new mods/configs.

Multi-version delete ensures skipped updates are cleaned properly.

Configs Zip (configs.zip per version)

Contains all updated/new config files for the version.

Extraction rules:

overwrite=true → Replace existing configs

overwrite=false → Keep existing configs

Works together with delete.json to remove obsolete configs.

Update Flow

Read local version (modpack_version.txt)

Download latest_config.json

If local < remote:

Apply multi-version deletes (delete_history_urls)

Download and install mods from mods.json

Extract configs.zip

Download extra files (optional)

Optionally prune mods

Update modpack_version.txt to the new version

GUI shows: Update complete

Else → GUI shows: Up to date

Backups

Stored in modupdater_backups/backup-<timestamp>.zip

Protects user-edited configs/mods before deletion

Extra Files

Can include zips, single files, or resource packs.

Fields: url, destination, extract, overwrite