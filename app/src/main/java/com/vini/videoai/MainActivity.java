package com.vini.videoai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 10;
    private static final int PICK_VIDEO = 11;
    private static final String PREFS = "vinivideo";
    private static final String PROJECTS = "projects";
    private static final String BACKEND = "backend";
    private static final String API_KEY = "api_key";
    private static final String BACKEND_OK = "backend_ok";
    private static final String LAST_SCREEN = "last_screen";
    private static final String RETURN_PROJECT = "return_project_id";

    private static final String DRAFT_PROMPT = "draft_prompt";
    private static final String DRAFT_ASPECT = "draft_aspect";
    private static final String DRAFT_DURATION = "draft_duration";
    private static final String DRAFT_MODEL = "draft_model";
    private static final String DRAFT_QUALITY = "draft_quality";
    private static final String DRAFT_REFERENCE = "draft_reference";
    private static final String DRAFT_DIRECTOR = "draft_director";
    private static final String DRAFT_CONTINUITY = "draft_continuity";
    private static final String DRAFT_AUDIO = "draft_audio";
    private static final String DRAFT_STYLE = "draft_style";

    private static final String KAGGLE_USERNAME = "kaggle_username";
    private static final String KAGGLE_KEY_ENC = "kaggle_key_enc";
    private static final String KAGGLE_TOKEN_ENC = "kaggle_token_enc";
    private static final String KAGGLE_TOKEN_SCOPES = "kaggle_token_scopes";
    private static final String KAGGLE_SCOPES_OK = "kaggle_scopes_ok";
    private static final String KAGGLE_KEY_ALIAS = "vinivideo_kaggle_key";
    private static final String KAGGLE_API_BASE = "https://api.kaggle.com/v1";
    private static final String KAGGLE_SAVE_KERNEL =
            KAGGLE_API_BASE + "/kernels.KernelsApiService/SaveKernel";
    private static final String KAGGLE_DELETE_KERNEL =
            KAGGLE_API_BASE + "/kernels.KernelsApiService/DeleteKernel";
    private static final String KAGGLE_STATUS_KERNEL =
            KAGGLE_API_BASE + "/kernels.KernelsApiService/GetKernelSessionStatus";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private LinearLayout root;

    private EditText promptInput;
    private Spinner modelSpinner;
    private Spinner qualitySpinner;
    private Spinner durationSpinner;
    private Spinner styleSpinner;
    private TextView autosaveStatus;
    private ImageView referencePreview;
    private String selectedAspect = "9:16";
    private String referenceUri = "";
    private CheckBox directorCheck;
    private CheckBox continuityCheck;
    private CheckBox audioCheck;
    private String pendingImportProjectId = "";

    private final int bg = Color.rgb(5, 11, 20);
    private final int panel = Color.rgb(11, 24, 39);
    private final int panelAlt = Color.rgb(17, 37, 59);
    private final int border = Color.rgb(35, 68, 99);
    private final int cyan = Color.rgb(50, 213, 255);
    private final int cyanDark = Color.rgb(10, 54, 70);
    private final int gold = Color.rgb(242, 193, 78);
    private final int green = Color.rgb(68, 213, 151);
    private final int text = Color.rgb(244, 248, 255);
    private final int muted = Color.rgb(151, 171, 194);
    private final int danger = Color.rgb(255, 105, 120);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(Color.BLACK);
        String lastScreen = prefs.getString(LAST_SCREEN, "CRIAR");
        if ("BACKEND".equals(lastScreen)) {
            showSettings();
        } else if ("PROJETOS".equals(lastScreen)) {
            showProjects();
        } else {
            showCreate();
        }
    }

    private void baseScreen() {
        handler.removeCallbacksAndMessages(null);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(42));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void showCreate() {
        prefs.edit().putString(LAST_SCREEN, "CRIAR").apply();
        baseScreen();
        brand("ViniVideo AI", "Crie sem se perder em configurações");
        navBar("CRIAR");

        LinearLayout hero = card();
        root.addView(hero, margin(-1, -2, 0, 12));
        label(hero, "O QUE ACONTECE NO VÍDEO?", cyan, 12, true);
        hero.addView(small(
                "Escreva só a ação do episódio. O app transforma isso em um prompt completo automaticamente.",
                muted), margin(-1, -2, 0, 10));

        promptInput = edit(
                prefs.getString(DRAFT_PROMPT, ""),
                "Ex.: Poder Azul percebe a câmera e toma um susto...");
        promptInput.setMinLines(5);
        promptInput.setGravity(Gravity.TOP);
        hero.addView(promptInput, margin(-1, -2, 0, 10));

        autosaveStatus = small("Rascunho salvo automaticamente", green);
        hero.addView(autosaveStatus);

        LinearLayout quick = card();
        root.addView(quick, margin(-1, -2, 0, 12));

        sectionLabel(quick, "FORMATO");
        selectedAspect = prefs.getString(DRAFT_ASPECT, "9:16");
        if (!"9:16".equals(selectedAspect) && !"16:9".equals(selectedAspect)) {
            selectedAspect = "9:16";
        }
        quick.addView(aspectSelectorSimple(), margin(-1, -2, 0, 12));

        sectionLabel(quick, "DURAÇÃO");
        durationSpinner = spinner(new String[]{"5 s", "10 s", "15 s"});
        String savedDuration = prefs.getString(DRAFT_DURATION, "5 s");
        if (!"5 s".equals(savedDuration)
                && !"10 s".equals(savedDuration)
                && !"15 s".equals(savedDuration)) {
            savedDuration = "5 s";
        }
        setSpinnerSelection(durationSpinner, savedDuration);
        quick.addView(durationSpinner, margin(-1, dp(52), 0, 10));

        quick.addView(small(
                "Automático: personagem Poder Azul + Cartoon Filme Animado + consistência + Qualidade Rápida",
                green));

        // Configurações avançadas ficam prontas, mas escondidas.
        LinearLayout advanced = card();
        advanced.setVisibility(View.GONE);

        sectionLabel(advanced, "ESTILO");
        styleSpinner = spinner(new String[]{
                "Cartoon Filme Animado",
                "Padrão",
                "Anime",
                "Cinemático",
                "3D Realista"
        });
        setSpinnerSelection(styleSpinner,
                prefs.getString(DRAFT_STYLE, "Cartoon Filme Animado"));
        advanced.addView(styleSpinner, margin(-1, dp(52), 0, 12));

        sectionLabel(advanced, "QUALIDADE");
        qualitySpinner = spinner(new String[]{
                "Rápido", "Qualidade", "Cinema"
        });
        setSpinnerSelection(qualitySpinner,
                prefs.getString(DRAFT_QUALITY, "Rápido"));
        advanced.addView(qualitySpinner, margin(-1, dp(52), 0, 12));

        sectionLabel(advanced, "MODELO / BACKEND PRÓPRIO");
        modelSpinner = spinner(new String[]{
                "Motor grátis automático (Turbo/LTX)",
                "Wan 2.2 (backend próprio)",
                "LTX-2 (backend próprio)",
                "HunyuanVideo 1.5 (backend próprio)"
        });
        setSpinnerSelection(modelSpinner,
                prefs.getString(DRAFT_MODEL, "Motor grátis automático (Turbo/LTX)"));
        advanced.addView(modelSpinner, margin(-1, dp(52), 0, 12));

        sectionLabel(advanced, "REFERÊNCIA VISUAL");
        advanced.addView(small(
                "Opcional. A integração completa de referência ainda está em desenvolvimento.",
                muted), margin(-1, -2, 0, 8));

        referencePreview = new ImageView(this);
        referencePreview.setAdjustViewBounds(true);
        referencePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        referencePreview.setBackground(round(panelAlt, 16));
        advanced.addView(referencePreview, margin(-1, dp(160), 0, 8));

        referenceUri = prefs.getString(DRAFT_REFERENCE, "");
        refreshReferencePreview();

        Button chooseImage = button(
                referenceUri.isEmpty() ? "+ Adicionar imagem" : "Trocar imagem",
                panelAlt, cyan);
        chooseImage.setOnClickListener(v -> pickImage());
        advanced.addView(chooseImage, margin(-1, dp(50), 0, 12));

        sectionLabel(advanced, "DIREÇÃO");
        directorCheck = checkbox(
                "Director AI",
                prefs.getBoolean(DRAFT_DIRECTOR, true));
        continuityCheck = checkbox(
                "Consistência forte",
                prefs.getBoolean(DRAFT_CONTINUITY, true));
        audioCheck = checkbox(
                "Pedir áudio quando o backend suportar",
                prefs.getBoolean(DRAFT_AUDIO, true));
        advanced.addView(directorCheck);
        advanced.addView(continuityCheck);
        advanced.addView(audioCheck);

        Button backendGenerate = button(
                "GERAR COM BACKEND PRÓPRIO",
                panelAlt, text);
        backendGenerate.setOnClickListener(v -> {
            saveDraftNow();
            if (prefs.getString(BACKEND, "").trim().isEmpty()) {
                showBackendRequired();
                return;
            }
            Project p = projectFromDraft();
            p.status = "PREPARANDO";
            p.progress = 1;
            p.stage = "Preparando projeto";
            saveProject(p);
            submitProject(p);
        });
        advanced.addView(backendGenerate, margin(-1, dp(52), 12, 0));

        Button advancedButton = button("⚙ AVANÇADO", panelAlt, muted);
        advancedButton.setOnClickListener(v -> {
            boolean opening = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(opening ? View.VISIBLE : View.GONE);
            advancedButton.setText(opening ? "⚙ OCULTAR AVANÇADO" : "⚙ AVANÇADO");
        });
        root.addView(advancedButton, margin(-1, dp(50), 0, 8));
        root.addView(advanced, margin(-1, -2, 0, 12));

        Button generate = button("🎬 GERAR VÍDEO", cyan, bg);
        generate.setOnClickListener(v -> {
            // Defaults simples, sem obrigar o usuário a decidir tudo.
            if (styleSpinner.getSelectedItem() == null) {
                styleSpinner.setSelection(0);
            }

            String livePrompt = promptInput == null
                    ? ""
                    : promptInput.getText().toString().trim();
            if (livePrompt.length() < 4) {
                toast("Escreva o que acontece no vídeo primeiro.");
                return;
            }

            saveDraftNow();

            Project p = projectFromDraft();
            p.prompt = livePrompt;
            p.status = "PREPARANDO";
            p.progress = 1;
            p.stage = "Preparando envio direto ao Kaggle";
            saveProject(p);
            submitKaggleProjectDirect(p);
        });
        root.addView(generate, margin(-1, dp(62), 0, 10));

        root.addView(small(
                "Fluxo simples: escreva → gerar. O app envia o prompt e inicia o Kaggle sozinho. Sem copiar e colar.",
                muted), margin(-1, -2, 0, 8));

        Button save = button("Salvar rascunho como projeto", panelAlt, text);
        save.setOnClickListener(v -> {
            saveDraftNow();
            Project p = projectFromDraft();
            p.status = "SALVO";
            p.stage = "Aguardando geração";
            saveProject(p);
            toast("Projeto salvo.");
            showProject(p);
        });
        root.addView(save, margin(-1, dp(50), 0, 8));

        attachDraftListeners();
    }

    private View aspectSelectorSimple() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        String[] values = {"9:16", "16:9"};
        for (String value : values) {
            Button b = button(
                    value,
                    value.equals(selectedAspect) ? cyanDark : panelAlt,
                    value.equals(selectedAspect) ? cyan : text);
            b.setTag(value);
            b.setOnClickListener(v -> {
                selectedAspect = String.valueOf(v.getTag());
                saveDraftNow();
                showCreate();
            });
            row.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
            if (!"16:9".equals(value)) {
                row.addView(space(8));
            }
        }
        return row;
    }

    private void backendBanner() {
        boolean configured = !prefs.getString(BACKEND, "").trim().isEmpty();
        boolean tested = prefs.getBoolean(BACKEND_OK, false);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(12), dp(14), dp(12));
        bar.setBackground(round(configured ? (tested ? Color.rgb(12, 55, 44) : cyanDark) : Color.rgb(55, 30, 32), 16));

        TextView dot = new TextView(this);
        dot.setText(configured ? "●" : "●");
        dot.setTextColor(configured ? (tested ? green : gold) : danger);
        dot.setTextSize(14);
        bar.addView(dot, new LinearLayout.LayoutParams(dp(24), -2));

        TextView info = small(
                !configured
                        ? "Nenhum motor conectado — o app ainda não consegue renderizar vídeo."
                        : (tested ? "Motor conectado e testado." : "Servidor configurado — teste a conexão em Backend."),
                configured ? text : Color.rgb(255, 210, 214));
        bar.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        bar.setOnClickListener(v -> showSettings());
        root.addView(bar, margin(-1, -2, 0, 14));
    }

    private View aspectSelector() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        String[] values = {"9:16", "16:9", "1:1"};
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            Button b = button(
                    value,
                    value.equals(selectedAspect) ? cyanDark : panelAlt,
                    value.equals(selectedAspect) ? cyan : text);
            b.setTag(value);
            b.setOnClickListener(v -> {
                selectedAspect = String.valueOf(v.getTag());
                prefs.edit().putString(DRAFT_ASPECT, selectedAspect).apply();
                showCreate();
            });
            row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
            if (i < values.length - 1) {
                View spacer = new View(this);
                row.addView(spacer, new LinearLayout.LayoutParams(dp(8), 1));
            }
        }
        return row;
    }

    private void attachDraftListeners() {
        promptInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString(DRAFT_PROMPT, s.toString()).apply();
                showAutosaved();
            }
        });

        AdapterView.OnItemSelectedListener selectionListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        saveDraftNow();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                };

        durationSpinner.setOnItemSelectedListener(selectionListener);
        modelSpinner.setOnItemSelectedListener(selectionListener);
        qualitySpinner.setOnItemSelectedListener(selectionListener);
        styleSpinner.setOnItemSelectedListener(selectionListener);

        directorCheck.setOnCheckedChangeListener((b, checked) -> saveDraftNow());
        continuityCheck.setOnCheckedChangeListener((b, checked) -> saveDraftNow());
        audioCheck.setOnCheckedChangeListener((b, checked) -> saveDraftNow());
    }

    private void showAutosaved() {
        if (autosaveStatus == null) return;
        autosaveStatus.setText("Salvo agora ✓");
        autosaveStatus.setTextColor(green);
    }

    private void saveDraftNow() {
        SharedPreferences.Editor e = prefs.edit()
                .putString(DRAFT_ASPECT, selectedAspect)
                .putString(DRAFT_REFERENCE, referenceUri);

        if (promptInput != null) e.putString(DRAFT_PROMPT, promptInput.getText().toString());
        if (durationSpinner != null && durationSpinner.getSelectedItem() != null) {
            e.putString(DRAFT_DURATION, String.valueOf(durationSpinner.getSelectedItem()));
        }
        if (modelSpinner != null && modelSpinner.getSelectedItem() != null) {
            e.putString(DRAFT_MODEL, String.valueOf(modelSpinner.getSelectedItem()));
        }
        if (qualitySpinner != null && qualitySpinner.getSelectedItem() != null) {
            e.putString(DRAFT_QUALITY, String.valueOf(qualitySpinner.getSelectedItem()));
        }
        if (styleSpinner != null && styleSpinner.getSelectedItem() != null) {
            e.putString(DRAFT_STYLE, String.valueOf(styleSpinner.getSelectedItem()));
        }
        if (directorCheck != null) e.putBoolean(DRAFT_DIRECTOR, directorCheck.isChecked());
        if (continuityCheck != null) e.putBoolean(DRAFT_CONTINUITY, continuityCheck.isChecked());
        if (audioCheck != null) e.putBoolean(DRAFT_AUDIO, audioCheck.isChecked());
        e.apply();
        showAutosaved();
    }

    private Project projectFromDraft() {
        String prompt = "";
        if (promptInput != null) {
            prompt = promptInput.getText().toString().trim();
        }
        if (prompt.isEmpty()) {
            prompt = prefs.getString(DRAFT_PROMPT, "").trim();
        }
        if (prompt.length() < 4) prompt = "Vídeo sem descrição";

        Project p = new Project();
        p.prompt = prompt;
        p.aspect = prefs.getString(DRAFT_ASPECT, "9:16");
        p.durationSeconds = durationSeconds(
                prefs.getString(DRAFT_DURATION, "5 s"));
        p.model = prefs.getString(DRAFT_MODEL, "Motor grátis automático (Turbo/LTX)");
        p.quality = prefs.getString(DRAFT_QUALITY, "Rápido");
        p.style = prefs.getString(DRAFT_STYLE, "Cartoon Filme Animado");
        p.referenceUri = prefs.getString(DRAFT_REFERENCE, "");
        p.directorMode = prefs.getBoolean(DRAFT_DIRECTOR, true);
        p.strongContinuity = prefs.getBoolean(DRAFT_CONTINUITY, true);
        p.generateAudio = prefs.getBoolean(DRAFT_AUDIO, true);
        p.seed = Math.abs((p.prompt + p.createdAt).hashCode());
        p.scenes.addAll(buildScenes(p));
        return p;
    }

    private List<Scene> buildScenes(Project p) {
        List<Scene> out = new ArrayList<>();
        String[] cameras = {
                "plano geral com movimento suave",
                "plano médio acompanhando o personagem",
                "close-up expressivo e estável",
                "travelling lateral com paralaxe",
                "câmera seguindo por trás e aproximando",
                "plano cinematográfico de encerramento"
        };

        int start = 0;
        int index = 1;
        while (start < p.durationSeconds) {
            int duration = Math.min(6, p.durationSeconds - start);
            float ratio = start / (float) Math.max(1, p.durationSeconds);
            String beat;
            String title;

            if (ratio < 0.15f) {
                title = "Abertura";
                beat = "apresente claramente o ambiente, o personagem principal e a situação inicial";
            } else if (ratio < 0.35f) {
                title = "Encontro";
                beat = "faça a história avançar com uma descoberta, encontro ou nova ação";
            } else if (ratio < 0.60f) {
                title = "Desenvolvimento";
                beat = "desenvolva a interação e aumente gradualmente o movimento";
            } else if (ratio < 0.82f) {
                title = "Clímax";
                beat = "entregue o momento mais importante sem perder a identidade visual";
            } else {
                title = "Fechamento";
                beat = "conclua a ação de forma visualmente clara e natural";
            }

            Scene s = new Scene();
            s.index = index;
            s.title = title;
            s.startSecond = start;
            s.durationSeconds = duration;
            String continuity = p.strongContinuity
                    ? " Use o último frame da cena anterior como referência visual e preserve exatamente identidade, proporções, roupa, paleta, iluminação e cenário."
                    : "";

            s.prompt = buildAutomaticKagglePrompt(p.prompt, p.style)
                    + ". Objetivo desta tomada: " + beat + ". "
                    + cameras[(index - 1) % cameras.length]
                    + ". Evite reiniciar cenário, trocar design do personagem ou inserir cortes aleatórios."
                    + continuity;

            out.add(s);
            start += duration;
            index++;
        }
        return out;
    }

    private void showProject(Project p) {
        baseScreen();
        brand("Projeto", truncate(p.prompt, 54));
        navBar("PROJETOS");

        LinearLayout previewCard = card();
        root.addView(previewCard, margin(-1, -2, 0, 12));
        sectionLabel(previewCard, "PREVIEW");

        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(round(Color.BLACK, 18));
        previewCard.addView(preview, margin(-1, previewHeight(p.aspect), 0, 12));

        boolean hasImportedVideo = p.importedVideoUri != null
                && !p.importedVideoUri.isEmpty();

        if (hasImportedVideo || !p.outputUrl.isEmpty()) {
            VideoView video = new VideoView(this);
            video.setBackgroundColor(Color.BLACK);
            preview.addView(video, new FrameLayout.LayoutParams(-1, -1));

            TextView playHint = centered("▶  REPRODUZIR VÍDEO", text, 16);
            playHint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            playHint.setBackgroundColor(Color.argb(145, 0, 0, 0));
            preview.addView(playHint, new FrameLayout.LayoutParams(-1, -1));

            String resolved = hasImportedVideo
                    ? p.importedVideoUri
                    : resolveOutputUrl(p.outputUrl);
            MediaController controller = new MediaController(this);
            controller.setAnchorView(video);
            video.setMediaController(controller);
            video.setVideoURI(Uri.parse(resolved));

            video.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                try {
                    video.seekTo(1);
                } catch (Exception ignored) {}
                playHint.setText("▶  REPRODUZIR VÍDEO");
                playHint.setVisibility(View.VISIBLE);
            });

            View.OnClickListener startPlayback = v -> {
                playHint.setVisibility(View.GONE);
                video.start();
            };
            preview.setOnClickListener(startPlayback);
            playHint.setOnClickListener(startPlayback);

            video.setOnCompletionListener(mp -> {
                playHint.setText("↻  REPRODUZIR NOVAMENTE");
                playHint.setVisibility(View.VISIBLE);
            });

            video.setOnErrorListener((mp, what, extra) -> {
                playHint.setText(
                        "Não consegui abrir o preview.\nUse BAIXAR MP4 abaixo."
                );
                playHint.setTextColor(danger);
                playHint.setVisibility(View.VISIBLE);
                return true;
            });

            Button playButton = button("▶ REPRODUZIR", cyanDark, cyan);
            playButton.setOnClickListener(startPlayback);
            previewCard.addView(playButton, margin(-1, dp(50), 0, 8));

            if (!hasImportedVideo) {
                Button downloadNow = button("⬇ BAIXAR MP4", panelAlt, text);
                downloadNow.setOnClickListener(v ->
                        downloadVideo(resolved, p.id));
                previewCard.addView(downloadNow, margin(-1, dp(50), 0, 12));
            } else {
                previewCard.addView(small(
                        "Resultado importado do Kaggle e associado a este projeto.",
                        green), margin(-1, -2, 0, 12));
            }
        } else {
            TextView placeholder = centered(
                    p.jobId.isEmpty()
                            ? "Seu vídeo aparecerá aqui após a geração"
                            : "Renderização em andamento…",
                    muted, 15);
            preview.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView status = pill(p.status, statusColor(p.status));
        statusRow.addView(status, new LinearLayout.LayoutParams(-2, dp(36)));
        TextView meta = small(
                p.aspect + "  •  " + prettyDuration(p.durationSeconds) + "  •  " + p.style,
                muted);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(0, -2, 1);
        metaParams.leftMargin = dp(10);
        statusRow.addView(meta, metaParams);
        previewCard.addView(statusRow, margin(-1, -2, 0, 10));

        ProgressBar progress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(Math.max(0, Math.min(100, p.progress)));
        previewCard.addView(progress, margin(-1, dp(8), 0, 6));
        previewCard.addView(small(
                (p.stage == null || p.stage.isEmpty()) ? "Aguardando" : p.stage,
                p.status.equalsIgnoreCase("ERRO") ? danger : muted));

        if (("ENVIADO".equalsIgnoreCase(p.status)
                || "PREPARANDO".equalsIgnoreCase(p.status)
                || "NA FILA".equalsIgnoreCase(p.status)
                || "GERANDO".equalsIgnoreCase(p.status))
                && p.kaggleSlug != null
                && !p.kaggleSlug.isEmpty()) {
            previewCard.addView(small(
                    "O estado vem do Kaggle. Enquanto ele só informa RUNNING, "
                            + "a porcentagem é uma estimativa visual baseada no tempo decorrido — não um progresso exato do modelo.",
                    green), margin(-1, -2, 8, 0));
        }

        if (p.connectionWarning != null && !p.connectionWarning.isEmpty()) {
            previewCard.addView(small(
                    "Status remoto indisponível: " + p.connectionWarning
                            + "\nSe a execução foi aceita, o Kaggle continua sozinho até concluir ou atingir o limite automático de sessão.",
                    danger), margin(-1, -2, 8, 0));
        }

        if (isTerminal(p.status)
                && !p.status.toUpperCase(Locale.ROOT).contains("ERRO")
                && p.kaggleSlug != null
                && !p.kaggleSlug.isEmpty()) {
            previewCard.addView(small(
                    "✓ Geração finalizada — a sessão de GPU do Kaggle foi encerrada/liberada. "
                            + "O Wi‑Fi do celular permanece como estava.",
                    green), margin(-1, -2, 8, 0));
        }

        LinearLayout details = card();
        details.setVisibility(View.GONE);
        sectionLabel(details, "DETALHES DO PROJETO");
        details.addView(keyValue("Qualidade", p.quality));
        details.addView(keyValue("Estilo", p.style));
        details.addView(keyValue(
                "Continuidade",
                p.strongContinuity ? "Forte" : "Normal"));
        details.addView(keyValue(
                "Director AI",
                p.directorMode ? "Ligado" : "Desligado"));
        details.addView(keyValue(
                "Referência",
                p.referenceUri.isEmpty() ? "Nenhuma" : "Selecionada"));

        if (!p.scenes.isEmpty()) {
            details.addView(small(
                    "O app preparou " + p.scenes.size()
                            + " tomada(s) automaticamente. Você não precisa mexer nelas.",
                    muted), margin(-1, -2, 8, 0));
        }

        Button detailsButton = button("ℹ VER DETALHES", panelAlt, muted);
        detailsButton.setOnClickListener(v -> {
            boolean opening = details.getVisibility() != View.VISIBLE;
            details.setVisibility(opening ? View.VISIBLE : View.GONE);
            detailsButton.setText(opening ? "ℹ OCULTAR DETALHES" : "ℹ VER DETALHES");
        });
        root.addView(detailsButton, margin(-1, dp(50), 0, 8));
        root.addView(details, margin(-1, -2, 0, 12));

        Button kaggleAgain = button("🎬 GERAR DIRETO NO KAGGLE", cyanDark, cyan);
        kaggleAgain.setOnClickListener(v -> submitKaggleProjectDirect(p));
        root.addView(kaggleAgain, margin(-1, dp(56), 0, 8));

        if (p.kaggleOutputPage != null
                && !p.kaggleOutputPage.isEmpty()
                && !p.status.toUpperCase(Locale.ROOT).contains("ERRO")) {
            Button openKaggleOutput = button(
                    "↗ ABRIR EXECUÇÃO REAL NO KAGGLE",
                    cyanDark, cyan);
            openKaggleOutput.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(p.kaggleOutputPage)));
                } catch (Exception e) {
                    toast("Não consegui abrir o resultado do Kaggle.");
                }
            });
            root.addView(
                    openKaggleOutput,
                    margin(-1, dp(54), 0, 8));
        }

        Button importKaggle = button(
                hasImportedVideo
                        ? "📥 TROCAR RESULTADO DO KAGGLE"
                        : "📥 IMPORTAR RESULTADO DO KAGGLE",
                panelAlt, text);
        importKaggle.setOnClickListener(v -> pickVideoForProject(p.id));
        root.addView(importKaggle, margin(-1, dp(54), 0, 10));

        if (p.jobId.isEmpty() && p.outputUrl.isEmpty() && !hasImportedVideo) {
            Button generate = button("AVANÇADO: BACKEND PRÓPRIO", panelAlt, text);
            generate.setOnClickListener(v -> {
                if (prefs.getString(BACKEND, "").trim().isEmpty()) {
                    showBackendRequired();
                } else {
                    submitProject(p);
                }
            });
            root.addView(generate, margin(-1, dp(58), 0, 10));
        }

        if (p.connectionWarning != null
                && !p.connectionWarning.isEmpty()
                && !p.jobId.isEmpty()
                && !isTerminal(p.status)) {
            Button reconnect = button("🔄 RECONECTAR MOTOR E CONTINUAR", panelAlt, cyan);
            reconnect.setOnClickListener(v -> {
                prefs.edit()
                        .putString(RETURN_PROJECT, p.id)
                        .putString(LAST_SCREEN, "BACKEND")
                        .apply();
                showSettings();
            });
            root.addView(reconnect, margin(-1, dp(56), 0, 10));
        }

        Button duplicate = button("Duplicar para editar", panelAlt, text);
        duplicate.setOnClickListener(v -> {
            loadProjectIntoDraft(p);
            showCreate();
        });
        root.addView(duplicate, margin(-1, dp(52), 0, 8));

        if (!p.jobId.isEmpty() && !isTerminal(p.status)) {
            handler.postDelayed(() -> refreshProject(p), 4000);
        }
        if (p.kaggleSlug != null
                && !p.kaggleSlug.isEmpty()
                && !isTerminal(p.status)) {
            handler.postDelayed(
                    () -> refreshKaggleProject(p),
                    7000);
        }
    }

    private void showSceneDetail(Scene scene) {
        new AlertDialog.Builder(this)
                .setTitle(scene.title + " • " + scene.startSecond + "s")
                .setMessage(scene.prompt)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void showProjects() {
        prefs.edit().putString(LAST_SCREEN, "PROJETOS").apply();
        baseScreen();
        brand("Projetos", "Tudo salvo neste aparelho");
        navBar("PROJETOS");

        List<Project> all = loadProjects();
        if (all.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(centered("Nenhum projeto salvo ainda.", muted, 15));
            root.addView(empty);
            return;
        }

        for (Project p : all) {
            LinearLayout c = card();
            root.addView(c, margin(-1, -2, 0, 10));

            TextView title = body(truncate(p.prompt, 84));
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            c.addView(title, margin(-1, -2, 0, 6));
            c.addView(small(
                    p.aspect + " • " + prettyDuration(p.durationSeconds)
                            + " • " + p.model + " • " + p.status,
                    statusColor(p.status)), margin(-1, -2, 0, 4));
            c.addView(small(
                    DateFormat.getDateTimeInstance().format(new Date(p.createdAt)),
                    muted), margin(-1, -2, 0, 10));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button open = button("Abrir", panelAlt, cyan);
            open.setOnClickListener(v -> showProject(p));
            actions.addView(open, new LinearLayout.LayoutParams(0, dp(46), 1));

            View gap = new View(this);
            actions.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

            Button delete = button("Excluir", panelAlt, danger);
            delete.setOnClickListener(v -> confirmDelete(p));
            actions.addView(delete, new LinearLayout.LayoutParams(0, dp(46), 1));

            c.addView(actions);
        }
    }

    private void showSettings() {
        prefs.edit().putString(LAST_SCREEN, "BACKEND").apply();
        baseScreen();
        brand("Backend", "Motor de geração real");
        navBar("BACKEND");

        LinearLayout kaggleDirect = card();
        root.addView(kaggleDirect, margin(-1, -2, 0, 12));
        label(kaggleDirect, "KAGGLE DIRETO — RECOMENDADO", cyan, 12, true);
        kaggleDirect.addView(small(
                "Conecte sua conta uma vez. Depois, o botão Gerar envia o prompt e inicia a GPU do Kaggle sem copiar e colar.",
                muted), margin(-1, -2, 0, 12));

        sectionLabel(kaggleDirect, "USUÁRIO KAGGLE");
        EditText kaggleUser = edit(
                prefs.getString(KAGGLE_USERNAME, ""),
                "Seu nome de usuário no Kaggle");
        kaggleUser.setSingleLine(true);
        kaggleDirect.addView(kaggleUser, margin(-1, dp(54), 0, 10));

        sectionLabel(kaggleDirect, "PERSONAL API TOKEN");
        boolean hasSavedKaggleToken =
                !prefs.getString(KAGGLE_TOKEN_ENC, "").isEmpty();
        EditText kaggleToken = edit(
                "",
                hasSavedKaggleToken
                        ? "Token salvo com segurança — deixe vazio para manter"
                        : "Cole o token KGAT_...");
        kaggleToken.setSingleLine(true);
        kaggleToken.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        kaggleDirect.addView(kaggleToken, margin(-1, dp(54), 0, 8));

        kaggleDirect.addView(small(
                "O Kaggle tem um bug conhecido na consulta de status (kernels.get). "
                        + "Por isso o app testa só o que precisa para trabalhar com segurança: "
                        + "criar um kernel privado SEM GPU e apagá-lo logo depois.",
                muted), margin(-1, -2, 0, 8));

        boolean connectionOk = prefs.getBoolean(KAGGLE_SCOPES_OK, false);
        if (hasSavedKaggleToken) {
            kaggleDirect.addView(small(
                    connectionOk
                            ? "✓ Kaggle pronto: criar/executar e limpar funcionam."
                            : "⚠ Token salvo, mas o teste seguro ainda não passou.",
                    connectionOk ? green : gold), margin(-1, -2, 0, 8));
        }

        Button openTokenSettings = button(
                "ABRIR CONFIGURAÇÕES DE TOKEN KAGGLE",
                panelAlt, cyan);
        openTokenSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.kaggle.com/settings/api")));
            } catch (Exception e) {
                toast("Não consegui abrir as configurações do Kaggle.");
            }
        });
        kaggleDirect.addView(openTokenSettings, margin(-1, dp(50), 0, 8));

        Button connectKaggle = button(
                hasSavedKaggleToken
                        ? "✓ TESTAR KAGGLE"
                        : "CONECTAR E TESTAR",
                cyan, bg);
        connectKaggle.setOnClickListener(v -> {
            String username = kaggleUser.getText().toString().trim();
            String enteredToken =
                    kaggleToken.getText().toString().trim();

            if (username.isEmpty()) {
                toast("Digite seu usuário do Kaggle.");
                return;
            }

            try {
                prefs.edit()
                        .putString(KAGGLE_USERNAME, username)
                        .apply();

                if (!enteredToken.isEmpty()) {
                    saveKaggleToken(enteredToken);
                } else if (!hasSavedKaggleToken) {
                    toast("Cole seu Personal API Token.");
                    return;
                }
            } catch (Exception e) {
                toast("Não consegui salvar o token.");
                return;
            }

            toast("Testando Kaggle sem usar GPU…");
            testKaggleDirect();
        });
        kaggleDirect.addView(connectKaggle, margin(-1, dp(54), 0, 8));

        if (hasSavedKaggleToken) {
            Button disconnectKaggle = button(
                    "Desconectar Kaggle",
                    panelAlt, danger);
            disconnectKaggle.setOnClickListener(v -> {
                prefs.edit()
                        .remove(KAGGLE_USERNAME)
                        .remove(KAGGLE_KEY_ENC)
                        .remove(KAGGLE_TOKEN_ENC)
                        .remove(KAGGLE_TOKEN_SCOPES)
                        .remove(KAGGLE_SCOPES_OK)
                        .apply();
                toast("Kaggle desconectado.");
                showSettings();
            });
            kaggleDirect.addView(disconnectKaggle, margin(-1, dp(48), 0, 0));
        }

        LinearLayout warning = card();
        root.addView(warning, margin(-1, -2, 0, 12));
        label(warning, "IMPORTANTE", gold, 12, true);
        warning.addView(body(
                "O APK não contém uma GPU de vídeo. Para gerar de verdade, ele precisa conversar com um servidor seu ou uma API compatível. Sem isso, nenhum modelo vai renderizar."));
        warning.addView(small(
                "O app não usa créditos próprios. Custos ou limites dependem do lugar onde o modelo roda.",
                muted), margin(-1, -2, 8, 0));

        LinearLayout c = card();
        root.addView(c, margin(-1, -2, 0, 12));

        sectionLabel(c, "URL DO MOTOR");
        EditText url = edit(
                prefs.getString(BACKEND, ""),
                "https://seu-servidor.exemplo");
        c.addView(url, margin(-1, dp(54), 0, 10));

        sectionLabel(c, "API KEY (OPCIONAL)");
        EditText key = edit(
                prefs.getString(API_KEY, ""),
                "Chave do seu próprio servidor");
        c.addView(key, margin(-1, dp(54), 0, 12));

        boolean hasBackend = !prefs.getString(BACKEND, "").trim().isEmpty();
        Button freeEngine = button(
                hasBackend
                        ? "🚀 REABRIR MOTOR GRÁTIS (COLAB)"
                        : "🚀 INICIAR MOTOR GRÁTIS (COLAB)",
                cyanDark,
                cyan);
        freeEngine.setOnClickListener(v -> openFreeColab());
        c.addView(freeEngine, margin(-1, dp(54), 0, 8));

        Button pasteUrl = button("📋 COLAR URL DO COLAB E TESTAR", panelAlt, cyan);
        pasteUrl.setOnClickListener(v -> pasteColabUrlAndTest(url, key));
        c.addView(pasteUrl, margin(-1, dp(54), 0, 8));

        Button kaggleQuality = button("🧰 KAGGLE MANUAL — FALLBACK", panelAlt, text);
        kaggleQuality.setOnClickListener(v -> openKaggleQuality());
        c.addView(kaggleQuality, margin(-1, dp(54), 0, 8));

        Button kaggle = button("🟦 KAGGLE RÁPIDO — PLANO B", panelAlt, text);
        kaggle.setOnClickListener(v -> openKaggleFallback());
        c.addView(kaggle, margin(-1, dp(54), 0, 8));

        c.addView(small(
                "O Kaggle Direto acima é o fluxo principal. Os botões abaixo ficam apenas como fallback manual se a API estiver indisponível.",
                muted), margin(-1, -2, 0, 12));

        Button save = button("Salvar", panelAlt, text);
        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString(BACKEND, url.getText().toString().trim())
                    .putString(API_KEY, key.getText().toString().trim())
                    .putBoolean(BACKEND_OK, false)
                    .apply();
            toast("Configuração salva.");
        });
        c.addView(save, margin(-1, dp(50), 0, 8));

        Button test = button("TESTAR CONEXÃO", cyan, bg);
        test.setOnClickListener(v -> {
            String endpoint = url.getText().toString().trim();
            prefs.edit()
                    .putString(BACKEND, endpoint)
                    .putString(API_KEY, key.getText().toString().trim())
                    .apply();
            testBackend(endpoint);
        });
        c.addView(test, margin(-1, dp(54), 0, 0));

        LinearLayout api = card();
        root.addView(api);
        sectionLabel(api, "CONTRATO DA API");
        api.addView(small("GET /api/v1/health", cyan));
        api.addView(small("POST /api/v1/jobs", cyan));
        api.addView(small("GET /api/v1/jobs/{id}", cyan));
        api.addView(small(
                "O job deve retornar status, progress (0–100), stage e output_url quando terminar.",
                muted), margin(-1, -2, 8, 0));
    }

    private String styleCode(String style) {
        if (style == null) return "cartoon_movie";
        String s = style.toLowerCase(Locale.ROOT);
        if (s.contains("anime")) return "anime";
        if (s.contains("cinem")) return "cinematic";
        if (s.contains("realista")) return "realistic_3d";
        if (s.contains("padr")) return "default";
        return "cartoon_movie";
    }

    private String stylePrompt(String style) {
        String code = styleCode(style);
        if ("anime".equals(code)) {
            return "anime-inspired animation, clean stylized character design, expressive motion, consistent proportions";
        }
        if ("cinematic".equals(code)) {
            return "cinematic animated film look, controlled lighting, appealing composition, smooth coherent motion";
        }
        if ("realistic_3d".equals(code)) {
            return "high-quality stylized 3D rendering, cinematic lighting, coherent anatomy and stable character identity";
        }
        if ("default".equals(code)) {
            return "clean high-quality animation, stable character identity, coherent motion";
        }
        return "stylized 3D animated feature-film look, clean appealing character shapes, expressive cartoon face, soft stylized materials, colorful production design, smooth coherent animation, avoid uncanny photorealism";
    }

    private String buildAutomaticKagglePrompt(String userPrompt, String style) {
        String idea = userPrompt == null ? "" : userPrompt.trim();
        String lower = idea.toLowerCase(Locale.ROOT);

        StringBuilder out = new StringBuilder();

        if (lower.contains("poder azul")
                || lower.contains("arara azul")
                || lower.contains("blue macaw")) {
            out.append(
                    "Poder Azul is a cute charismatic blue macaw with large expressive eyes, "
                            + "a distinctive clean beak, soft stylized blue feathers and consistent cartoon proportions. "
                            + "A tiny friendly green creature sits on top of his head. "
                            + "Keep both characters visually identical throughout the entire shot. "
                            + "Their faces, eyes, beak, colors, body proportions and the green companion must not change. "
            );
        }

        out.append("User requested action: ").append(idea).append(". ");
        out.append(stylePrompt(style)).append(". ");
        out.append(
                "Follow the requested action closely. Keep the scene simple and readable for a short vertical video. "
                        + "Use expressive cartoon acting, smooth coherent movement, stable anatomy, "
                        + "clean cinematic framing and no random cuts. "
                        + "The character may look directly at the camera when the action implies breaking the fourth wall."
        );

        return out.toString();
    }

    private boolean hasKaggleCredentials() {
        return !prefs.getString(KAGGLE_USERNAME, "").trim().isEmpty()
                && !prefs.getString(KAGGLE_TOKEN_ENC, "").trim().isEmpty();
    }

    private boolean hasKaggleDirectAccess() {
        return hasKaggleCredentials()
                && prefs.getBoolean(KAGGLE_SCOPES_OK, false);
    }

    private void saveKaggleToken(String token) throws Exception {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token Kaggle vazio");
        }

        String clean = token.trim();
        String encrypted = encryptSecret(clean);
        prefs.edit()
                .putString(KAGGLE_TOKEN_ENC, encrypted)
                .putBoolean(KAGGLE_SCOPES_OK, false)
                .remove(KAGGLE_TOKEN_SCOPES)
                .apply();
    }

    private String getKaggleToken() throws Exception {
        String encrypted = prefs.getString(KAGGLE_TOKEN_ENC, "");
        if (encrypted.isEmpty()) return "";
        return decryptSecret(encrypted);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KAGGLE_KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry)
                    keyStore.getEntry(KAGGLE_KEY_ALIAS, null))
                    .getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KAGGLE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT
                        | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private String encryptSecret(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());

        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(
                value.getBytes(StandardCharsets.UTF_8));

        byte[] joined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, joined, 0, iv.length);
        System.arraycopy(
                encrypted, 0, joined, iv.length, encrypted.length);

        return Base64.encodeToString(joined, Base64.NO_WRAP);
    }

    private String decryptSecret(String encoded) throws Exception {
        byte[] joined = Base64.decode(encoded, Base64.NO_WRAP);
        if (joined.length < 13) {
            throw new IllegalStateException("Credencial inválida");
        }

        byte[] iv = new byte[12];
        byte[] encrypted = new byte[joined.length - 12];
        System.arraycopy(joined, 0, iv, 0, 12);
        System.arraycopy(joined, 12, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                new GCMParameterSpec(128, iv));

        return new String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8);
    }

    private void testKaggleDirect() {
        executor.execute(() -> {
            String username =
                    prefs.getString(KAGGLE_USERNAME, "").trim();
            String uniqueSuffix =
                    Long.toHexString(System.currentTimeMillis())
                            + "-"
                            + UUID.randomUUID().toString()
                            .substring(0, 6)
                            .toLowerCase(Locale.ROOT);
            String testSlug =
                    "vinivideo-permission-test-" + uniqueSuffix;
            String testTitle =
                    "ViniVideo permission test " + uniqueSuffix;

            boolean created = false;
            try {
                if (username.isEmpty()) {
                    throw new IllegalStateException(
                            "Digite seu usuário do Kaggle.");
                }

                JSONObject body = new JSONObject();
                body.put("slug", username + "/" + testSlug);
                body.put("newTitle", testTitle);
                body.put(
                        "text",
                        "print('ViniVideo Kaggle permission test OK')\n");
                body.put("language", "python");
                body.put("kernelType", "script");
                body.put("isPrivate", true);
                body.put("enableInternet", false);
                body.put("kernelExecutionType", "QUICK_SAVE");

                try {
                    kaggleRequestRaw(
                            "POST",
                            KAGGLE_SAVE_KERNEL,
                            body);
                } catch (Exception firstError) {
                    String firstMessage = compact(firstError.getMessage());
                    if (!firstMessage.contains("409")) {
                        throw firstError;
                    }

                    uniqueSuffix =
                            Long.toHexString(System.currentTimeMillis())
                                    + "-"
                                    + UUID.randomUUID().toString()
                                    .substring(0, 8)
                                    .toLowerCase(Locale.ROOT);
                    testSlug =
                            "vinivideo-permission-test-" + uniqueSuffix;
                    testTitle =
                            "ViniVideo permission test " + uniqueSuffix;
                    body.put("slug", username + "/" + testSlug);
                    body.put("newTitle", testTitle);

                    kaggleRequestRaw(
                            "POST",
                            KAGGLE_SAVE_KERNEL,
                            body);
                }
                created = true;

                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                JSONObject deleteBody = new JSONObject();
                deleteBody.put("userName", username);
                deleteBody.put("kernelSlug", testSlug);
                kaggleRequestRaw(
                        "POST",
                        KAGGLE_DELETE_KERNEL,
                        deleteBody);
                created = false;

                prefs.edit()
                        .putBoolean(KAGGLE_SCOPES_OK, true)
                        .remove(KAGGLE_TOKEN_SCOPES)
                        .apply();

                runOnUiThread(() -> {
                    toast("Kaggle pronto ✓ Criar e limpar funcionam");
                    showSettings();
                });
            } catch (Exception e) {
                prefs.edit()
                        .putBoolean(KAGGLE_SCOPES_OK, false)
                        .apply();

                if (created) {
                    try {
                        JSONObject deleteBody =
                                new JSONObject();
                        deleteBody.put("userName", username);
                        deleteBody.put("kernelSlug", testSlug);
                        kaggleRequestRaw(
                                "POST",
                                KAGGLE_DELETE_KERNEL,
                                deleteBody);
                    } catch (Exception ignored) {}
                }

                String error = compact(e.getMessage());
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Kaggle ainda não está pronto")
                        .setMessage(
                                explainKaggleTestError(error)
                                        + "\n\nNenhuma GPU foi usada nesse teste.")
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }

    private String explainKaggleTestError(String error) {
        if (error == null) return "Falha desconhecida ao testar o Kaggle.";
        String lower = error.toLowerCase(Locale.ROOT);

        if (lower.contains("kernels.delete") || lower.contains("delete")) {
            return "O token conseguiu criar o kernel, mas não conseguiu limpá-lo. "
                    + "O Kaggle retornou: " + error;
        }
        if (lower.contains("403")) {
            return "O Kaggle recusou uma operação necessária para criar ou limpar kernels: " + error;
        }
        if (lower.contains("401")) {
            return "O Personal API Token não foi aceito. Gere um novo token em Settings → API Tokens e tente novamente.";
        }
        if (lower.contains("409")) {
            return "O Kaggle informou conflito de título. "
                    + "O app já tenta novamente com um título único; se isso aparecer outra vez, "
                    + "mande apenas esta mensagem de erro.";
        }
        if (lower.contains("404")) {
            return "A API do Kaggle não encontrou a operação esperada: " + error;
        }
        return error;
    }

    private void submitKaggleProjectDirect(Project p) {
        if (!hasUsableInternet()) {
            p.status = "SEM INTERNET";
            p.progress = 0;
            p.stage = "Conecte o aparelho à internet";
            saveProject(p);

            new AlertDialog.Builder(this)
                    .setTitle("Sem internet")
                    .setMessage(
                            "O Android não permite que o app ligue o Wi‑Fi sozinho. Posso abrir o painel de internet para você conectar.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton(
                            "Abrir internet",
                            (d, w) -> openInternetPanel())
                    .show();
            return;
        }

        if (!hasKaggleDirectAccess()) {
            p.status = "KAGGLE NÃO TESTADO";
            p.progress = 0;
            p.stage = "Teste criar/limpar no Kaggle antes de gerar";
            saveProject(p);

            new AlertDialog.Builder(this)
                    .setTitle("Teste o Kaggle primeiro")
                    .setMessage(
                            "Antes de gastar GPU, o app precisa provar que consegue criar e limpar um kernel privado de teste sem GPU.")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton(
                            "Testar",
                            (d, w) -> showSettings())
                    .show();
            return;
        }

        p.status = "ENVIANDO";
        p.progress = 3;
        p.kaggleRunningStartedAt = 0L;
        p.stage = "Enviando: " + truncate(p.prompt, 70);
        p.connectionWarning = "";

        String username =
                prefs.getString(KAGGLE_USERNAME, "").trim();
        String slug =
                "vinivideo-"
                        + p.id.substring(0, Math.min(8, p.id.length()))
                        .toLowerCase(Locale.ROOT);
        String kernelTitle = slug;

        p.kaggleOwner = username;
        p.kaggleSlug = slug;
        p.kaggleOutputPage = "";

        saveProject(p);
        showProject(p);

        executor.execute(() -> {
            try {
                String automaticPrompt =
                        buildAutomaticKagglePrompt(
                                p.prompt, p.style);
                String profile;
                if ("Cinema".equalsIgnoreCase(p.quality)) {
                    profile = "MAXIMA";
                } else if ("Qualidade".equalsIgnoreCase(p.quality)) {
                    profile = "QUALIDADE";
                } else {
                    profile = "RAPIDO";
                }

                String script = readAssetText(
                        "kaggle_vinivideo_runner.py")
                        .replace(
                                "__USER_PROMPT_JSON__",
                                JSONObject.quote(automaticPrompt))
                        .replace(
                                "__ASPECT_JSON__",
                                JSONObject.quote(p.aspect))
                        .replace(
                                "__DURATION_SECONDS__",
                                String.valueOf(p.durationSeconds))
                        .replace(
                                "__PROFILE_JSON__",
                                JSONObject.quote(profile))
                        .replace(
                                "__SEED__",
                                String.valueOf(p.seed));

                JSONObject body = new JSONObject();
                body.put("slug", username + "/" + slug);
                body.put("newTitle", kernelTitle);
                body.put("text", script);
                body.put("language", "python");
                body.put("kernelType", "script");
                body.put("isPrivate", true);
                body.put("enableInternet", true);
                body.put("machineShape", "NvidiaTeslaT4");
                int sessionTimeoutSeconds;
                if ("MAXIMA".equals(profile)) {
                    sessionTimeoutSeconds = 2700;
                } else if ("QUALIDADE".equals(profile)) {
                    sessionTimeoutSeconds = 2100;
                } else {
                    sessionTimeoutSeconds = 1200;
                }
                body.put("sessionTimeoutSeconds", sessionTimeoutSeconds);

                String saveResponse = kaggleRequestRaw(
                        "POST",
                        KAGGLE_SAVE_KERNEL,
                        body);

                String returnedUrl = "";
                String returnedRef = "";
                try {
                    JSONObject saved = new JSONObject(saveResponse);
                    returnedUrl = saved.optString("url", "").trim();
                    returnedRef = saved.optString("ref", "").trim();
                } catch (Exception ignored) {}

                p.kaggleRef = returnedRef;

                if (!returnedRef.isEmpty() && returnedRef.contains("/")) {
                    String[] parts = returnedRef.split("/", 2);
                    if (parts.length == 2) {
                        p.kaggleOwner = parts[0];
                        p.kaggleSlug = parts[1];
                    }
                }

                String normalizedReturnedUrl = returnedUrl;
                if (!normalizedReturnedUrl.isEmpty()
                        && normalizedReturnedUrl.startsWith("/")) {
                    normalizedReturnedUrl =
                            "https://www.kaggle.com"
                                    + normalizedReturnedUrl;
                }

                boolean returnedUrlLooksLikeNotebook =
                        normalizedReturnedUrl.contains("kaggle.com/code/");

                if (!returnedRef.isEmpty()) {
                    // O ref é o identificador mais confiável do notebook criado.
                    p.kaggleOutputPage =
                            "https://www.kaggle.com/code/"
                                    + returnedRef
                                    + "/edit";
                } else if (returnedUrlLooksLikeNotebook) {
                    p.kaggleOutputPage =
                            normalizedReturnedUrl.endsWith("/edit")
                                    ? normalizedReturnedUrl
                                    : normalizedReturnedUrl + "/edit";
                } else {
                    p.kaggleOutputPage = "";
                }

                p.status = "ENVIADO";
                p.progress = 15;
                p.stage = p.kaggleOutputPage.isEmpty()
                        ? "Kaggle recebeu o kernel • aguardando estado real da execução"
                        : "Kernel enviado • aguardando estado real do Kaggle";
                saveProject(p);

                runOnUiThread(() -> showProject(p));
            } catch (Exception e) {
                p.status = "ERRO";
                p.progress = 0;
                p.stage = compact(e.getMessage());
                saveProject(p);

                runOnUiThread(() -> showProject(p));
            }
        });
    }

    private void refreshKaggleProject(Project p) {
        if (p.kaggleOwner == null
                || p.kaggleOwner.isEmpty()
                || p.kaggleSlug == null
                || p.kaggleSlug.isEmpty()
                || !hasKaggleCredentials()) {
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("userName", p.kaggleOwner);
                request.put("kernelSlug", p.kaggleSlug);

                String raw = kaggleRequestRaw(
                        "POST",
                        KAGGLE_STATUS_KERNEL,
                        request);

                JSONObject statusJson = new JSONObject(raw);
                String state = statusJson.optString("status", "");
                String failure = statusJson.optString(
                        "failureMessage",
                        statusJson.optString("failure_message", ""));

                String normalized =
                        state.toUpperCase(Locale.ROOT);

                if (normalized.contains("COMPLETE")) {
                    p.status = "CONCLUÍDO";
                    p.progress = 100;
                    p.stage = "Kaggle concluiu a execução • GPU liberada";
                    p.connectionWarning = "";
                } else if (normalized.contains("ERROR")) {
                    p.status = "ERRO";
                    p.progress = 0;
                    p.stage = failure.isEmpty()
                            ? "A execução do Kaggle falhou"
                            : "Kaggle: " + compact(failure);
                } else if (normalized.contains("RUNNING")) {
                    p.status = "GERANDO";
                    if (p.kaggleRunningStartedAt <= 0L) {
                        p.kaggleRunningStartedAt = System.currentTimeMillis();
                    }
                    p.progress = estimatedRunningProgress(p);
                    p.stage = estimatedRunningStage(p);
                    p.connectionWarning = "";
                } else if (normalized.contains("QUEUED")) {
                    p.status = "NA FILA";
                    p.progress = 25;
                    p.stage = "Na fila do Kaggle • aguardando GPU T4";
                    p.connectionWarning = "";
                } else if (normalized.contains("CANCEL")) {
                    p.status = "CANCELADO";
                    p.progress = 0;
                    p.stage = "Execução cancelada no Kaggle";
                } else if (normalized.contains("NEW_SCRIPT")
                        || normalized.isEmpty()) {
                    p.status = "PREPARANDO";
                    p.progress = 15;
                    p.stage = "Kernel criado • Kaggle preparando a execução";
                } else {
                    p.status = state.isEmpty() ? "ENVIADO" : state;
                    p.progress = Math.max(15, p.progress);
                    p.stage = "Estado Kaggle: "
                            + (state.isEmpty() ? "desconhecido" : state);
                }

                saveProject(p);
                runOnUiThread(() -> showProject(p));
            } catch (Exception e) {
                String error = compact(e.getMessage());
                p.connectionWarning = error;
                p.stage =
                        "Não consegui ler o estado real do Kaggle agora.";
                saveProject(p);
                runOnUiThread(() -> showProject(p));
            }
        });
    }

    private int estimatedRunningProgress(Project p) {
        long started = p.kaggleRunningStartedAt > 0L
                ? p.kaggleRunningStartedAt
                : System.currentTimeMillis();
        long elapsedSeconds = Math.max(
                0L,
                (System.currentTimeMillis() - started) / 1000L);

        int expectedSeconds;
        if ("Cinema".equalsIgnoreCase(p.quality)) {
            expectedSeconds = Math.max(420, p.durationSeconds * 55);
        } else if ("Qualidade".equalsIgnoreCase(p.quality)) {
            expectedSeconds = Math.max(300, p.durationSeconds * 40);
        } else {
            expectedSeconds = Math.max(180, p.durationSeconds * 26);
        }

        double ratio = Math.min(
                0.96,
                elapsedSeconds / (double) expectedSeconds);
        int estimate = 35 + (int) Math.round(ratio * 60.0);
        return Math.max(35, Math.min(95, estimate));
    }

    private String estimatedRunningStage(Project p) {
        long started = p.kaggleRunningStartedAt > 0L
                ? p.kaggleRunningStartedAt
                : System.currentTimeMillis();
        long elapsedSeconds = Math.max(
                0L,
                (System.currentTimeMillis() - started) / 1000L);

        int minute = (int) (elapsedSeconds / 60L);
        int second = (int) (elapsedSeconds % 60L);
        String elapsed = String.format(
                Locale.ROOT,
                "%d:%02d",
                minute,
                second);

        int progress = estimatedRunningProgress(p);
        String phase;
        if (progress < 48) {
            phase = "Carregando modelo e preparando frames";
        } else if (progress < 72) {
            phase = "Gerando os frames do vídeo";
        } else if (progress < 88) {
            phase = "Montando movimento e continuidade";
        } else {
            phase = "Finalizando e codificando o MP4";
        }

        return phase
                + " • "
                + elapsed
                + " decorrido • "
                + progress
                + "% estimado";
    }

    private boolean hasUsableInternet() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager)
                            getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities caps =
                    cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception ignored) {
            return true;
        }
    }

    private void openInternetPanel() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                startActivity(new Intent(
                        Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
            } else {
                startActivity(new Intent(
                        Settings.ACTION_WIFI_SETTINGS));
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception ignored) {
                toast("Abra o Wi‑Fi nas configurações do Android.");
            }
        }
    }

    private String extractKaggleStatus(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "running";
        }

        try {
            JSONObject o = new JSONObject(raw);
            String[] keys = {
                    "status",
                    "state",
                    "workerStatus",
                    "kernelWorkerStatus"
            };
            for (String key : keys) {
                String value = o.optString(key, "");
                if (!value.isEmpty()) return value;
            }

            String text = o.toString();
            String upper = text.toUpperCase(Locale.ROOT);
            if (upper.contains("COMPLETE")) return "complete";
            if (upper.contains("ERROR")) return "error";
            if (upper.contains("QUEUE")) return "queued";
            if (upper.contains("RUN")) return "running";
        } catch (Exception ignored) {
            String upper = raw.toUpperCase(Locale.ROOT);
            if (upper.contains("COMPLETE")) return "complete";
            if (upper.contains("ERROR")) return "error";
            if (upper.contains("QUEUE")) return "queued";
        }
        return "running";
    }

    private String kaggleRequestRaw(
            String method,
            String endpoint,
            JSONObject body) throws Exception {
        String token = getKaggleToken();

        if (token.isEmpty()) {
            throw new IllegalStateException(
                    "Personal API Token do Kaggle não conectado");
        }

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty(
                "Accept", "application/json");
        connection.setRequestProperty(
                "Authorization", "Bearer " + token);

        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8");
            try (OutputStream os =
                         connection.getOutputStream()) {
                os.write(
                        body.toString()
                                .getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = connection.getResponseCode();
        InputStream stream =
                code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        String response = readAll(stream);

        if (code < 200 || code >= 300) {
            String detail = response;
            try {
                JSONObject error = new JSONObject(response);
                detail = error.optString(
                        "message",
                        error.optString("detail", response));
            } catch (Exception ignored) {}

            throw new IllegalStateException(
                    "Kaggle HTTP "
                            + code
                            + (detail == null || detail.isEmpty()
                            ? ""
                            : ": " + detail));
        }

        return response == null ? "" : response;
    }

    private String readAssetText(String name) throws Exception {
        try (InputStream in = getAssets().open(name);
             BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     in,
                                     StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            return out.toString();
        }
    }

    private void copyKaggleConfigAndOpen(Project p) {
        try {
            String profile = "Cinema".equalsIgnoreCase(p.quality)
                    ? "MAXIMA"
                    : "RAPIDO_QUALIDADE";

            String automaticPrompt = buildAutomaticKagglePrompt(
                    p.prompt, p.style);

            String config =
                    "# ViniVideo AI v0.6.2 — prompt automático\n"
                    + "# Você escreveu só a ação; o app expandiu o resto sozinho.\n"
                    + "USER_PROMPT = " + JSONObject.quote(automaticPrompt) + "\n"
                    + "STYLE = " + JSONObject.quote(styleCode(p.style)) + "\n"
                    + "ASPECT = " + JSONObject.quote(p.aspect) + "\n"
                    + "DURATION_SECONDS = " + p.durationSeconds + "\n"
                    + "PROFILE = " + JSONObject.quote(profile) + "\n"
                    + "SEED = " + p.seed + "\n";

            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(
                            Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                                "ViniVideo AI Kaggle", config));
            }

            prefs.edit()
                    .putString(RETURN_PROJECT, p.id)
                    .apply();

            toast("Prompt automático pronto ✓ Cole na célula CONFIGURAÇÃO.");
            openKaggleQuality();
        } catch (Exception e) {
            toast("Não consegui preparar a configuração do Kaggle.");
        }
    }

    private void pickVideoForProject(String projectId) {
        pendingImportProjectId = projectId == null ? "" : projectId;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/mp4");
        startActivityForResult(i, PICK_VIDEO);
    }

    private void openKaggleQuality() {
        try {
            String source =
                    "https://github.com/ViniIsOn/ViniVideoAI-Android/blob/main/"
                            + "kaggle/ViniVideoAI_Kaggle_Quality.ipynb";
            String kaggleUrl =
                    "https://www.kaggle.com/notebooks/welcome?src="
                            + Uri.encode(source);
            Intent i = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(kaggleUrl));
            startActivity(i);
        } catch (Exception e) {
            toast("Não consegui abrir o Kaggle Qualidade.");
        }
    }

    private void openKaggleFallback() {
        try {
            String source =
                    "https://github.com/ViniIsOn/ViniVideoAI-Android/blob/main/"
                            + "kaggle/ViniVideoAI_Kaggle.ipynb";
            String kaggleUrl =
                    "https://www.kaggle.com/notebooks/welcome?src="
                            + Uri.encode(source);
            Intent i = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(kaggleUrl));
            startActivity(i);
        } catch (Exception e) {
            toast("Não consegui abrir o Kaggle.");
        }
    }

    private void pasteColabUrlAndTest(EditText url, EditText key) {
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(
                            Context.CLIPBOARD_SERVICE);

            if (clipboard == null
                    || !clipboard.hasPrimaryClip()
                    || clipboard.getPrimaryClip() == null
                    || clipboard.getPrimaryClip().getItemCount() == 0) {
                toast("Nada copiado ainda.");
                return;
            }

            CharSequence value = clipboard
                    .getPrimaryClip()
                    .getItemAt(0)
                    .coerceToText(this);

            String clip = value == null ? "" : value.toString().trim();
            int start = clip.indexOf("https://");
            if (start < 0) {
                toast("Não encontrei uma URL https:// copiada.");
                return;
            }

            String endpoint = clip.substring(start).split("\\s+")[0].trim();
            while (endpoint.endsWith("/")
                    || endpoint.endsWith(".")
                    || endpoint.endsWith(",")
                    || endpoint.endsWith(")")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }

            if (!endpoint.contains(".trycloudflare.com")) {
                new AlertDialog.Builder(this)
                        .setTitle("URL diferente do Colab")
                        .setMessage(
                                "O texto copiado não parece uma URL trycloudflare.com. Você pode colar manualmente no campo se for outro backend.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            url.setText(endpoint);
            prefs.edit()
                    .putString(BACKEND, endpoint)
                    .putString(API_KEY, key.getText().toString().trim())
                    .putBoolean(BACKEND_OK, false)
                    .putString(LAST_SCREEN, "BACKEND")
                    .apply();

            toast("URL colada. Testando…");
            testBackend(endpoint);
        } catch (Exception e) {
            toast("Não consegui ler a URL copiada.");
        }
    }

    private void openFreeColab() {
        prefs.edit().putString(LAST_SCREEN, "BACKEND").apply();
        try {
            Intent i = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://colab.research.google.com/github/ViniIsOn/ViniVideoAI-Android/blob/main/colab/ViniVideoAI_FreeGPU.ipynb"));
            startActivity(i);
        } catch (Exception e) {
            toast("Não consegui abrir o Colab.");
        }
    }

    private void testBackend(String endpoint) {
        if (endpoint.isEmpty()) {
            toast("Digite a URL do servidor.");
            return;
        }

        toast("Testando conexão…");
        executor.execute(() -> {
            try {
                request("GET", join(endpoint, "/api/v1/health"), null);
                prefs.edit().putBoolean(BACKEND_OK, true).apply();
                runOnUiThread(() -> {
                    toast("Motor conectado ✓");
                    String returnId = prefs.getString(RETURN_PROJECT, "");
                    if (!returnId.isEmpty()) {
                        prefs.edit().remove(RETURN_PROJECT).apply();
                        Project project = findProjectById(returnId);
                        if (project != null) {
                            project.connectionWarning = "";
                            saveProject(project);
                            showProject(project);
                            return;
                        }
                    }
                    showSettings();
                });
            } catch (Exception e) {
                prefs.edit().putBoolean(BACKEND_OK, false).apply();
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Não conectou")
                        .setMessage(compact(e.getMessage())
                                + "\n\nO app foi configurado corretamente, mas não há um backend respondendo nessa URL.")
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }

    private void showBackendRequired() {
        new AlertDialog.Builder(this)
                .setTitle("Falta o motor de vídeo")
                .setMessage(
                        "O app não vai fingir que gerou. Antes de renderizar, precisamos conectar um backend com GPU/modelo de vídeo.")
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Configurar backend", (d, w) -> showSettings())
                .show();
    }

    private void submitProject(Project p) {
        String backend = prefs.getString(BACKEND, "").trim();
        if (backend.isEmpty()) {
            showBackendRequired();
            return;
        }

        p.status = "ENVIANDO";
        p.progress = 2;
        p.stage = "Enviando projeto";
        saveProject(p);
        showProject(p);

        executor.execute(() -> {
            try {
                JSONObject body = buildRequestJson(p);
                JSONObject response = request(
                        "POST",
                        join(backend, "/api/v1/jobs"),
                        body);

                p.jobId = response.optString(
                        "job_id", response.optString("id", ""));
                p.status = response.optString("status", "QUEUED");
                p.progress = response.optInt("progress", 5);
                p.stage = response.optString("stage", "Na fila");
                p.outputUrl = response.optString("output_url", "");
                saveProject(p);

                runOnUiThread(() -> showProject(p));
            } catch (Exception e) {
                p.status = "ERRO";
                p.stage = compact(e.getMessage());
                p.progress = 0;
                saveProject(p);
                runOnUiThread(() -> showProject(p));
            }
        });
    }

    private void refreshProject(Project p) {
        String backend = prefs.getString(BACKEND, "").trim();
        if (backend.isEmpty() || p.jobId.isEmpty()) return;

        executor.execute(() -> {
            try {
                JSONObject response = request(
                        "GET",
                        join(backend, "/api/v1/jobs/" + p.jobId),
                        null);

                p.status = response.optString("status", p.status);
                p.progress = response.optInt("progress", p.progress);
                p.stage = response.optString("stage", p.stage);
                p.connectionWarning = "";
                String output = response.optString("output_url", "");
                if (!output.isEmpty()) p.outputUrl = output;

                saveProject(p);
                runOnUiThread(() -> showProject(p));
            } catch (Exception e) {
                p.connectionWarning = compact(e.getMessage());
                saveProject(p);
                runOnUiThread(() -> showProject(p));
            }
        });
    }

    private JSONObject buildRequestJson(Project p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("project_id", p.id);
        o.put("prompt", p.prompt);
        o.put("aspect_ratio", p.aspect);
        o.put("duration_seconds", p.durationSeconds);
        o.put("model", normalizeModel(p.model));
        o.put("quality_preset", p.quality.toLowerCase(Locale.ROOT));
        o.put("fps", 24);
        o.put("seed", p.seed);
        o.put("director_mode", p.directorMode);
        o.put("generate_audio", p.generateAudio);
        o.put("continuity_mode",
                p.strongContinuity
                        ? "last_frame_and_reference"
                        : "normal");
        o.put("render_strategy", "sequential_last_frame");

        if (!p.referenceUri.isEmpty()) {
            String image = referenceAsDataUri(p.referenceUri);
            if (!image.isEmpty()) o.put("reference_image", image);
        }

        JSONArray scenes = new JSONArray();
        for (Scene s : p.scenes) {
            JSONObject scene = new JSONObject();
            scene.put("index", s.index);
            scene.put("title", s.title);
            scene.put("start_second", s.startSecond);
            scene.put("duration_seconds", s.durationSeconds);
            scene.put("prompt", s.prompt);
            scenes.put(scene);
        }
        o.put("scenes", scenes);

        JSONObject stitch = new JSONObject();
        stitch.put("match_last_frame", p.strongContinuity);
        stitch.put("avoid_random_transitions", true);
        stitch.put("audio_crossfade_ms", 100);
        o.put("stitch", stitch);

        return o;
    }

    private String referenceAsDataUri(String uriText) {
        try {
            Uri uri = Uri.parse(uriText);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }

            int maxDimension = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (maxDimension / sample > 1280) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);

            Bitmap bitmap;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return "";

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int max = Math.max(width, height);
            if (max > 1280) {
                float scale = 1280f / max;
                Bitmap resized = Bitmap.createScaledBitmap(
                        bitmap,
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)),
                        true);
                if (resized != bitmap) bitmap.recycle();
                bitmap = resized;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
            bitmap.recycle();

            String encoded = Base64.encodeToString(
                    out.toByteArray(), Base64.NO_WRAP);
            return "data:image/jpeg;base64," + encoded;
        } catch (Exception ignored) {
            return "";
        }
    }

    private JSONObject request(String method, String endpoint, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Accept", "application/json");

        String key = prefs.getString(API_KEY, "").trim();
        if (!key.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + key);
        }

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty(
                    "Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = c.getResponseCode();
        InputStream stream =
                code >= 200 && code < 300
                        ? c.getInputStream()
                        : c.getErrorStream();
        String response = readAll(stream);

        if (code < 200 || code >= 300) {
            String detail = response;
            try {
                JSONObject errorJson = new JSONObject(response);
                detail = errorJson.optString("detail", response);
            } catch (Exception ignored) {}
            throw new IllegalStateException(
                    "HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
        }

        if (response.trim().isEmpty()) return new JSONObject();
        return new JSONObject(response);
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

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        if (requestCode == PICK_VIDEO) {
            Project p = findProjectById(pendingImportProjectId);
            pendingImportProjectId = "";
            if (p == null) {
                toast("Não encontrei o projeto para importar o vídeo.");
                return;
            }

            p.importedVideoUri = uri.toString();
            p.status = "IMPORTADO";
            p.progress = 100;
            p.stage = "Resultado do Kaggle importado";
            saveProject(p);
            toast("Vídeo do Kaggle importado ✓");
            showProject(p);
            return;
        }

        if (requestCode != PICK_IMAGE) return;

        referenceUri = uri.toString();
        prefs.edit()
                .putString(DRAFT_REFERENCE, referenceUri)
                .apply();
        refreshReferencePreview();
        showAutosaved();
    }

    private void refreshReferencePreview() {
        if (referencePreview == null) return;
        if (referenceUri.isEmpty()) {
            referencePreview.setImageDrawable(null);
            referencePreview.setContentDescription("Sem imagem de referência");
            return;
        }

        try {
            referencePreview.setImageURI(Uri.parse(referenceUri));
            referencePreview.setContentDescription("Imagem de referência selecionada");
        } catch (Exception e) {
            referencePreview.setImageDrawable(null);
        }
    }

    private void downloadVideo(String url, String id) {
        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));
            request.setTitle("ViniVideo AI");
            request.setDescription("Baixando vídeo renderizado");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "ViniVideo-" + id.substring(0, 8) + ".mp4");
            DownloadManager dm =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);
            toast("Download iniciado.");
        } catch (Exception e) {
            toast("Não foi possível baixar o vídeo.");
        }
    }

    private void loadProjectIntoDraft(Project p) {
        prefs.edit()
                .putString(DRAFT_PROMPT, p.prompt)
                .putString(DRAFT_ASPECT, p.aspect)
                .putString(DRAFT_DURATION, durationLabel(p.durationSeconds))
                .putString(DRAFT_MODEL, p.model)
                .putString(DRAFT_QUALITY, p.quality)
                .putString(DRAFT_STYLE, p.style)
                .putString(DRAFT_REFERENCE, p.referenceUri)
                .putBoolean(DRAFT_DIRECTOR, p.directorMode)
                .putBoolean(DRAFT_CONTINUITY, p.strongContinuity)
                .putBoolean(DRAFT_AUDIO, p.generateAudio)
                .apply();
    }

    private void confirmDelete(Project p) {
        boolean hasRemoteKaggle =
                p.kaggleOwner != null
                        && !p.kaggleOwner.isEmpty()
                        && p.kaggleSlug != null
                        && !p.kaggleSlug.isEmpty();

        String message = hasRemoteKaggle
                ? "O projeto será removido deste aparelho agora. "
                        + "O app também vai tentar limpar a execução no Kaggle; "
                        + "se o Kaggle negar a exclusão, o projeto local ainda será apagado "
                        + "e a sessão remota ficará protegida pelo limite automático."
                : "O projeto será removido do histórico deste aparelho.";

        new AlertDialog.Builder(this)
                .setTitle("Excluir projeto?")
                .setMessage(message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    if (hasRemoteKaggle && hasKaggleCredentials()) {
                        cleanupKaggleAndDeleteProject(p);
                    } else {
                        deleteProject(p.id);
                        showProjects();
                    }
                })
                .show();
    }

    private void cleanupKaggleAndDeleteProject(Project p) {
        // O usuário sempre consegue apagar o projeto local.
        deleteProject(p.id);
        showProjects();
        toast("Projeto removido deste aparelho.");

        executor.execute(() -> {
            try {
                JSONObject deleteBody = new JSONObject();
                deleteBody.put("userName", p.kaggleOwner);
                deleteBody.put("kernelSlug", p.kaggleSlug);

                kaggleRequestRaw(
                        "POST",
                        KAGGLE_DELETE_KERNEL,
                        deleteBody);

                runOnUiThread(() ->
                        toast("Execução do Kaggle também foi limpa ✓"));
            } catch (Exception e) {
                String error = compact(e.getMessage());
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Projeto apagado do celular")
                        .setMessage(
                                "O Kaggle não autorizou a limpeza remota:\n"
                                        + error
                                        + "\n\nO projeto local já foi removido. "
                                        + "A execução remota fica sujeita ao limite automático de sessão e encerra sozinha.")
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
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
            for (Project p : all) a.put(projectToJson(p));
            prefs.edit().putString(PROJECTS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private List<Project> loadProjects() {
        List<Project> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(PROJECTS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(projectFromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private Project findProjectById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Project p : loadProjects()) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    private void deleteProject(String id) {
        List<Project> all = loadProjects();
        JSONArray a = new JSONArray();

        try {
            for (Project p : all) {
                if (!p.id.equals(id)) a.put(projectToJson(p));
            }
            prefs.edit().putString(PROJECTS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private JSONObject projectToJson(Project p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("created_at", p.createdAt);
        o.put("prompt", p.prompt);
        o.put("aspect_ratio", p.aspect);
        o.put("duration_seconds", p.durationSeconds);
        o.put("model", p.model);
        o.put("quality", p.quality);
        o.put("style", p.style);
        o.put("reference_uri", p.referenceUri);
        o.put("imported_video_uri", p.importedVideoUri);
        o.put("director_mode", p.directorMode);
        o.put("strong_continuity", p.strongContinuity);
        o.put("generate_audio", p.generateAudio);
        o.put("seed", p.seed);
        o.put("job_id", p.jobId);
        o.put("status", p.status);
        o.put("progress", p.progress);
        o.put("stage", p.stage);
        o.put("output_url", p.outputUrl);
        o.put("connection_warning", p.connectionWarning);
        o.put("kaggle_owner", p.kaggleOwner);
        o.put("kaggle_slug", p.kaggleSlug);
        o.put("kaggle_output_page", p.kaggleOutputPage);
        o.put("kaggle_ref", p.kaggleRef);
        o.put("kaggle_running_started_at", p.kaggleRunningStartedAt);

        JSONArray scenes = new JSONArray();
        for (Scene s : p.scenes) {
            JSONObject scene = new JSONObject();
            scene.put("index", s.index);
            scene.put("title", s.title);
            scene.put("start_second", s.startSecond);
            scene.put("duration_seconds", s.durationSeconds);
            scene.put("prompt", s.prompt);
            scenes.put(scene);
        }
        o.put("scenes", scenes);
        return o;
    }

    private Project projectFromJson(JSONObject o) {
        Project p = new Project();
        p.id = o.optString("id", p.id);
        p.createdAt = o.optLong("created_at", p.createdAt);
        p.prompt = o.optString("prompt", "");
        p.aspect = o.optString("aspect_ratio", "9:16");
        p.durationSeconds = o.optInt("duration_seconds", 30);
        p.model = o.optString("model", "Motor grátis automático (Turbo/LTX)");
        p.quality = o.optString("quality", "Cinema");
        p.style = o.optString("style", "Cartoon Filme Animado");
        p.referenceUri = o.optString("reference_uri", "");
        p.importedVideoUri = o.optString("imported_video_uri", "");
        p.directorMode = o.optBoolean("director_mode", true);
        p.strongContinuity = o.optBoolean("strong_continuity", true);
        p.generateAudio = o.optBoolean("generate_audio", true);
        p.seed = o.optInt("seed", Math.abs(p.prompt.hashCode()));
        p.jobId = o.optString("job_id", "");
        p.status = o.optString("status", "SALVO");
        p.progress = o.optInt("progress", 0);
        p.stage = o.optString("stage", "");
        p.outputUrl = o.optString("output_url", "");
        p.connectionWarning = o.optString("connection_warning", "");
        p.kaggleOwner = o.optString("kaggle_owner", "");
        p.kaggleSlug = o.optString("kaggle_slug", "");
        p.kaggleOutputPage = o.optString("kaggle_output_page", "");
        p.kaggleRef = o.optString("kaggle_ref", "");
        p.kaggleRunningStartedAt =
                o.optLong("kaggle_running_started_at", 0L);

        JSONArray scenes = o.optJSONArray("scenes");
        if (scenes != null) {
            for (int i = 0; i < scenes.length(); i++) {
                JSONObject item = scenes.optJSONObject(i);
                if (item == null) continue;

                Scene s = new Scene();
                s.index = item.optInt("index", i + 1);
                s.title = item.optString("title", "Tomada");
                s.startSecond = item.optInt("start_second", i * 6);
                s.durationSeconds = item.optInt("duration_seconds", 6);
                s.prompt = item.optString("prompt", "");
                p.scenes.add(s);
            }
        }

        if (p.scenes.isEmpty()) p.scenes.addAll(buildScenes(p));
        return p;
    }

    private void navBar(String active) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(16));

        Button create = navButton("Criar", "CRIAR".equals(active));
        Button projects = navButton("Projetos", "PROJETOS".equals(active));
        Button backend = navButton("Backend", "BACKEND".equals(active));

        create.setOnClickListener(v -> showCreate());
        projects.setOnClickListener(v -> showProjects());
        backend.setOnClickListener(v -> showSettings());

        row.addView(create, new LinearLayout.LayoutParams(0, dp(46), 1));
        row.addView(space(8));
        row.addView(projects, new LinearLayout.LayoutParams(0, dp(46), 1));
        row.addView(space(8));
        row.addView(backend, new LinearLayout.LayoutParams(0, dp(46), 1));

        root.addView(row);
    }

    private void brand(String title, String subtitle) {
        TextView brand = new TextView(this);
        brand.setText("VINI  /  VIDEO AI");
        brand.setTextColor(cyan);
        brand.setTextSize(11);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        brand.setLetterSpacing(0.12f);
        root.addView(brand);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(text);
        t.setTextSize(28);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        root.addView(t, margin(-1, -2, 3, 1));

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

    private LinearLayout miniCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(round(panelAlt, 16));
        return c;
    }

    private void sectionLabel(LinearLayout parent, String value) {
        label(parent, value, gold, 11, true);
    }

    private void label(
            LinearLayout parent,
            String value,
            int color,
            int size,
            boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setTypeface(Typeface.create(
                "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        if (bold) v.setLetterSpacing(0.05f);
        parent.addView(v, margin(-1, -2, 0, 7));
    }

    private TextView body(String value) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(text);
        v.setTextSize(15);
        v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        v.setLineSpacing(0, 1.14f);
        return v;
    }

    private TextView small(String value, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(13);
        v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private TextView centered(String value, int color, int size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return v;
    }

    private TextView keyValue(String key, String value) {
        TextView v = new TextView(this);
        v.setText(key + "   " + value);
        v.setTextColor(text);
        v.setTextSize(14);
        v.setPadding(0, dp(8), 0, dp(8));
        v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return v;
    }

    private TextView pill(String value, int color) {
        TextView v = small(value, color);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackground(round(Color.argb(
                36,
                Color.red(color),
                Color.green(color),
                Color.blue(color)), 14));
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setTextSize(15);
        e.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(round(panelAlt, 14));
        e.setSingleLine(false);
        return e;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                values) {
            @Override
            public View getView(
                    int position,
                    View convertView,
                    ViewGroup parent) {
                TextView v = (TextView) super.getView(
                        position, convertView, parent);
                v.setTextColor(text);
                v.setTextSize(15);
                v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                v.setBackgroundColor(panelAlt);
                v.setPadding(dp(14), dp(10), dp(14), dp(10));
                return v;
            }

            @Override
            public View getDropDownView(
                    int position,
                    View convertView,
                    ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(
                        position, convertView, parent);
                v.setTextColor(text);
                v.setTextSize(15);
                v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                v.setBackgroundColor(panelAlt);
                v.setPadding(dp(14), dp(12), dp(14), dp(12));
                return v;
            }
        };
        s.setAdapter(a);
        s.setBackground(round(panelAlt, 14));
        return s;
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (String.valueOf(spinner.getItemAtPosition(i)).equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private CheckBox checkbox(String value, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(value);
        c.setTextColor(text);
        c.setTextSize(14);
        c.setChecked(checked);
        c.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{cyan, muted}
        ));
        c.setPadding(0, dp(4), 0, dp(4));
        return c;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(foreground);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(round(background, 14));
        return b;
    }

    private Button navButton(String value, boolean active) {
        return button(
                value,
                active ? cyanDark : panelAlt,
                active ? cyan : text);
    }

    private View space(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return v;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), border);
        return g;
    }

    private LinearLayout.LayoutParams margin(
            int width,
            int height,
            int topDp,
            int bottomDp) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(width, height);
        p.topMargin = dp(topDp);
        p.bottomMargin = dp(bottomDp);
        return p;
    }

    private int previewHeight(String aspect) {
        int screen = getResources().getDisplayMetrics().widthPixels - dp(64);
        if ("9:16".equals(aspect)) return Math.min(dp(430), Math.round(screen * 1.35f));
        if ("1:1".equals(aspect)) return Math.min(dp(360), screen);
        return Math.max(dp(190), Math.round(screen * 9f / 16f));
    }

    private int durationSeconds(String label) {
        if (label == null) return 30;
        if (label.startsWith("2")) return 120;
        if (label.startsWith("5 min")) return 300;
        if (label.startsWith("10 min")) return 600;

        String digits = label.replaceAll("[^0-9]", "");
        try {
            int value = Integer.parseInt(digits);
            return Math.max(5, Math.min(600, value));
        } catch (Exception ignored) {
            return 30;
        }
    }

    private String durationLabel(int seconds) {
        if (seconds == 120) return "2 min";
        if (seconds == 300) return "5 min";
        if (seconds == 600) return "10 min";
        return seconds + " s";
    }

    private String prettyDuration(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " min";
        }
        return seconds + " s";
    }

    private boolean isTerminal(String status) {
        if (status == null) return false;
        String s = status.toUpperCase(Locale.ROOT);
        return s.contains("DONE")
                || s.contains("COMPLET")
                || s.contains("CONCLU")
                || s.contains("FAIL")
                || s.contains("ERRO")
                || s.contains("CANCEL");
    }

    private int statusColor(String status) {
        if (status == null) return muted;
        String s = status.toUpperCase(Locale.ROOT);
        if (s.contains("DONE") || s.contains("COMPLET") || s.contains("CONCLU")) return green;
        if (s.contains("ERRO") || s.contains("FAIL")) return danger;
        if (s.contains("RUN") || s.contains("RENDER") || s.contains("ENVI")) return cyan;
        if (s.contains("QUEUE") || s.contains("FILA") || s.contains("PREPAR")) return gold;
        return muted;
    }

    private String normalizeModel(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        if (s.contains("automático") || s.contains("automatico") || s.contains("turbo")) return "auto-turbo-ltx";
        if (s.contains("colab") || s.contains("video 2b")) return "ltx-video-2b";
        if (s.contains("wan")) return "wan2.2";
        if (s.contains("ltx-2")) return "ltx-2";
        if (s.contains("hunyuan")) return "hunyuanvideo-1.5";
        return s.replace(" ", "-");
    }

    private String resolveOutputUrl(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        return join(prefs.getString(BACKEND, ""), raw);
    }

    private String join(String base, String path) {
        base = base == null ? "" : base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    private String compact(String value) {
        if (value == null || value.trim().isEmpty()) return "erro desconhecido";
        String clean = value.replace("\n", " ").trim();
        return clean.length() > 180
                ? clean.substring(0, 180) + "…"
                : clean;
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max
                ? value
                : value.substring(0, max - 1) + "…";
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(
                CharSequence s, int start, int before, int count) {}
    }

    private static class Scene {
        int index;
        String title = "Tomada";
        int startSecond;
        int durationSeconds;
        String prompt = "";
    }

    private static class Project {
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String prompt = "";
        String aspect = "9:16";
        int durationSeconds = 30;
        String model = "Motor grátis automático (Turbo/LTX)";
        String quality = "Rápido";
        String style = "Cartoon Filme Animado";
        String referenceUri = "";
        String importedVideoUri = "";
        boolean directorMode = true;
        boolean strongContinuity = true;
        boolean generateAudio = true;
        int seed = 1;
        String jobId = "";
        String status = "SALVO";
        int progress = 0;
        String stage = "";
        String outputUrl = "";
        String connectionWarning = "";
        String kaggleOwner = "";
        String kaggleSlug = "";
        String kaggleOutputPage = "";
        String kaggleRef = "";
        long kaggleRunningStartedAt = 0L;
        final List<Scene> scenes = new ArrayList<>();
    }
}
