#ifndef AppVersion
  #error AppVersion is required
#endif
#ifndef SourceDir
  #error SourceDir is required
#endif
#ifndef OutputDir
  #error OutputDir is required
#endif

#define AppName "Mission Visualization"
#define AppPublisher "Aequicor"
#define AppExeName "Mission Visualization.exe"

[Setup]
; Keep this AppId independent from the legacy jpackage MSI UpgradeCode.
AppId={{B5547647-505B-41E9-A76C-1F3DFECA2FB9}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL=https://github.com/aequicor/mission-visualization
AppSupportURL=https://github.com/aequicor/mission-visualization/issues
AppUpdatesURL=https://github.com/aequicor/mission-visualization/releases
DefaultDirName={localappdata}\Programs\Mission Visualization
DefaultGroupName=Mission Visualization
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename=Mission Visualization-{#AppVersion}-setup
SetupIconFile=..\..\resources\icons\mission-logo.ico
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no
VersionInfoVersion={#AppVersion}
VersionInfoProductVersion={#AppVersion}
VersionInfoDescription=Mission Visualization installer
VersionInfoCompany={#AppPublisher}
VersionInfoProductName={#AppName}

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{userprograms}\Mission Visualization"; Filename: "{app}\{#AppExeName}"

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Launch Mission Visualization"; Flags: nowait postinstall skipifsilent
