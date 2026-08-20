using System.ServiceProcess;

namespace WinSysMonitor;

internal static class Program
{
    [STAThread]
    static void Main()
    {
        var args = Environment.GetCommandLineArgs();

        // One-shot capture child: WinSysMonitor.exe --capture <outFile>
        // Launched by the scheduled task in the user's interactive session.
        // No PowerShell involved — fast, and the process is this EXE itself.
        for (int i = 1; i < args.Length; i++)
        {
            if (args[i] == "--capture" && i + 1 < args.Length)
            {
                Environment.ExitCode = ScreenCapture.CaptureToFile(args[i + 1]);
                return;
            }
        }

        // Windows Service mode: run 24x7 as LocalSystem (installed by the admin
        // installer).
        for (int i = 0; i < args.Length; i++)
        {
            if (args[i] == "--service")
            {
                if (Environment.UserInteractive)
                    RunServiceInConsole();
                else
                    ServiceBase.Run(new ServiceBase[] { new AgentService() });
                return;
            }
        }

        // No args (e.g. launched from the Start Menu shortcut): nothing for the
        // user to interact with — the service owns the connection. Exit quietly.
    }

    private static void RunServiceInConsole()
    {
        Console.WriteLine($"WinSysMonitor (console) starting — v{AgentVersionInfo.Version}");
        var service = new AgentService();
        service.StartManually();
        Console.WriteLine("Running. Press Ctrl+C to stop.");
        var done = new ManualResetEvent(false);
        Console.CancelKeyPress += (_, e) => { e.Cancel = true; done.Set(); };
        done.WaitOne();
        service.StopManually();
    }
}