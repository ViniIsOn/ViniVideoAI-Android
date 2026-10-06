package com.vini.videoai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 10;
    private static final int EXPORT_JSON = 11;
    private static final String PREFS = "vinivideo";
    private static final String PROJECTS = "projects";
    private static final String BACKEND = "backend";
    private static final String API_KEY = "api_key";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;
    private EditText promptInput;
    private EditText durationInput;
    private Spinner aspectSpinner;
    private Spinner modelSpinner;
    private TextView referenceLabel;
    private String referenceUri = "";
    private Project exportTarget;

    private final int bg = Color.rgb(7,17,31);
    private final int panel = Color.rgb(13,27,43);
    private final int panelAlt = Color.rgb(18,38,61);
    private final int gold = Color.rgb(242,193,78);
    private final int cyan = Color.rgb(50,213,255);
    private final int green = Color.rgb(66,211,146);
    private final int text = Color.rgb(244,248,255);
    private final int muted = Color.rgb(168,184,204);
    private final int danger = Color.rgb(255,107,107);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showHome();
    }

    private void baseScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(42));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void showHome() {
        baseScreen();
        header("VINIVIDEO AI", "Seu estúdio de vídeo IA no Android.");
        navBar();

        LinearLayout box = card();
        root.addView(box);

        label(box, "IDEIA DO VÍDEO", gold, 12, true);
        promptInput = new EditText(this);
        promptInput.setHint("Ex.: Uma arara azul atravessa uma cidade durante uma tempestade, com câmera cinematográfica...");
        promptInput.setHintTextColor(muted);
        promptInput.setTextColor(text);
        promptInput.setMinLines(4);
        promptInput.setGravity(Gravity.TOP);
        promptInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        promptInput.setBackground(round(panelAlt, 16));
        box.addView(promptInput, margin(-1, -2, 0, 10));

        label(box, "FORMATO", gold, 12, true);
        aspectSpinner = spinner(new String[]{"9:16", "16:9", "1:1"});
        box.addView(aspectSpinner, margin(-1, -2, 0, 10));

        label(box, "DURAÇÃO TOTAL (5–600 s)", gold, 12, true);
        durationInput = new EditText(this);
        durationInput.setText("30");
        durationInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durationInput.setTextColor(text);
        durationInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        durationInput.setBackground(round(panelAlt, 14));
        box.addView(durationInput, margin(-1, -2, 0, 10));

        label(box, "MOTOR", gold, 12, true);
        modelSpinner = spinner(new String[]{
                "Demo local (storyboard)",
                "Wan 2.2 (backend)",
                "LTX-2 (backend)",
                "HunyuanVideo 1.5 (backend)"
        });
        box.addView(modelSpinner, margin(-1, -2, 0, 10));

        Button ref = button("+ Imagem de referência", panelAlt, cyan);
        ref.setOnClickListener(v -> pickImage());
        box.addView(ref, margin(-1, -2, 0, 8));

        referenceLabel = small("Nenhuma imagem selecionada.", muted);
        box.addView(referenceLabel, margin(-1, -2, 0, 12));

        Button create = button("CRIAR STORYBOARD", cyan, bg);
        create.setOnClickListener(v -> createProject());
        box.addView(create, margin(-1, -2, 0, 12));

        box.addView(small(
                "O modo Demo funciona sem servidor. Os outros motores usam o backend configurado no app.",
                muted));
    }

    private void createProject() {
        String prompt = promptInput.getText().toString().trim();
        if (prompt.length() < 8) {
            toast("Escreva uma ideia mais detalhada.");
            return;
        }

        int seconds = 30;
        try {
            seconds = Integer.parseInt(durationInput.getText().toString().trim());
        } catch (Exception ignored) {}
        seconds = Math.max(5, Math.min(600, seconds));

        Project p = new Project();
        p.prompt = prompt;
        p.durationSeconds = seconds;
        p.aspect = String.valueOf(aspectSpinner.getSelectedItem());
        p.model = String.valueOf(modelSpinner.getSelectedItem());
        p.referenceUri = referenceUri;
        p.scenes.addAll(buildScenes(prompt, seconds, p.aspect));
        saveProject(p);
        showProject(p);
    }

    private List<Scene> buildScenes(String prompt, int totalSeconds, String aspect) {
        List<Scene> out = new ArrayList<>();
        int start = 0;
        int index = 1;
        while (start < totalSeconds) {
            int duration = Math.min(6, totalSeconds - start);
            Scene s = new Scene();
            s.index = index;
            s.startSecond = start;
            s.durationSeconds = duration;

            String beat;
            int phase = (int) (((double) start / Math.max(1, totalSeconds)) * 4);
            if (phase <= 0) beat = "estabeleça o ambiente e o personagem";
            else if (phase == 1) beat = "desenvolva a ação e aumente o movimento";
            else if (phase == 2) beat = "avance o conflito ou objetivo principal";
            else beat = "conduza naturalmente para a conclusão";

            s.prompt = prompt
                    + ". Cena " + index + ": " + beat
                    + ". Preserve rigorosamente personagem, roupa, cores, cenário e direção visual da cena anterior."
                    + " Formato " + aspect + ", movimento de câmera coerente, sem cortes aleatórios.";
            out.add(s);
            start += duration;
            index++;
        }
        return out;
    }

    private void showProject(Project p) {
        baseScreen();
        header("PROJETO", p.aspect + " • " + p.durationSeconds + "s • " + p.model);
        navBar();

        LinearLayout info = card();
        root.addView(info, margin(-1, -2, 0, 10));
        label(info, "PROMPT", gold, 12, true);
        info.addView(body(p.prompt));
        info.addView(small("Status: " + p.status + (p.jobId.isEmpty() ? "" : " • Job " + p.jobId),
                statusColor(p.status)), margin(-1, -2, 0, 4));

        if (!p.referenceUri.isEmpty()) {
            info.addView(small("Referência: selecionada", cyan));
        }

        for (Scene s : p.scenes) {
            LinearLayout c = card();
            root.addView(c, margin(-1, -2, 0, 10));
            label(c,
                    "CENA " + s.index + " • " + s.startSecond + "s–" + (s.startSecond + s.durationSeconds) + "s",
                    cyan, 12, true);
            c.addView(body(s.prompt));
        }

        LinearLayout actions = card();
        root.addView(actions);

        if (p.model.startsWith("Demo")) {
            actions.addView(small(
                    "Storyboard pronto. Escolha Wan/LTX/Hunyuan para enviar a renderização ao seu servidor.",
                    muted), margin(-1, -2, 0, 8));
        } else {
            Button render = button(
                    p.jobId.isEmpty() ? "ENVIAR PARA RENDERIZAÇÃO" : "ATUALIZAR STATUS",
                    green, bg);
            render.setOnClickListener(v -> {
                if (p.jobId.isEmpty()) submitProject(p);
                else refreshProject(p);
            });
            actions.addView(render, margin(-1, -2, 0, 8));
        }

        Button export = button("EXPORTAR STORYBOARD JSON", panelAlt, text);
        export.setOnClickListener(v -> exportJson(p));
        actions.addView(export, margin(-1, -2, 0, 8));

        if (!p.outputUrl.isEmpty()) {
            Button download = button("BAIXAR MP4", cyan, bg);
            download.setOnClickListener(v -> downloadVideo(p.outputUrl, p.id));
            actions.addView(download);
        }
    }

    private void submitProject(Project p) {
        String backend = prefs.getString(BACKEND, "").trim();
        if (backend.isEmpty()) {
            toast("Configure o backend primeiro.");
            showSettings();
            return;
        }

        p.status = "ENVIANDO";
        saveProject(p);
        showProject(p);

        executor.execute(() -> {
            try {
                JSONObject response = request(
                        "POST",
                        join(backend, "/api/v1/jobs"),
                        p.toRequestJson());
                p.jobId = response.optString("job_id", response.optString("id", ""));
                p.status = response.optString("status", "QUEUED");
                p.outputUrl = response.optString("output_url", "");
                saveProject(p);
                runOnUiThread(() -> {
                    toast("Job enviado.");
                    showProject(p);
                });
            } catch (Exception e) {
                p.status = "ERRO";
                saveProject(p);
                runOnUiThread(() -> {
                    toast("Falha: " + compact(e.getMessage()));
                    showProject(p);
                });
            }
        });
    }

    private void refreshProject(Project p) {
        String backend = prefs.getString(BACKEND, "").trim();
        if (backend.isEmpty()) {
            showSettings();
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject response = request(
                        "GET",
                        join(backend, "/api/v1/jobs/" + p.jobId),
                        null);
                String status = response.optString("status", "");
                String output = response.optString("output_url", "");
                if (!status.isEmpty()) p.status = status;
                if (!output.isEmpty()) p.outputUrl = output;
                saveProject(p);
                runOnUiThread(() -> showProject(p));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Falha ao consultar: " + compact(e.getMessage())));
            }
        });
    }

    private JSONObject request(String method, String endpoint, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Accept", "application/json");

        String key = prefs.getString(API_KEY, "").trim();
        if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String response = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + response);
        }
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    private void showHistory() {
        baseScreen();
        header("HISTÓRICO", "Storyboards e jobs salvos neste aparelho.");
        navBar();

        List<Project> all = loadProjects();
        if (all.isEmpty()) {
            root.addView(small("Nenhum projeto criado ainda.", muted));
            return;
        }

        for (Project p : all) {
            LinearLayout c = card();
            root.addView(c, margin(-1, -2, 0, 10));
            label(c, truncate(p.prompt, 72), text, 16, true);
            c.addView(small(
                    p.aspect + " • " + p.durationSeconds + "s • " + p.status + "\n"
                            + DateFormat.getDateTimeInstance().format(new Date(p.createdAt)),
                    muted), margin(-1, -2, 0, 8));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            Button open = button("ABRIR", panelAlt, cyan);
            open.setOnClickListener(v -> showProject(p));
            row.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1));

            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(dp(8), 1));

            Button delete = button("EXCLUIR", panelAlt, danger);
            delete.setOnClickListener(v -> confirmDelete(p));
            row.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1));

            c.addView(row);
        }
    }

    private void confirmDelete(Project p) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir projeto?")
                .setMessage("O storyboard será removido do histórico deste aparelho.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    deleteProject(p.id);
                    showHistory();
                })
                .show();
    }

    private void showSettings() {
        baseScreen();
        header("BACKEND", "Conecte uma GPU ou servidor self-hosted.");
        navBar();

        LinearLayout c = card();
        root.addView(c);

        label(c, "URL DO SERVIDOR", gold, 12, true);
        EditText url = edit(prefs.getString(BACKEND, ""), "http://192.168.0.10:8000");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        c.addView(url, margin(-1, -2, 0, 10));

        label(c, "API KEY (OPCIONAL)", gold, 12, true);
        EditText key = edit(prefs.getString(API_KEY, ""), "Chave do seu servidor");
        c.addView(key, margin(-1, -2, 0, 12));

        Button save = button("SALVAR CONFIGURAÇÃO", cyan, bg);
        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString(BACKEND, url.getText().toString().trim())
                    .putString(API_KEY, key.getText().toString().trim())
                    .apply();
            toast("Configuração salva.");
            showHome();
        });
        c.addView(save, margin(-1, -2, 0, 12));

        c.addView(small(
                "API esperada: POST /api/v1/jobs e GET /api/v1/jobs/{id}. "
                        + "A imagem de referência já fica registrada no projeto; upload binário entra na próxima versão.",
                muted));
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    private void exportJson(Project p) {
        exportTarget = p;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "vinivideo-" + p.id.substring(0, 8) + ".json");
        startActivityForResult(i, EXPORT_JSON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        if (requestCode == PICK_IMAGE) {
            referenceUri = uri.toString();
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            if (referenceLabel != null) referenceLabel.setText("Imagem selecionada ✓");
        } else if (requestCode == EXPORT_JSON && exportTarget != null) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(exportTarget.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
                    toast("Storyboard exportado.");
                }
            } catch (Exception e) {
                toast("Não foi possível exportar.");
            }
        }
    }

    private void downloadVideo(String url, String id) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("ViniVideo AI");
            request.setDescription("Baixando vídeo renderizado");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "ViniVideo-" + id.substring(0, 8) + ".mp4");
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);
            toast("Download iniciado.");
        } catch (Exception e) {
            toast("URL do vídeo inválida.");
        }
    }

    private void saveProject(Project project) {
        List<Project> all = loadProjects();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(project.id)) {
                all.set(i, project);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(0, project);

        JSONArray a = new JSONArray();
        try {
            for (Project p : all) a.put(p.toJson());
            prefs.edit().putString(PROJECTS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private List<Project> loadProjects() {
        List<Project> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(PROJECTS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(Project.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void deleteProject(String id) {
        List<Project> all = loadProjects();
        JSONArray a = new JSONArray();
        try {
            for (Project p : all) {
                if (!p.id.equals(id)) a.put(p.toJson());
            }
            prefs.edit().putString(PROJECTS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void navBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(16));

        Button home = miniButton("CRIAR");
        home.setOnClickListener(v -> showHome());
        Button history = miniButton("HISTÓRICO");
        history.setOnClickListener(v -> showHistory());
        Button settings = miniButton("BACKEND");
        settings.setOnClickListener(v -> showSettings());

        row.addView(home, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.addView(space());
        row.addView(history, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.addView(space());
        row.addView(settings, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(row);
    }

    private void header(String title, String subtitle) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(text);
        t.setTextSize(27);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(t);

        TextView s = small(subtitle, muted);
        root.addView(s, margin(-1, -2, 0, 18));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(round(panel, 18));
        return c;
    }

    private void label(LinearLayout parent, String value, int color, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(v, margin(-1, -2, 0, 6));
    }

    private TextView body(String value) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(text);
        v.setTextSize(15);
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    private TextView small(String value, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(13);
        v.setLineSpacing(0, 1.1f);
        return v;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(round(panelAlt, 14));
        return e;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(text);
                v.setBackgroundColor(panelAlt);
                v.setPadding(dp(12), dp(12), dp(12), dp(12));
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(text);
                v.setBackgroundColor(panelAlt);
                v.setPadding(dp(12), dp(12), dp(12), dp(12));
                return v;
            }
        };
        s.setAdapter(a);
        s.setBackground(round(panelAlt, 14));
        return s;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(foreground);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(background, 14));
        return b;
    }

    private Button miniButton(String value) {
        return button(value, panelAlt, text);
    }

    private View space() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(7), 1));
        return v;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), Color.rgb(30, 58, 86));
        return g;
    }

    private LinearLayout.LayoutParams margin(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private int statusColor(String status) {
        String s = status == null ? "" : status.toUpperCase();
        if (s.contains("DONE") || s.contains("CONCLU")) return green;
        if (s.contains("ERRO") || s.contains("FAIL")) return danger;
        if (s.contains("RUN") || s.contains("ENVI")) return cyan;
        return muted;
    }

    private static String normalizeModel(String value) {
        String s = value.toLowerCase();
        if (s.contains("wan")) return "wan2.2";
        if (s.contains("ltx")) return "ltx-2";
        if (s.contains("hunyuan")) return "hunyuanvideo-1.5";
        return "storyboard-local";
    }

    private String join(String base, String path) {
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private String compact(String value) {
        if (value == null || value.trim().isEmpty()) return "erro desconhecido";
        return value.length() > 100 ? value.substring(0, 100) + "…" : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class Scene {
        int index;
        int startSecond;
        int durationSeconds;
        String prompt = "";

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("index", index);
            o.put("start_second", startSecond);
            o.put("duration_seconds", durationSeconds);
            o.put("prompt", prompt);
            return o;
        }

        static Scene fromJson(JSONObject o) {
            Scene s = new Scene();
            s.index = o.optInt("index");
            s.startSecond = o.optInt("start_second");
            s.durationSeconds = o.optInt("duration_seconds");
            s.prompt = o.optString("prompt", "");
            return s;
        }
    }

    private static class Project {
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String prompt = "";
        String aspect = "9:16";
        int durationSeconds = 30;
        String model = "Demo local (storyboard)";
        String referenceUri = "";
        String jobId = "";
        String status = "RASCUNHO";
        String outputUrl = "";
        final List<Scene> scenes = new ArrayList<>();

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("created_at", createdAt);
            o.put("prompt", prompt);
            o.put("aspect_ratio", aspect);
            o.put("duration_seconds", durationSeconds);
            o.put("model", model);
            o.put("reference_uri", referenceUri);
            o.put("job_id", jobId);
            o.put("status", status);
            o.put("output_url", outputUrl);

            JSONArray a = new JSONArray();
            for (Scene s : scenes) a.put(s.toJson());
            o.put("scenes", a);
            return o;
        }

        JSONObject toRequestJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("prompt", prompt);
            o.put("aspect_ratio", aspect);
            o.put("duration_seconds", durationSeconds);
            o.put("model", normalizeModel(model));
            if (!referenceUri.isEmpty()) o.put("reference_uri", referenceUri);

            JSONArray a = new JSONArray();
            for (Scene s : scenes) a.put(s.toJson());
            o.put("scenes", a);
            return o;
        }

        static Project fromJson(JSONObject o) {
            Project p = new Project();
            p.id = o.optString("id", p.id);
            p.createdAt = o.optLong("created_at", p.createdAt);
            p.prompt = o.optString("prompt", "");
            p.aspect = o.optString("aspect_ratio", "9:16");
            p.durationSeconds = o.optInt("duration_seconds", 30);
            p.model = o.optString("model", "Demo local (storyboard)");
            p.referenceUri = o.optString("reference_uri", "");
            p.jobId = o.optString("job_id", "");
            p.status = o.optString("status", "RASCUNHO");
            p.outputUrl = o.optString("output_url", "");

            JSONArray a = o.optJSONArray("scenes");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject scene = a.optJSONObject(i);
                    if (scene != null) p.scenes.add(Scene.fromJson(scene));
                }
            }
            return p;
        }
    }
}
