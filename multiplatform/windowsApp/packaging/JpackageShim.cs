using System;
using System.Diagnostics;
using System.IO;
using System.Text;

internal static class JpackageShim
{
    private static int Main(string[] args)
    {
        var binDir = AppDomain.CurrentDomain.BaseDirectory;
        var configPath = Path.Combine(binDir, "shim.config");
        if (!File.Exists(configPath))
        {
            Console.Error.WriteLine("jpackage shim: missing shim.config next to jpackage.exe");
            return 2;
        }

        string realJpackage = null;
        string overrideDir = null;
        foreach (var line in File.ReadAllLines(configPath, Encoding.UTF8))
        {
            var trimmed = line.Trim();
            if (trimmed.StartsWith("real=", StringComparison.OrdinalIgnoreCase))
            {
                realJpackage = trimmed.Substring(5).Trim();
            }
            else if (trimmed.StartsWith("override=", StringComparison.OrdinalIgnoreCase))
            {
                overrideDir = trimmed.Substring(9).Trim();
            }
        }

        if (string.IsNullOrEmpty(realJpackage) || !File.Exists(realJpackage))
        {
            Console.Error.WriteLine("jpackage shim: real jpackage not found: " + realJpackage);
            return 2;
        }

        var overrideWxs = string.IsNullOrEmpty(overrideDir)
            ? null
            : Path.Combine(overrideDir, "main.wxs");
        var copied = 0;
        if (!string.IsNullOrEmpty(overrideWxs) && File.Exists(overrideWxs))
        {
            var expanded = ExpandArgs(args);
            for (var i = 0; i < expanded.Length - 1; i++)
            {
                if (!string.Equals(expanded[i], "--resource-dir", StringComparison.Ordinal))
                {
                    continue;
                }

                var resourceDir = TrimQuotes(expanded[i + 1]);
                Directory.CreateDirectory(resourceDir);
                File.Copy(overrideWxs, Path.Combine(resourceDir, "main.wxs"), true);
                copied++;
            }
        }
        File.WriteAllText(
            Path.Combine(binDir, "shim-last-run.log"),
            "real=" + realJpackage + "\noverride=" + overrideWxs + "\ncopied=" + copied + "\nargs=" + QuoteArgs(args) + "\n",
            Encoding.UTF8);

        var start = new ProcessStartInfo
        {
            FileName = realJpackage,
            UseShellExecute = false,
            Arguments = QuoteArgs(args),
        };
        using (var process = Process.Start(start))
        {
            if (process == null)
            {
                Console.Error.WriteLine("jpackage shim: failed to start " + realJpackage);
                return 2;
            }

            process.WaitForExit();
            return process.ExitCode;
        }
    }

    private static string[] ExpandArgs(string[] args)
    {
        var list = new System.Collections.Generic.List<string>();
        foreach (var arg in args)
        {
            if (arg.Length > 1 && arg[0] == '@' && File.Exists(arg.Substring(1)))
            {
                foreach (var line in File.ReadAllLines(arg.Substring(1), Encoding.UTF8))
                {
                    var trimmed = line.Trim();
                    if (trimmed.Length > 0)
                    {
                        list.Add(TrimQuotes(trimmed));
                    }
                }
            }
            else
            {
                list.Add(arg);
            }
        }

        return list.ToArray();
    }

    private static string TrimQuotes(string value)
    {
        if (value.Length >= 2 && value[0] == '"' && value[value.Length - 1] == '"')
        {
            return value.Substring(1, value.Length - 2);
        }

        return value;
    }

    private static string QuoteArgs(string[] args)
    {
        var builder = new StringBuilder();
        foreach (var arg in args)
        {
            if (builder.Length > 0)
            {
                builder.Append(' ');
            }

            if (arg.Length > 0 && arg.IndexOfAny(new[] { ' ', '\t', '"' }) < 0)
            {
                builder.Append(arg);
            }
            else
            {
                builder.Append('"');
                builder.Append(arg.Replace("\"", "\\\""));
                builder.Append('"');
            }
        }

        return builder.ToString();
    }
}
