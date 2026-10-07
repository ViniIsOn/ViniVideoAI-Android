# ViniVideo AI

## v0.5 — Kaggle Qualidade (Wan 2.1)

- Novo botão **Kaggle Qualidade — Wan 2.1**.
- Notebook separado `kaggle/ViniVideoAI_Kaggle_Quality.ipynb`.
- Geração próxima de 480p com **Wan 2.1 T2V 1.3B**.
- 81 frames por tomada, mais passos de difusão e saída final interpolada para **24 fps**.
- Modo pensado para priorizar consistência visual e movimento em vez de velocidade.
- O modo rápido AnimateDiff continua disponível como fallback.



## v0.4.2 — fallback Kaggle

- Botão **Abrir motor Kaggle (Plano B)** na tela Backend.
- Notebook `kaggle/ViniVideoAI_Kaggle.ipynb` abre direto do GitHub no Kaggle.
- Use **Settings → Accelerator → GPU T4 x2** e Internet ativada.
- O Kaggle gera o MP4 dentro do notebook e mostra um link de download.
- Este fallback não usa Cloudflare/Ngrok e não preenche a URL do backend do app.



## v0.4 — Turbo + retomada

- **Rápido** usa AnimateDiff-Lightning 4-step.
- **Qualidade/Cinema** continuam usando LTX.
- Downloads do Hugging Face usam Xet em modo de alta performance.
- O Colab pode salvar checkpoints de tomadas no Google Drive em `MyDrive/ViniVideoAI`.
- O backend usa o `project_id` como job estável e retoma tomadas já salvas após reinício.
- Uma tomada concluída não precisa ser renderizada novamente depois de uma queda.


## v0.3 — motor grátis pelo celular

A v0.3 adiciona um notebook do Google Colab que inicia um backend real de geração de vídeo em GPU gratuita e cria uma URL pública temporária via Cloudflare Tunnel. O app ganhou o botão **Iniciar motor grátis (Colab)** na tela Backend.

O perfil gratuito usa **LTX-Video 2B** para permitir texto→vídeo, imagem→vídeo e continuidade entre tomadas por último frame. Ele é experimental: Colab gratuito pode desconectar, limitar GPU ou demorar bastante, e não entrega a mesma qualidade de serviços fechados como Flow.

Caminho rápido: **Backend → Iniciar motor grátis → rodar tudo no Colab → copiar URL trycloudflare.com → colar no app → Testar conexão → Criar**.


Aplicativo Android nativo para criar e controlar geração de vídeos por IA.

## v0.2

A v0.2 abandona a interface de “lista de prompts” e passa a funcionar como um estúdio:

- Android nativo em Java, sem HTML/WebView;
- ícone próprio;
- rascunho salvo automaticamente enquanto o usuário digita;
- botão separado para salvar projeto e botão separado para gerar;
- 9:16, 16:9 e 1:1;
- 5 segundos até 10 minutos;
- Wan 2.2, LTX-2 e HunyuanVideo 1.5 como perfis de backend;
- presets Rápido, Qualidade e Cinema;
- imagem de referência enviada ao backend em JPEG/base64;
- Director AI;
- continuidade forte usando estratégia de último frame;
- timeline compacta;
- preview de vídeo no próprio app;
- progresso 0–100 e estágio atual;
- polling automático do job;
- download do MP4;
- histórico local de projetos;
- teste de conexão com o backend;
- GitHub Actions gera o APK.

## Limite real

O APK é o estúdio/cliente. Modelos de vídeo grandes precisam de uma GPU em algum lugar.  
Sem backend conectado, o aplicativo **não finge** que gerou um vídeo.

O aplicativo não possui sistema próprio de créditos. Custos e limites dependem do servidor em que o modelo estiver rodando.

## API esperada

### Health

```
GET /api/v1/health
```

Resposta JSON 2xx:

```json
{"status":"ok"}
```

### Criar geração

```
POST /api/v1/jobs
Content-Type: application/json
```

O corpo contém:

- prompt;
- aspect_ratio;
- duration_seconds;
- model;
- quality_preset;
- fps;
- seed;
- director_mode;
- generate_audio;
- continuity_mode;
- render_strategy;
- reference_image (quando houver);
- scenes[];
- stitch{}.

Resposta:

```json
{
  "job_id": "abc123",
  "status": "QUEUED",
  "progress": 5,
  "stage": "Na fila",
  "output_url": ""
}
```

### Consultar geração

```
GET /api/v1/jobs/{id}
```

Enquanto renderiza:

```json
{
  "job_id": "abc123",
  "status": "RUNNING",
  "progress": 63,
  "stage": "Renderizando tomada 4/7",
  "output_url": ""
}
```

Quando concluir:

```json
{
  "job_id": "abc123",
  "status": "DONE",
  "progress": 100,
  "stage": "Concluído",
  "output_url": "/outputs/abc123.mp4"
}
```
