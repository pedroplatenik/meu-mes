// Meu Mês para Windows: uma janela de programa com o mesmo app do celular dentro (WebView2 do Windows).
// Os dados ficam em Documentos\Meu Mês\meus-dados.json, no mesmo formato do Android e do iPhone.
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

[assembly: AssemblyTitle("Meu Mês")]
[assembly: AssemblyProduct("Meu Mês")]
[assembly: AssemblyDescription("Contas, ganhos e quanto sobra no mês")]
[assembly: AssemblyVersion("2.0.0.0")]
[assembly: AssemblyFileVersion("2.0.0.0")]

namespace MeuMes
{
    static class Programa
    {
        [DllImport("user32.dll")] static extern bool SetProcessDpiAwarenessContext(IntPtr v);
        [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern IntPtr FindWindow(string cls, string titulo);
        [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
        [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int cmd);
        [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);

        [STAThread]
        static void Main()
        {
            try { if (!SetProcessDpiAwarenessContext(new IntPtr(-4))) SetProcessDPIAware(); }
            catch { try { SetProcessDPIAware(); } catch { } }

            bool novo;
            using (var trava = new Mutex(true, "MeuMes.Windows.Janela.v2", out novo))
            {
                if (!novo)
                {
                    // já está aberto: traz a janela para a frente
                    IntPtr h = FindWindow(null, Janela.Titulo);
                    if (h != IntPtr.Zero) { if (IsIconic(h)) ShowWindow(h, 9); SetForegroundWindow(h); }
                    return;
                }
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new Janela());
            }
        }
    }

    class Janela : Form
    {
        public const string Titulo = "Meu Mês";
        const string Host = "meumes.example";
        static readonly Color Fundo = Color.FromArgb(0xF4, 0xEE, 0xE5);

        readonly WebView2 web;
        readonly string pastaDados, arquivo, anterior, pastaLocal, pastaApp, arqJanela;

        public Janela()
        {
            Text = Titulo;
            BackColor = Fundo;
            StartPosition = FormStartPosition.CenterScreen;
            AutoScaleMode = AutoScaleMode.Dpi;
            AutoScaleDimensions = new SizeF(96f, 96f);
            ClientSize = new Size(480, 860);
            MinimumSize = new Size(380, 560);
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

            pastaDados = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "Meu Mês");
            arquivo = Path.Combine(pastaDados, "meus-dados.json");
            anterior = Path.Combine(pastaDados, "meus-dados.anterior.json");
            pastaLocal = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MeuMes");
            pastaApp = Path.Combine(pastaLocal, "app");
            arqJanela = Path.Combine(pastaLocal, "janela.txt");

            web = new WebView2();
            web.Dock = DockStyle.Fill;
            web.DefaultBackgroundColor = Fundo;
            Controls.Add(web);

            RestaurarJanela();
            Load += async (s, e) => await Iniciar();
            FormClosing += (s, e) => GuardarJanela();
        }

        async Task Iniciar()
        {
            try
            {
                string arq = "win-x86";
                if (Environment.Is64BitProcess)
                    arq = string.Equals(Environment.GetEnvironmentVariable("PROCESSOR_ARCHITECTURE"), "ARM64", StringComparison.OrdinalIgnoreCase) ? "win-arm64" : "win-x64";
                string pastaLoader = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "runtimes", arq, "native");
                if (File.Exists(Path.Combine(pastaLoader, "WebView2Loader.dll"))) CoreWebView2Environment.SetLoaderDllFolderPath(pastaLoader);

                Directory.CreateDirectory(pastaApp);
                using (var r = Assembly.GetExecutingAssembly().GetManifestResourceStream("MeuMes.index.html"))
                using (var w = File.Create(Path.Combine(pastaApp, "index.html")))
                    r.CopyTo(w);

                var amb = await CoreWebView2Environment.CreateAsync(null, Path.Combine(pastaLocal, "WebView2"), null);
                await web.EnsureCoreWebView2Async(amb);
            }
            catch (WebView2RuntimeNotFoundException)
            {
                MostrarFalta();
                return;
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Não consegui abrir o Meu Mês.\n\n" + ex.Message, Titulo, MessageBoxButtons.OK, MessageBoxIcon.Error);
                Close();
                return;
            }

            var cw = web.CoreWebView2;
            cw.Settings.AreDevToolsEnabled = false;
            cw.Settings.AreDefaultContextMenusEnabled = false;
            cw.Settings.IsStatusBarEnabled = false;
            cw.Settings.IsZoomControlEnabled = true;
            try { cw.Settings.AreBrowserAcceleratorKeysEnabled = false; } catch { }
            try { cw.Settings.IsPasswordAutosaveEnabled = false; cw.Settings.IsGeneralAutofillEnabled = false; } catch { }

            cw.SetVirtualHostNameToFolderMapping(Host, pastaApp, CoreWebView2HostResourceAccessKind.DenyCors);
            await cw.AddScriptToExecuteOnDocumentCreatedAsync(
                "window.__MEUMES={plataforma:'windows',texto:" + TextoJs(Ler(arquivo)) + ",anterior:" + TextoJs(Ler(anterior)) + "};");

            cw.WebMessageReceived += Mensagem;
            cw.NewWindowRequested += (s, e) => { e.Handled = true; AbrirFora(e.Uri); };
            cw.NavigationStarting += (s, e) =>
            {
                if (!e.Uri.StartsWith("https://" + Host + "/", StringComparison.OrdinalIgnoreCase)) { e.Cancel = true; AbrirFora(e.Uri); }
            };
            cw.Navigate("https://" + Host + "/index.html");
        }

        // ---------- mensagens do app: "tipo\nconteúdo" ----------
        void Mensagem(object s, CoreWebView2WebMessageReceivedEventArgs e)
        {
            string m;
            try { m = e.TryGetWebMessageAsString(); } catch { return; }
            if (m == null) return;
            int i = m.IndexOf('\n');
            string tipo = i < 0 ? m : m.Substring(0, i), resto = i < 0 ? "" : m.Substring(i + 1);
            try
            {
                switch (tipo)
                {
                    case "salvar": Responder(Salvar(resto) ? "salvo" : "erro"); break;
                    case "exportar": Exportar(resto); break;
                    case "importar": Importar(); break;
                    case "copiar":
                        if (resto.Length > 0) { Clipboard.SetText(resto); Responder("copiado"); }
                        break;
                    case "abrirPasta":
                        Directory.CreateDirectory(pastaDados);
                        Process.Start(new ProcessStartInfo("explorer.exe", "\"" + pastaDados + "\"") { UseShellExecute = true });
                        break;
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, Titulo, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        void Responder(string m) { try { web.CoreWebView2.PostWebMessageAsString(m); } catch { } }

        // grava sem risco de perder: escreve num temporário e troca, guardando a versão anterior
        bool Salvar(string texto)
        {
            if (string.IsNullOrEmpty(texto) || texto[0] != '{') return false;
            try
            {
                Directory.CreateDirectory(pastaDados);
                string tmp = arquivo + ".tmp";
                File.WriteAllText(tmp, texto, new UTF8Encoding(false));
                if (File.Exists(arquivo)) File.Replace(tmp, arquivo, anterior, true);
                else File.Move(tmp, arquivo);
                return true;
            }
            catch { return false; }
        }

        void Exportar(string resto)
        {
            int i = resto.IndexOf('\n');
            if (i < 0) return;
            string nome = resto.Substring(0, i), texto = resto.Substring(i + 1);
            foreach (char c in Path.GetInvalidFileNameChars()) nome = nome.Replace(c, '-');
            using (var d = new SaveFileDialog())
            {
                d.Title = "Salvar cópia de segurança";
                d.Filter = "Cópia do Meu Mês (*.json)|*.json";
                d.FileName = nome;
                d.InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
                if (d.ShowDialog(this) != DialogResult.OK) return;
                File.WriteAllText(d.FileName, texto, new UTF8Encoding(false));
                Responder("exportado");
            }
        }

        void Importar()
        {
            using (var d = new OpenFileDialog())
            {
                d.Title = "Restaurar uma cópia";
                d.Filter = "Cópia do Meu Mês (*.json)|*.json|Todos os arquivos (*.*)|*.*";
                d.InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
                if (d.ShowDialog(this) != DialogResult.OK) return;
                var info = new FileInfo(d.FileName);
                if (info.Length > 5 * 1024 * 1024) { MessageBox.Show(this, "Esse arquivo é grande demais para ser uma cópia do Meu Mês.", Titulo); return; }
                Responder("importado\n" + File.ReadAllText(d.FileName, Encoding.UTF8));
            }
        }

        static void AbrirFora(string uri)
        {
            Uri u;
            if (!Uri.TryCreate(uri, UriKind.Absolute, out u)) return;
            if (u.Scheme != Uri.UriSchemeHttps && u.Scheme != Uri.UriSchemeHttp) return;
            try { Process.Start(new ProcessStartInfo(u.AbsoluteUri) { UseShellExecute = true }); } catch { }
        }

        static string Ler(string caminho)
        {
            try { return File.Exists(caminho) ? File.ReadAllText(caminho, Encoding.UTF8) : null; } catch { return null; }
        }

        // texto -> literal de JavaScript seguro
        static string TextoJs(string t)
        {
            if (t == null) return "null";
            var sb = new StringBuilder(t.Length + 16);
            sb.Append('"');
            foreach (char c in t)
            {
                switch (c)
                {
                    case '"': sb.Append("\\\""); break;
                    case '\\': sb.Append("\\\\"); break;
                    case '\n': sb.Append("\\n"); break;
                    case '\r': sb.Append("\\r"); break;
                    case '\t': sb.Append("\\t"); break;
                    case '<': sb.Append("\\u003c"); break;
                    default:
                        if (c < 0x20 || c == (char)0x2028 || c == (char)0x2029) sb.Append("\\u").Append(((int)c).ToString("x4"));
                        else sb.Append(c);
                        break;
                }
            }
            sb.Append('"');
            return sb.ToString();
        }

        // ---------- sem o componente do Windows ----------
        void MostrarFalta()
        {
            Controls.Clear();
            var painel = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(32), BackColor = Fundo, ColumnCount = 1, RowCount = 4 };
            var t1 = new Label { Text = "Falta um componente do Windows", Font = new Font("Segoe UI", 16f, FontStyle.Bold), AutoSize = true, ForeColor = Color.FromArgb(0x2B, 0x24, 0x33), Margin = new Padding(0, 40, 0, 12) };
            var t2 = new Label { Text = "O Meu Mês usa o \"Microsoft Edge WebView2\", que já vem na maioria dos computadores. Neste ele não foi encontrado.\n\nClique no botão abaixo, baixe e instale o \"Evergreen Bootstrapper\". Depois abra o Meu Mês de novo.", Font = new Font("Segoe UI", 11f), AutoSize = true, MaximumSize = new Size(400, 0), ForeColor = Color.FromArgb(0x55, 0x4C, 0x60) };
            var bt = new Button { Text = "Baixar o componente", Font = new Font("Segoe UI", 11f, FontStyle.Bold), AutoSize = true, Padding = new Padding(14, 8, 14, 8), Margin = new Padding(0, 24, 0, 0), BackColor = Color.FromArgb(0x12, 0x85, 0x5A), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            bt.FlatAppearance.BorderSize = 0;
            bt.Click += (s, e) => AbrirFora("https://developer.microsoft.com/microsoft-edge/webview2/#download");
            painel.Controls.Add(t1); painel.Controls.Add(t2); painel.Controls.Add(bt);
            Controls.Add(painel);
        }

        // ---------- lembra tamanho e posição da janela ----------
        void RestaurarJanela()
        {
            try
            {
                if (!File.Exists(arqJanela)) return;
                var p = File.ReadAllText(arqJanela).Split(';');
                if (p.Length < 5) return;
                var r = new Rectangle(int.Parse(p[0]), int.Parse(p[1]), int.Parse(p[2]), int.Parse(p[3]));
                bool visivel = false;
                foreach (var tela in Screen.AllScreens) if (tela.WorkingArea.IntersectsWith(r)) visivel = true;
                if (!visivel || r.Width < 300 || r.Height < 400) return;
                StartPosition = FormStartPosition.Manual;
                Bounds = r;
                if (p[4] == "max") WindowState = FormWindowState.Maximized;
            }
            catch { }
        }

        void GuardarJanela()
        {
            try
            {
                Directory.CreateDirectory(pastaLocal);
                var r = WindowState == FormWindowState.Normal ? Bounds : RestoreBounds;
                File.WriteAllText(arqJanela, r.X + ";" + r.Y + ";" + r.Width + ";" + r.Height + ";" + (WindowState == FormWindowState.Maximized ? "max" : "normal"));
            }
            catch { }
        }
    }
}
