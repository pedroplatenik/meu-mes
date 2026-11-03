package app.meumes.casa;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_EXPORTAR = 11;
    private static final int REQ_IMPORTAR = 12;
    private static final int REQ_PERMISSAO = 13;
    static final String PERM_NOTIF = "android.permission.POST_NOTIFICATIONS";

    private WebView web;
    private String paraExportar;
    private boolean carregou = false;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        try {
            Window w = getWindow();
            w.setStatusBarColor(0xFFF4EEE5);
            w.setNavigationBarColor(0xFFFBF8F2);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= 0x00000010; // barra de navegação clara
            w.getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable t) { /* aparência apenas */ }

        web = new WebView(this);
        web.setBackgroundColor(0xFFF4EEE5);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Ponte(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url != null && url.startsWith("file:///android_asset/")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { /* nada */ }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) { carregou = true; }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        Lembretes.agendar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (carregou) js("window.app && window.app.aoVoltar && window.app.aoVoltar()");
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("Android"); web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web == null || !carregou) { super.onBackPressed(); return; }
        web.evaluateJavascript("(window.app && window.app.voltar()) ? '1' : '0'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (v == null || !v.contains("1")) MainActivity.super.onBackPressed();
            }
        });
    }

    void js(final String codigo) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() { if (web != null) web.evaluateJavascript(codigo, null); }
        });
    }

    void aviso(String texto) { js("window.app && window.app.aviso(" + JSONObject.quote(texto) + ")"); }

    @Override
    protected void onActivityResult(int req, int res, Intent dados) {
        super.onActivityResult(req, res, dados);
        Uri uri = (res == RESULT_OK && dados != null) ? dados.getData() : null;
        if (req == REQ_EXPORTAR) {
            String conteudo = paraExportar;
            paraExportar = null;
            if (uri == null || conteudo == null) return;
            OutputStream os = null;
            try {
                os = getContentResolver().openOutputStream(uri, "w");
                if (os == null) throw new Exception("sem saída");
                os.write(conteudo.getBytes(StandardCharsets.UTF_8));
                os.flush();
                aviso("Cópia salva ✓");
            } catch (Exception e) {
                aviso("Não consegui salvar a cópia");
            } finally {
                try { if (os != null) os.close(); } catch (Exception e) { /* nada */ }
            }
        } else if (req == REQ_IMPORTAR) {
            if (uri == null) return;
            InputStream is = null;
            try {
                is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("sem entrada");
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n, total = 0;
                while ((n = is.read(b)) > 0) {
                    total += n;
                    if (total > 20 * 1024 * 1024) throw new Exception("grande demais");
                    buf.write(b, 0, n);
                }
                String texto = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                js("window.app && window.app.receberImport(" + JSONObject.quote(texto) + ")");
            } catch (Exception e) {
                aviso("Não consegui abrir esse arquivo");
            } finally {
                try { if (is != null) is.close(); } catch (Exception e) { /* nada */ }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req != REQ_PERMISSAO) return;
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        aviso(ok ? "Lembretes ligados ✓" : "Sem permissão, os lembretes não vão aparecer");
    }

    static boolean podeNotificar(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(PERM_NOTIF) != PackageManager.PERMISSION_GRANTED) return false;
        try {
            Object nm = c.getSystemService(Context.NOTIFICATION_SERVICE);
            Object r = nm.getClass().getMethod("areNotificationsEnabled").invoke(nm);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable t) { /* versão antiga */ }
        return true;
    }

    // ---------- arquivo de dados ----------
    static final Object TRAVA = new Object();

    static String ler(Context c) {
        synchronized (TRAVA) {
            File dir = c.getFilesDir();
            String t = lerArquivo(new File(dir, "dados.json"));
            if (t == null || t.length() == 0) t = lerArquivo(new File(dir, "dados.anterior.json"));
            return t == null ? "" : t;
        }
    }

    static String lerArquivo(File f) {
        if (!f.exists()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception e) { /* nada */ }
        }
    }

    static void gravar(Context c, String texto) {
        if (texto == null || texto.length() == 0) return;
        synchronized (TRAVA) {
            File dir = c.getFilesDir();
            File atual = new File(dir, "dados.json");
            File novo = new File(dir, "dados.novo.json");
            File anterior = new File(dir, "dados.anterior.json");
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(novo);
                out.write(texto.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
                out.close();
                out = null;
                if (atual.exists()) {
                    if (anterior.exists()) anterior.delete();
                    atual.renameTo(anterior);
                }
                if (!novo.renameTo(atual)) {
                    // último recurso: escreve direto
                    FileOutputStream o2 = new FileOutputStream(atual);
                    o2.write(texto.getBytes(StandardCharsets.UTF_8));
                    o2.close();
                }
            } catch (Exception e) {
                /* mantém a versão anterior */
            } finally {
                try { if (out != null) out.close(); } catch (Exception e) { /* nada */ }
            }
        }
    }

    // ---------- ponte com a tela ----------
    class Ponte {
        @JavascriptInterface
        public String carregar() { return ler(MainActivity.this); }

        @JavascriptInterface
        public void salvar(String texto) { gravar(MainActivity.this, texto); }

        @JavascriptInterface
        public void agendarLembretes(String json) {
            getSharedPreferences(Lembretes.PREFS, MODE_PRIVATE).edit().putString("json", json).apply();
            Lembretes.agendar(MainActivity.this);
        }

        @JavascriptInterface
        public void pedirNotificacao() {
            if (Build.VERSION.SDK_INT < 33) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (checkSelfPermission(PERM_NOTIF) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{PERM_NOTIF}, REQ_PERMISSAO);
                    }
                }
            });
        }

        @JavascriptInterface
        public String avisoTeste() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(PERM_NOTIF) != PackageManager.PERMISSION_GRANTED) {
                pedirNotificacao();
                return "permissao";
            }
            if (!podeNotificar(MainActivity.this)) return "desligado";
            List<String> l = new ArrayList<String>();
            l.add("Os lembretes estão funcionando ✓");
            Lembretes.notificar(MainActivity.this, "Meu Mês", l);
            return "ok";
        }

        @JavascriptInterface
        public void exportar(final String nome, String conteudo) {
            paraExportar = conteudo;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_TITLE, nome);
                        startActivityForResult(i, REQ_EXPORTAR);
                    } catch (Exception e) {
                        paraExportar = null;
                        aviso("Não consegui abrir a tela de salvar");
                    }
                }
            });
        }

        @JavascriptInterface
        public void importar() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                        startActivityForResult(i, REQ_IMPORTAR);
                    } catch (Exception e) {
                        aviso("Não consegui abrir os arquivos");
                    }
                }
            });
        }

        @JavascriptInterface
        public void compartilhar(final String texto) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, texto);
                        startActivity(Intent.createChooser(i, "Enviar resumo do mês"));
                    } catch (Exception e) {
                        aviso("Não consegui compartilhar");
                    }
                }
            });
        }

        @JavascriptInterface
        public void vibrar() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try { if (web != null) web.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); } catch (Exception e) { /* nada */ }
                }
            });
        }
    }
}
