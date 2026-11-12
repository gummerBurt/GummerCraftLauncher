; GummerCraft Launcher: тиха установка без майстра (1.32+), стиль Discord.
; Збирає tools/installer/build-installer.sh (передає AppVersion і Stage). Вручну:
;   ISCC.exe /DAppVersion=1.32 /DStage=<тека з GummerCraft.exe, jre\ (17) і jre8\> /O<вихід> GummerCraft.iss
; Що робить: без жодної сторінки (лише смужка прогресу), без прав адміністратора, у %LOCALAPPDATA%\Programs\GummerCraft,
; ярлики в меню Пуск і на робочому столі, запис у «Програми та компоненти», після установки відкриває лаунчер.
; Повторний запуск того самого інсталятора, коли ця версія вже стоїть: просто відкриває лаунчер.
; Дані гравця (Документи\GummerCraft: модпаки, акаунти, налаштування) установка і видалення не чіпають.

#ifndef AppVersion
  #define AppVersion "0.0"
#endif
#ifndef Stage
  #define Stage "stage"
#endif
#define AppId "4B5A2474-1B9F-4597-A8EB-5F9CBCC008D3"

[Setup]
AppId={{{#AppId}}
AppName=GummerCraft
AppVersion={#AppVersion}
AppVerName=GummerCraft {#AppVersion}
AppPublisher=GummerCraft
AppPublisherURL=https://gummercraft.fun/
AppSupportURL=https://gummercraft.fun/contact
DefaultDirName={localappdata}\Programs\GummerCraft
DefaultGroupName=GummerCraft
PrivilegesRequired=lowest
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
DisableFinishedPage=yes
DisableStartupPrompt=yes
AllowNoIcons=yes
UninstallDisplayIcon={app}\GummerCraft.exe
UninstallDisplayName=GummerCraft
SetupIconFile={#Stage}\app.ico
WizardStyle=modern
WizardSizePercent=100
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no
OutputBaseFilename=GummerCraft-Setup-{#AppVersion}
VersionInfoVersion={#AppVersion}
VersionInfoProductName=GummerCraft Launcher

[Languages]
Name: "uk"; MessagesFile: "compiler:Languages\Ukrainian.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "{#Stage}\GummerCraft.exe"; DestDir: "{app}"; Flags: ignoreversion
; Java лаунчера (tools/installer/jre.lock): Windows 10+ Temurin JRE 17 (масштаб вікна під масштаб Windows),
; Windows 7/8.1 Temurin JRE 8 (17 там офіційно не підтримується). Обидві в одну теку jre, exe шукає саме її.
Source: "{#Stage}\jre\*"; DestDir: "{app}\jre"; Check: IsWin10OrNewer; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Stage}\jre8\*"; DestDir: "{app}\jre"; Check: not IsWin10OrNewer; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; стара вбудована Java при оновленні замінюється повністю (а не змішується з новою)
Type: filesandordirs; Name: "{app}\jre"

[Icons]
Name: "{userprograms}\GummerCraft"; Filename: "{app}\GummerCraft.exe"
Name: "{userdesktop}\GummerCraft"; Filename: "{app}\GummerCraft.exe"

[Run]
Filename: "{app}\GummerCraft.exe"; Flags: nowait skipifsilent

[Code]
function IsWin10OrNewer: Boolean;
begin
  // GetWindowsVersion: старший байт - major (10 = Windows 10/11, 6 = Windows 7/8/8.1)
  Result := (GetWindowsVersion shr 24) >= 10;
end;

function InitializeSetup(): Boolean;
var
  Installed: String;
  ExePath: String;
  Code: Integer;
begin
  Result := True;
  ExePath := ExpandConstant('{localappdata}\Programs\GummerCraft\GummerCraft.exe');
  if (not WizardSilent) and FileExists(ExePath) and
     RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{{#AppId}}_is1', 'DisplayVersion', Installed) and
     (Installed = '{#AppVersion}') then
  begin
    // ця версія вже стоїть: не ставимо заново, просто відкриваємо лаунчер
    Exec(ExePath, '', '', SW_SHOWNORMAL, ewNoWait, Code);
    Result := False;
  end;
end;
