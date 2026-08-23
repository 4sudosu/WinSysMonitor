using System.Drawing;
using System.Drawing.Imaging;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace WinSysMonitor;

public class AgentClient
{
    private readonly AgentConfig _config;
    private readonly DeviceInfo _device;
    private readonly CancellationTokenSource _cts = new();
    private readonly SemaphoreSlim _sendGate = new(1, 1);

    private ClientWebSocket? _ws;
    private int _reconnectDelaySec;

    public event Action<string>? Log;

    public bool IsConnected => _ws?.State == WebSocketState.Open;
    public string MachineName => _device.Hostname;
    public static string AgentVersion => AgentVersionInfo.Version;

    public AgentClient(AgentConfig config)
    {
        _config = config;
        _device = DeviceInfo.Collect();
        _reconnectDelaySec = _config.ReconnectDelaySec;
    }

    public void Stop() => _cts.Cancel();

    public async Task RunAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try
            {
                var uri = new Uri($"{_config.ServerUrl.TrimEnd('/')}?token={Uri.EscapeDataString(_config.Token)}");

                _ws?.Dispose();
                _ws = new ClientWebSocket();
                await _ws.ConnectAsync(uri, _cts.Token);
                _reconnectDelaySec = _config.ReconnectDelaySec;
                LogLine($"Connected to {uri.Host}:{uri.Port}");

                await SendAsync(new
                {
                    type = "register",
                    machineName = _device.Hostname,
                    hostname = _device.Hostname,
                    model = _device.Model,
                    serial = _device.Serial,
                    username = _device.Username,
                    user = _device.Username,
                    os = _device.Os,
                    ip = _device.Ip,
                    version = AgentVersion
                });

                // App-level keepalive: defeats NAT idle drops and Render proxy/spin-down.
                var keepAliveSec = Math.Max(0, _config.KeepAliveSec);
                using var keepAliveCts = CancellationTokenSource.CreateLinkedTokenSource(_cts.Token);
                var keepAliveTask = keepAliveSec > 0 ? KeepAliveLoopAsync(keepAliveSec, keepAliveCts.Token) : Task.CompletedTask;

                try
                {
                    await ReceiveLoopAsync();
                }
                finally
                {
                    keepAliveCts.Cancel();
                    try { await keepAliveTask; } catch { /* cancelled */ }
                }
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception ex)
            {
                LogLine($"Connection error: {ex.Message}");
            }

            _reconnectDelaySec = Math.Max(1, _reconnectDelaySec) + 3;
            LogLine($"Reconnecting in {_reconnectDelaySec}s...");
            try { await Task.Delay(_reconnectDelaySec * 1000, _cts.Token); } catch { return; }
        }
    }

    /// <summary>Periodically sends a tiny frame so NAT/proxies never see the socket as idle.
    /// A failed send means the connection is dead — abort immediately so reconnect starts fast.</summary>
    private async Task KeepAliveLoopAsync(int intervalSec, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try { await Task.Delay(intervalSec * 1000, ct); } catch { return; }
            try
            {
                await SendAsync(new { type = "keepalive", at = DateTimeOffset.UtcNow.ToUnixTimeSeconds() });
            }
            catch (Exception ex)
            {
                LogLine($"Keepalive send failed ({ex.Message}) — forcing reconnect.");
                try { _ws?.Dispose(); } catch { }
                return;
            }
        }
    }

    private async Task ReceiveLoopAsync()
    {
        var buffer = new byte[16384];
        while (_ws?.State == WebSocketState.Open)
        {
            try
            {
                WebSocketReceiveResult result;
                using var ms = new MemoryStream();
                do
                {
                    result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), _cts.Token);
                    ms.Write(buffer, 0, result.Count);
                } while (!result.EndOfMessage);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    try { await _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None); } catch { }
                    break;
                }

                var text = Encoding.UTF8.GetString(ms.ToArray());
                _ = Task.Run(() => HandleMessageAsync(text));
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception ex)
            {
                LogLine($"Receive error: {ex.Message}");
                return;
            }
        }
    }

    private async Task HandleMessageAsync(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var type = doc.RootElement.GetProperty("type").GetString();
            switch (type)
            {
                case "hello":
                    LogLine("Channel ready.");
                    break;
                case "registered":
                    LogLine($"Registered on server as {_device.Hostname}");
                    break;
                case "capture_screenshot":
                    await HandleCaptureScreenshot(doc.RootElement);
                    break;
            }
        }
        catch (Exception ex)
        {
            LogLine($"Message error: {ex.Message}");
        }
    }

    private async Task HandleCaptureScreenshot(JsonElement el)
    {
        var taskId = GetString(el, "taskId");
        try
        {
            LogLine($"Capture: direct attempt...");
            var png = TryCaptureDirect();
            LogLine($"Capture: direct result {(png == null ? "null" : png.Length + " bytes")}");
            var error = "";
            if (png == null)
            {
                var outFile = Path.Combine(PowerShellRunner.CaptureWorkDir(), $"shot-{Guid.NewGuid().ToString("N")[..10]}.b64");
                var (b64, err) = PowerShellRunner.RunInInteractiveSession(outFile, 15);
                error = err ?? "";
                LogLine($"Capture: interactive result b64={(b64 == null ? "null" : b64.Length + " chars")} err='{error}'");
                if (b64 != null)
                {
                    try { png = Convert.FromBase64String(b64); } catch { png = null; error = "Invalid capture payload"; }
                }
            }

            if (png == null || png.Length == 0)
            {
                if (string.IsNullOrEmpty(error)) error = "Screenshot capture failed";
                await SendAsync(new { type = "result", taskId, success = false, output = "", error, exitCode = 1 });
                return;
            }
            var outB64 = Convert.ToBase64String(png);
            await SendAsync(new { type = "result", taskId, success = true, output = outB64, error = "", exitCode = 0 });
        }
        catch (Exception ex)
        {
            LogLine($"Capture exception: {ex.Message}");
            await SendAsync(new { type = "result", taskId, success = false, output = "", error = ex.Message, exitCode = 1 });
        }
    }

    private static byte[]? TryCaptureDirect() => ScreenCapture.CaptureBytes();

    private async Task SendAsync(object payload)
    {
        if (_ws?.State != WebSocketState.Open) return;
        var json = JsonSerializer.Serialize(payload);
        var bytes = Encoding.UTF8.GetBytes(json);
        var seg = new ArraySegment<byte>(bytes);
        await _sendGate.WaitAsync(_cts.Token);
        try
        {
            if (_ws?.State == WebSocketState.Open)
                await _ws.SendAsync(seg, WebSocketMessageType.Text, true, _cts.Token);
        }
        finally
        {
            _sendGate.Release();
        }
    }

    private static string GetString(JsonElement el, string name)
        => el.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.String ? p.GetString() ?? "" : "";

    private void LogLine(string s) => Log?.Invoke(s);
}
