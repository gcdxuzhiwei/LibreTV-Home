using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;

[assembly: AssemblyVersion("1.2.0.0")]
internal sealed class RemoteDebug : Form
{
	[DllImport("user32.dll")]
	private static extern bool ShowWindowAsync(IntPtr window, int command);
	[DllImport("user32.dll")]
	private static extern bool SetForegroundWindow(IntPtr window);
	private readonly string project = AppDomain.CurrentDomain.BaseDirectory;

	private readonly ComboBox devices = new ComboBox();

	private readonly Label status = new Label();

	private readonly List<Button> actions = new List<Button>();

	private bool working;

	private static readonly Dictionary<string, int> Keys = new Dictionary<string, int>
	{
		{ "上", 19 },
		{ "下", 20 },
		{ "左", 21 },
		{ "右", 22 },
		{ "确认", 23 },
		{ "返回", 4 },
		{ "主页", 3 }
	};

	private string Adb { get { return Path.Combine(project, ".tools", "android-sdk", "platform-tools", "adb.exe"); } }

	private string Emulator { get { return Path.Combine(project, ".tools", "android-sdk", "emulator", "emulator.exe"); } }

	[STAThread]
	private static int Main(string[] args)
	{
		Application.EnableVisualStyles();
		Application.SetCompatibleTextRenderingDefault(defaultValue: false);
		Application.Run(new RemoteDebug());
		return 0;
	}

	private RemoteDebug()
	{
		Text = "LibreTV 调试遥控器";
		base.ClientSize = new Size(350, 490);
		base.FormBorderStyle = FormBorderStyle.FixedSingle;
		base.MaximizeBox = false;
		base.StartPosition = FormStartPosition.CenterScreen;
		base.AutoScaleMode = AutoScaleMode.Dpi;
		Font = new Font("Microsoft YaHei UI", 10f);
		BackColor = Color.FromArgb(24, 26, 32);
		ForeColor = Color.WhiteSmoke;
		AddLabel("ANDROID TV  /  调试遥控器", 20, 17, 310, 28);
		devices.SetBounds(20, 56, 226, 30);
		devices.DropDownStyle = ComboBoxStyle.DropDownList;
		base.Controls.Add(devices);
		AddButton("刷新", 254, 54, 76, 34, RefreshDevices);
		foreach (KeyValuePair<string, int> key in Keys)
		{
			string name = key.Key;
			int num = ((name == "左") ? 30 : ((name == "右") ? 226 : 128));
			int num2 = ((name == "上") ? 108 : ((name == "下") ? 236 : 172));
			if (name == "返回")
			{
				num = 30;
				num2 = 300;
			}
			if (name == "主页")
			{
				num = 226;
				num2 = 300;
			}
			AddButton(name, num, num2, 94, 54, async delegate
			{
				string serial = SelectedDevice();
				await Run(Adb, "-s " + Quote(serial) + " shell input keyevent " + Keys[name], 15000);
				SetStatus("已发送：" + name + "  ·  " + serial);
			});
		}
		AddButton("打开安卓模拟器", 20, 373, 150, 40, OpenEmulator);
		AddButton("选择并安装 APK", 180, 373, 150, 40, InstallApk);
		status.SetBounds(20, 427, 310, 54);
		status.ForeColor = Color.FromArgb(195, 201, 212);
		status.Text = "使用项目 .tools 中的 Android 工具";
		base.Controls.Add(status);
		base.Shown += async delegate
		{
			await Execute(RefreshDevices);
		};
	}

	private void AddLabel(string text, int x, int y, int w, int h)
	{
		base.Controls.Add(new Label
		{
			Text = text,
			Bounds = new Rectangle(x, y, w, h)
		});
	}

	private void AddButton(string text, int x, int y, int w, int h, Func<Task> action)
	{
		Button button = new Button();
		button.Text = text;
		button.Bounds = new Rectangle(x, y, w, h);
		button.FlatStyle = FlatStyle.Flat;
		button.BackColor = Color.FromArgb(43, 47, 57);
		button.ForeColor = Color.WhiteSmoke;
		button.UseVisualStyleBackColor = false;
		button.FlatAppearance.BorderColor = Color.FromArgb(76, 83, 96);
		if (text == "确认")
		{
			button.BackColor = Color.FromArgb(220, 247, 99);
			button.ForeColor = Color.FromArgb(24, 26, 32);
		}
		button.Click += async delegate
		{
			await Execute(action);
		};
		actions.Add(button);
		base.Controls.Add(button);
	}

	private async Task Execute(Func<Task> action)
	{
		if (working)
		{
			return;
		}
		working = true;
		foreach (Button action2 in actions)
		{
			action2.Enabled = false;
		}
		devices.Enabled = false;
		try
		{
			await action();
		}
		catch (Exception ex)
		{
			if (!base.IsDisposed)
			{
				SetStatus("操作失败：" + ex.Message);
				MessageBox.Show(this, ex.Message, "调试工具", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
			}
		}
		finally
		{
			working = false;
			if (!base.IsDisposed)
			{
				foreach (Button action3 in actions)
				{
					action3.Enabled = true;
				}
				devices.Enabled = true;
			}
		}
	}

	private void SetStatus(string text)
	{
		if (!base.IsDisposed)
		{
			status.Text = text;
		}
	}

	private string SelectedDevice()
	{
		if (devices.SelectedItem == null)
		{
			throw new Exception("没有已连接设备，请先打开模拟器，再点击刷新。");
		}
		return devices.SelectedItem.ToString();
	}

	private async Task RefreshDevices()
	{
		SetStatus("正在查找 Android 设备…");
		string previous = ((devices.SelectedItem == null) ? "" : devices.SelectedItem.ToString());
		string output = await Run(Adb, "devices", 20000);
		if (base.IsDisposed)
		{
			return;
		}
		devices.Items.Clear();
		foreach (Match item in Regex.Matches(output, "(?m)^([^\\s]+)\\s+device\\s*$"))
		{
			devices.Items.Add(item.Groups[1].Value);
		}
		if (devices.Items.Contains(previous))
		{
			devices.SelectedItem = previous;
		}
		else if (devices.Items.Contains("emulator-5554"))
		{
			devices.SelectedItem = "emulator-5554";
		}
		else if (devices.Items.Count > 0)
		{
			devices.SelectedIndex = 0;
		}
		SetStatus((devices.Items.Count > 0) ? "已连接，可点击遥控按键" : "没有就绪设备，请打开模拟器或检查 ADB 授权");
	}

	private async Task OpenEmulator()
	{
		await RefreshDevices();
		if (base.IsDisposed)
		{
			return;
		}
		string selected = devices.SelectedItem == null ? "" : devices.SelectedItem.ToString();
		string running = selected.StartsWith("emulator-") ? selected : devices.Items.Cast<string>().FirstOrDefault((string s) => s.StartsWith("emulator-"));
		string avd = null;
		if (running != null)
		{
			devices.SelectedItem = running;
			string avdOutput = await Run(Adb, "-s " + Quote(running) + " emu avd name", 15000);
			avd = avdOutput.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)
				.FirstOrDefault(s => s != "OK" && !s.StartsWith("KO:"));
			if (string.IsNullOrWhiteSpace(avd)) throw new Exception("无法识别正在运行的模拟器。");
			if (ShowEmulatorWindow(running, avd))
			{
				await OpenApp(running, "org.libretv.home");
				SetStatus("已显示模拟器，并打开 LibreTV");
				return;
			}
			// 无窗口实例无法动态显示 UI，正常关闭后用同一个 AVD 重新启动，保留本机数据。
			SetStatus("正在将无窗口模拟器切换为可见窗口…");
			await Run(Adb, "-s " + Quote(running) + " emu kill", 15000);
			bool stopped = false;
			for (int i = 0; i < 30; i++)
			{
				await Task.Delay(1000);
				if (base.IsDisposed) return;
				string output = await Run(Adb, "devices", 15000);
				if (!Regex.IsMatch(output, "(?m)^" + Regex.Escape(running) + "\\s")) { stopped = true; break; }
			}
			if (!stopped) throw new Exception("模拟器尚未退出，请稍后重试。");
			// ADB 断开后，模拟器还需要短暂时间释放 AVD 文件锁。
			await Task.Delay(2000);
		}
		else
		{
			string[] avds = (await Run(Emulator, "-list-avds", 20000)).Split(new char[2] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
			avd = avds.FirstOrDefault((string s) => s == "LibreTVValidation") ?? avds.FirstOrDefault();
		}
		if (avd == null)
		{
			throw new Exception("未找到 Android 虚拟设备，请先创建 AVD。");
		}
		ProcessStartInfo info = StartInfo(Emulator, "-avd " + Quote(avd) + " -gpu swiftshader -no-snapshot-load");
		info.WindowStyle = ProcessWindowStyle.Normal;
		info.CreateNoWindow = false;
		info.StandardOutputEncoding = null;
		info.StandardErrorEncoding = null;
		info.RedirectStandardOutput = false;
		info.RedirectStandardError = false;
		using (Process.Start(info))
		{
		}
		SetStatus("正在启动 " + avd + "，等待系统就绪…");
		for (int i = 0; i < 60; i++)
		{
			if (base.IsDisposed)
			{
				break;
			}
			await Task.Delay(2000);
			if (base.IsDisposed)
			{
				return;
			}
			string serial = null;
			foreach (Match match in Regex.Matches(await Run(Adb, "devices", 15000), "(?m)^(emulator-\\d+)\\s+device\\s*$"))
			{
				string candidate = match.Groups[1].Value;
				string name = await Run(Adb, "-s " + Quote(candidate) + " emu avd name", 15000);
				if (name.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries).Contains(avd)) { serial = candidate; break; }
			}
			if (serial == null)
			{
				continue;
			}
			if (!((await Run(Adb, "-s " + Quote(serial) + " shell getprop sys.boot_completed", 15000)).Trim() != "1"))
			{
				await RefreshDevices();
				if (!base.IsDisposed)
				{
					devices.SelectedItem = serial;
					if (!ShowEmulatorWindow(serial, avd)) throw new Exception("模拟器已启动，但尚未找到可见窗口，请稍后重试。");
					await OpenApp(serial, "org.libretv.home");
					SetStatus("模拟器已就绪，已打开 LibreTV");
				}
				return;
			}
		}
		SetStatus("模拟器仍在启动，可稍后点击刷新");
	}

	private static bool ShowEmulatorWindow(string serial, string avd)
	{
		string port = serial.Substring("emulator-".Length);
		foreach (Process process in Process.GetProcesses())
		{
			using (process)
			{
				try
				{
					if (!(process.ProcessName.StartsWith("qemu-system") || process.ProcessName == "emulator")) continue;
					IntPtr window = process.MainWindowHandle;
					string title = process.MainWindowTitle;
					if (window == IntPtr.Zero || !(title.Contains(port) || title.Contains(avd))) continue;
					ShowWindowAsync(window, 9); // 恢复最小化的窗口。
					SetForegroundWindow(window);
					return true;
				}
				catch (InvalidOperationException) { }
				catch (System.ComponentModel.Win32Exception) { }
			}
		}
		return false;
	}

	private async Task<string> ApkPackage(string apk)
	{
		string tools = Path.Combine(project, ".tools", "android-sdk", "build-tools");
		string aapt = (Directory.Exists(tools) ? (from s in Directory.GetFiles(tools, "aapt.exe", SearchOption.AllDirectories)
			orderby s descending
			select s).FirstOrDefault() : null);
		if (aapt == null)
		{
			throw new Exception("缺少 SDK build-tools 中的 aapt.exe，无法识别 APK。");
		}
		Match match = Regex.Match(await Run(aapt, "dump badging " + Quote(apk), 20000), "(?m)^package: name='([A-Za-z0-9_.]+)'");
		if (!match.Success)
		{
			throw new Exception("无法读取 APK 包名，请选择有效的 Android 安装包。");
		}
		return match.Groups[1].Value;
	}

	private async Task InstallApk()
	{
		string serial = SelectedDevice();
		string apk;
		using (OpenFileDialog openFileDialog = new OpenFileDialog())
		{
			openFileDialog.Title = "选择要安装到 " + serial + " 的 APK";
			openFileDialog.Filter = "Android 安装包 (*.apk)|*.apk";
			openFileDialog.InitialDirectory = (Directory.Exists(Path.Combine(project, "dist")) ? Path.Combine(project, "dist") : project);
			if (openFileDialog.ShowDialog(this) != DialogResult.OK)
			{
				return;
			}
			apk = openFileDialog.FileName;
		}
		await InstallAndOpen(serial, apk);
	}

	private async Task InstallAndOpen(string serial, string apk)
	{
		string package = await ApkPackage(apk);
		SetStatus("正在安装 " + Path.GetFileName(apk) + "…");
		string result = await Run(Adb, "-s " + Quote(serial) + " install -r " + Quote(apk), 180000);
		if (!Regex.IsMatch(result, "(?m)^Success\\s*$"))
		{
			throw new Exception("APK 安装未成功：\n" + result);
		}
		SetStatus("安装成功，正在打开应用…");
		await OpenApp(serial, package);
		SetStatus("已安装并打开：" + Path.GetFileName(apk));
	}

	private async Task OpenApp(string serial, string package)
	{
		// 由 Android 解析 Launcher 入口，兼容原生与 React Native TV 版本。
		string launched = await Run(Adb, "-s " + Quote(serial) + " shell monkey -p " + Quote(package) + " -c android.intent.category.LAUNCHER 1", 20000);
		if (!launched.Contains("Events injected: 1"))
		{
			throw new Exception("应用未能打开，请确认已经安装 APK：\n" + launched);
		}
	}

	private static string Quote(string value)
	{
		StringBuilder stringBuilder = new StringBuilder("\"");
		int num = 0;
		foreach (char c in value)
		{
			if (c == '\\')
			{
				num++;
				continue;
			}
			stringBuilder.Append('\\', (c == '"') ? (num * 2 + 1) : num);
			stringBuilder.Append(c);
			num = 0;
		}
		stringBuilder.Append('\\', num * 2);
		return stringBuilder.Append('"').ToString();
	}

	private static ProcessStartInfo StartInfo(string file, string arguments)
	{
		if (!File.Exists(file))
		{
			throw new FileNotFoundException("未找到工具，请将 EXE 放在 LibreTV 项目根目录：\n" + file);
		}
		ProcessStartInfo processStartInfo = new ProcessStartInfo(file, arguments);
		processStartInfo.UseShellExecute = false;
		processStartInfo.CreateNoWindow = true;
		processStartInfo.WindowStyle = ProcessWindowStyle.Hidden;
		processStartInfo.WorkingDirectory = AppDomain.CurrentDomain.BaseDirectory;
		processStartInfo.RedirectStandardOutput = true;
		processStartInfo.RedirectStandardError = true;
		processStartInfo.StandardOutputEncoding = Encoding.UTF8;
		processStartInfo.StandardErrorEncoding = Encoding.UTF8;
		return processStartInfo;
	}

	private static async Task<string> Run(string file, string arguments, int timeout)
	{
		Process process = new Process
		{
			StartInfo = StartInfo(file, arguments)
		};
		try
		{
			process.Start();
			Task<string> stdout = process.StandardOutput.ReadToEndAsync();
			Task<string> stderr = process.StandardError.ReadToEndAsync();
			if (!(await Task.Run(() => process.WaitForExit(timeout))))
			{
				try
				{
					process.Kill();
				}
				catch
				{
				}
				throw new Exception("命令超时，请检查模拟器是否正常运行。");
			}
			string output = await stdout;
			string error = await stderr;
			if (process.ExitCode != 0)
			{
				throw new Exception((output + "\n" + error).Trim());
			}
			return output;
		}
		finally
		{
			if (process != null)
			{
				((IDisposable)process).Dispose();
			}
		}
	}
}

