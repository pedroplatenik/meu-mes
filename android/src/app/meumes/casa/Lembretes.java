package app.meumes.casa;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Recebe o alarme diário, mostra os avisos do dia e agenda o próximo. */
public class Lembretes extends BroadcastReceiver {

    static final String PREFS = "lembretes";
    static final String ACAO = "app.meumes.casa.LEMBRETE";
    static final String CANAL = "contas";
    static final int FLAG_IMMUTABLE = 0x04000000;

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (i != null && ACAO.equals(i.getAction())) mostrarDeHoje(c);
        } catch (Throwable t) { /* nunca derrubar o app */ }
        try { agendar(c); } catch (Throwable t) { /* nada */ }
    }

    static PendingIntent alarme(Context c) {
        Intent i = new Intent(c, Lembretes.class);
        i.setAction(ACAO);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    }

    static void agendar(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = alarme(c);
            String json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", null);
            if (json == null) return;
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("ativo", false)) { am.cancel(pi); return; }
            int hora = Math.max(0, Math.min(23, o.optInt("hora", 9)));
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hora);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis() + 30000L) cal.add(Calendar.DAY_OF_MONTH, 1);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } catch (Throwable t) { /* nada */ }
    }

    static String hoje() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    static void mostrarDeHoje(Context c) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = p.getString("json", null);
        if (json == null) return;
        JSONObject o = new JSONObject(json);
        if (!o.optBoolean("ativo", false)) return;
        String h = hoje();
        if (h.equals(p.getString("mostrado", ""))) return;
        JSONArray itens = o.optJSONArray("itens");
        List<String> linhas = new ArrayList<String>();
        if (itens != null) {
            for (int k = 0; k < itens.length(); k++) {
                JSONObject it = itens.optJSONObject(k);
                if (it != null && h.equals(it.optString("d"))) linhas.add(it.optString("t"));
            }
        }
        p.edit().putString("mostrado", h).apply();
        if (linhas.isEmpty()) return;
        boolean soGanho = true;
        for (String l : linhas) if (!l.startsWith("Hoje cai")) soGanho = false;
        notificar(c, soGanho ? "Dinheiro entrando 💰" : "Contas para pagar 📅", linhas);
    }

    static void criarCanal(NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            Class<?> k = Class.forName("android.app.NotificationChannel");
            Object canal = k.getConstructor(String.class, CharSequence.class, int.class).newInstance(CANAL, "Lembretes de contas", 4);
            k.getMethod("setDescription", String.class).invoke(canal, "Avisos antes das contas vencerem");
            nm.getClass().getMethod("createNotificationChannel", k).invoke(nm, canal);
        } catch (Throwable t) { /* nada */ }
    }

    @SuppressWarnings("deprecation")
    static void notificar(Context c, String titulo, List<String> linhas) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || linhas.isEmpty()) return;
            criarCanal(nm);
            Intent abrir = new Intent(c, MainActivity.class);
            abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 2, abrir, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
            Notification.Builder b = new Notification.Builder(c);
            if (Build.VERSION.SDK_INT >= 26) {
                try { b.getClass().getMethod("setChannelId", String.class).invoke(b, CANAL); } catch (Throwable t) { /* nada */ }
            }
            b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(titulo)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(0xFF12855A)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setShowWhen(true);
            if (linhas.size() == 1) {
                b.setContentText(linhas.get(0));
                b.setStyle(new Notification.BigTextStyle().bigText(linhas.get(0)));
            } else {
                b.setContentText(linhas.get(0) + " e mais " + (linhas.size() - 1));
                Notification.InboxStyle st = new Notification.InboxStyle();
                for (String l : linhas) st.addLine(l);
                st.setBigContentTitle(titulo);
                b.setStyle(st);
            }
            nm.notify(10, b.build());
        } catch (Throwable t) { /* nada */ }
    }
}
