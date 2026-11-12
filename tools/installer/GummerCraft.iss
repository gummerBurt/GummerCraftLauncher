; GummerCraft Launcher: один файл GummerCraft.exe з вбудованою Java (1.32+). Не «установка», а лаунчер, що при першому
; запуску тихо розпаковує себе і Java (2-5 с смужки «Перший запуск»), а далі одразу відкриває лаунчер. Вирішено 13.11.2026
; (власник: «установщики setup.exe людей відлякають»): без ярликів, без запису в «Програми та компоненти», без майстра,
; без прав адміністратора. Файли: %LOCALAPPDATA%\GummerCraft\app (exe лаунчера і jre). Гравець просто тримає скачаний
; GummerCraft.exe де завгодно і запускає його. Дані гравця (Документи\GummerCraft) цей файл не чіпає.
; Збирає tools/installer/build-installer.sh (передає AppVersion і Stage). Вручну:
;   ISCC.exe /DAppVersion=1.32 /DStage=<тека з GummerCraft.exe, jre\ і jre8\> /O<вихід> GummerCraft.iss
; Логіка запуску (InitializeSetup): розпакована версія не старша за цей файл - просто відкрити лаунчер; інакше розпакувати
; (стару jre прибрати повністю) і відкрити.

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
DefaultDirName={localappdata}\GummerCraft\app
PrivilegesRequired=lowest
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
DisableFinishedPage=yes
DisableStartupPrompt=yes
ShowLanguageDialog=no
Uninstallable=no
CreateUninstallRegKey=no
UsePreviousAppDir=no
SetupIconFile={#Stage}\app.ico
WizardStyle=modern
WizardSizePercent=100
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no
OutputBaseFilename=GummerCraft-{#AppVersion}
VersionInfoVersion={#AppVersion}
VersionInfoProductName=GummerCraft Launcher

[Languages]
Name: "uk"; MessagesFile: "compiler:Languages\Ukrainian.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[Messages]
uk.SetupWindowTitle=GummerCraft
uk.WizardInstalling=Перший запуск
uk.InstallingLabel=Готуємо GummerCraft до першого запуску, кілька секунд...
uk.StatusExtractFiles=Розпаковуємо файли...
uk.WizardPreparing=Перший запуск
en.SetupWindowTitle=GummerCraft
en.WizardInstalling=First start
en.InstallingLabel=Getting GummerCraft ready for the first start, a few seconds...
en.StatusExtractFiles=Unpacking files...
en.WizardPreparing=First start

[Files]
Source: "{#Stage}\GummerCraft.exe"; DestDir: "{app}"; Flags: ignoreversion
; Java лаунчера (tools/installer/jre.lock): Windows 10+ Temurin JRE 17 (масштаб вікна під масштаб Windows),
; Windows 7/8.1 Temurin JRE 8 (17 там офіційно не підтримується). Обидві в одну теку jre, exe шукає саме її.
Source: "{#Stage}\jre\*"; DestDir: "{app}\jre"; Check: IsWin10OrNewer; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Stage}\jre8\*"; DestDir: "{app}\jre"; Check: not IsWin10OrNewer; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; стара вбудована Java при оновленні замінюється повністю (а не змішується з новою)
Type: filesandordirs; Name: "{app}\jre"

[Run]
Filename: "{app}\GummerCraft.exe"; Flags: nowait

[Code]
function IsWin10OrNewer: Boolean;
begin
  // GetWindowsVersion: старший байт - major (10 = Windows 10/11, 6 = Windows 7/8/8.1)
  Result := (GetWindowsVersion shr 24) >= 10;
end;

// "1.32" -> порівняння з "1.4" покомпонентно (1.32 новіша за 1.4)
function CompareVer(A, B: String): Integer;
var
  X, Y, P: Integer;
begin
  Result := 0;
  while (Result = 0) and ((A <> '') or (B <> '')) do
  begin
    P := Pos('.', A); if P = 0 then P := Length(A) + 1;
    X := StrToIntDef(Copy(A, 1, P - 1), 0); Delete(A, 1, P);
    P := Pos('.', B); if P = 0 then P := Length(B) + 1;
    Y := StrToIntDef(Copy(B, 1, P - 1), 0); Delete(B, 1, P);
    if X > Y then Result := 1 else if X < Y then Result := -1;
  end;
end;

function InitializeSetup(): Boolean;
var
  Installed: AnsiString;
  AppDir: String;
  Code: Integer;
begin
  Result := True;
  AppDir := ExpandConstant('{localappdata}\GummerCraft\app');
  if FileExists(AppDir + '\GummerCraft.exe') and FileExists(AppDir + '\jre\bin\javaw.exe') and
     LoadStringFromFile(AppDir + '\version.txt', Installed) and
     (CompareVer(Trim(String(Installed)), '{#AppVersion}') >= 0) then
  begin
    // вже розпаковано (ця чи новіша версія): нічого не ставимо, просто відкриваємо лаунчер
    Exec(AppDir + '\GummerCraft.exe', '', AppDir, SW_SHOWNORMAL, ewNoWait, Code);
    Result := False;
    Exit;
  end;
end;

// без технічного шляху файлу під смужкою: гравцю досить «Розпаковуємо файли...»
procedure InitializeWizard;
begin
  WizardForm.FilenameLabel.Visible := False;
end;

// Жодних питань: Inno примусово показує сторінку «Готово до встановлення», коли всі інші сторінки вимкнені
// (DisableReadyPage тоді ігнорується, ShouldSkipPage її не пропускає, а перезапустити себе з /SILENT Inno не дає:
// «Access is denied»). Тому тиснемо «Встановити» самі одразу, як сторінка з'явилась: лишається лише смужка прогресу.
procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpReady then
    PostMessage(WizardForm.NextButton.Handle, $00F5 {BM_CLICK}, 0, 0);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  // позначка розпакованої версії: наступний запуск цього (чи старшого) файлу одразу відкриє лаунчер
  if CurStep = ssPostInstall then
    SaveStringToFile(ExpandConstant('{app}\version.txt'), '{#AppVersion}', False);
end;
