[Setup]
AppName=Meteotablo
AppVersion=1.0.0
AppPublisher=Meteotablo
DefaultDirName={autopf}\Meteotablo
DefaultGroupName=Meteotablo
OutputDir=build\installer
OutputBaseFilename=Meteotablo-Setup-Win7-x86-x64
Compression=lzma2
SolidCompression=yes
MinVersion=6.1
ArchitecturesAllowed=x86 x64compatible
SetupIconFile=assets\meteotablo.ico
UninstallDisplayIcon={app}\Meteotablo.exe
PrivilegesRequired=lowest

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "build\package\Meteotablo.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "build\package\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "build\package\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Meteotablo"; Filename: "{app}\Meteotablo.exe"
Name: "{autodesktop}\Meteotablo"; Filename: "{app}\Meteotablo.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Meteotablo.exe"; Description: "{cm:LaunchProgram,Meteotablo}"; Flags: nowait postinstall skipifsilent
