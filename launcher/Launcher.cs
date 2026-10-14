using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;

// ZayitSubset.exe: starts the Compose app on the JetBrains Runtime next to it (runtime\), with lib\ as the class path.
// With --zayit <library folder> (the shortcuts the app makes): starts desktop Zayit on that small library instead, with
// its own profile (APPDATA inside the library), the way the app's "open in desktop Zayit" button does.
static class Launcher
{
    const string Title = "ספרייה מוקטנת לזית";

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern int MessageBoxW(IntPtr h, string text, string caption, uint type);

    static void Say(string text) { MessageBoxW(IntPtr.Zero, text, Title, 0x40 | 0x00100000 | 0x00080000); }

    [STAThread]
    static void Main(string[] args)
    {
        if (args.Length >= 2 && args[0] == "--zayit") { OpenZayit(args[1]); return; }
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string javaw = Path.Combine(dir, "runtime", "bin", "javaw.exe");
        string cp = Path.Combine(dir, "lib", "*");
        var psi = new ProcessStartInfo(javaw, "-Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -Xmx2g -cp \"" + cp + "\" zayit.subset.MainKt");
        psi.UseShellExecute = false;
        psi.WorkingDirectory = dir;
        try { Process.Start(psi); }
        catch (Exception e) { Say("לא ניתן להפעיל את התוכנה:\n" + e.Message); }
    }

    static void OpenZayit(string library)
    {
        string db = Path.Combine(library, "seforim.db");
        if (!File.Exists(db)) { Say("הספרייה המוקטנת לא נמצאה:\n" + library); return; }
        string exe = null;
        foreach (var c in new[] {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "zayit", "zayit.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "zayit", "zayit.exe") })
            if (File.Exists(c)) { exe = c; break; }
        if (exe == null) { Say("לא נמצאה זית למחשב."); return; }
        // Zayit runs one instance: a second one hands over to the open one and quits
        if (Process.GetProcessesByName("zayit").Length > 0) { Say("זית כבר פתוחה. יש לסגור אותה ולפתוח שוב את הקיצור."); return; }
        string profile = Path.Combine(library, ".zayit-profile");
        Directory.CreateDirectory(profile);
        var psi = new ProcessStartInfo(exe);
        psi.UseShellExecute = false;
        psi.WorkingDirectory = Path.GetDirectoryName(exe);
        psi.EnvironmentVariables["SEFORIMAPP_DATABASE_PATH"] = db;
        psi.EnvironmentVariables["APPDATA"] = profile;
        try { Process.Start(psi); }
        catch (Exception e) { Say("לא ניתן להפעיל את זית:\n" + e.Message); }
    }
}
