# ViniVideo AI

## v0.7.9 — API oficial atual do Kaggle

- Troca rotas antigas `www.kaggle.com/api/v1/...` pelas rotas RPC atuais em `https://api.kaggle.com/v1`.
- Criação/execução usa `kernels.KernelsApiService/SaveKernel`.
- Exclusão/limpeza usa `kernels.KernelsApiService/DeleteKernel`.
- Mantém Bearer Personal API Token.
- Corrige o 404 HTML retornado pelas rotas antigas.



## v0.7.8 — evitar conflito 409 no teste Kaggle

- Kernel de teste agora usa **slug e título únicos**.
- Se o Kaggle ainda devolver HTTP 409, o app gera outro título automaticamente e tenta mais uma vez.
- O teste continua sem GPU.
- Nenhuma mudança no prompt ou no fluxo de geração real.



## v0.7.7 — não depender de kernels.get

- Remove a dependência do endpoint de status do Kaggle que pode retornar `kernels.get = 403` indevidamente.
- O teste de conexão usa apenas: criar kernel privado sem GPU → apagar kernel.
- Após enviar a geração, o app mostra **ENVIADO** e não fica fazendo polling de status.
- O botão de execução/resultado do Kaggle fica disponível logo após o envio.
- Timeout automático de sessão e limpeza ao excluir projeto continuam ativos.



## v0.7.6 — teste real do Kaggle antes da GPU

- Remove a checagem incorreta de escopos do Personal API Token.
- Ao conectar, o app cria um kernel privado minúsculo **sem GPU**, consulta o status e apaga o kernel.
- A geração de vídeo só é liberada se criar/executar, acompanhar e limpar funcionarem de verdade.
- Se o teste falhar, nenhuma GPU é usada.
- Mensagens de erro agora diferenciam falha de status, limpeza, autenticação e incompatibilidade da API.



## v0.7.5 — verificar permissões antes de gerar

- Kaggle Direto passa a usar **Personal API Token (Bearer)**.
- O app introspecta o token antes de gerar e exige:
  - `kernels.get:*`
  - `kernels.update:*`
  - `kernels.execute:*`
  - `kernels.delete:*`
- Se faltar qualquer escopo, a geração é bloqueada antes do envio.
- O app mostra os escopos detectados e quais estão faltando.
- Exclusão remota só é liberada quando `kernels.delete:*` foi verificado.



## v0.7.4 — limpeza segura do Kaggle

- Cada execução direta recebe um **limite automático de sessão** entre 30 e 60 minutos.
- Excluir um projeto com kernel Kaggle associado tenta primeiro remover o kernel remoto.
- O projeto local só é apagado depois que o Kaggle confirma a remoção.
- Se a limpeza remota falhar, o projeto fica salvo para o usuário não perder o controle.
- Mensagens de erro 403 agora orientam sobre permissões `kernels.get` e `kernels.delete`.



## v0.7.2 — GPU e internet automáticos

- O app verifica se o aparelho tem internet antes de enviar.
- Se estiver offline, abre o painel de internet do Android; o sistema não permite ligar o Wi‑Fi silenciosamente.
- O envio ao Kaggle solicita **Internet ON** e **GPU T4 ×2** automaticamente.
- O kernel usa `machineShape = NvidiaTeslaT4`.
- Erro 403 de `kernels.get` agora é tratado separadamente: a geração pode continuar mesmo quando a credencial não permite consultar o status.



## v0.7.1 — prompt ao vivo

- Corrige geração usando rascunho antigo.
- O botão **Gerar vídeo** lê o texto que está visível na tela naquele exato momento.
- Remove o exemplo antigo “uma arara azul conhece um novo amigo...” do notebook manual.
- O runner direto imprime nos logs o prompt exato que recebeu.
- O status do projeto mostra um resumo do prompt enviado ao Kaggle.



## v0.7 — Kaggle Direto

- O botão **Gerar vídeo** não usa mais copiar/colar como fluxo principal.
- Nova conexão **Kaggle Direto** por usuário + Legacy API Key.
- A API key é armazenada criptografada pelo **Android Keystore**.
- O app cria um script privado no Kaggle, envia o prompt atual e inicia a execução com GPU.
- O app consulta o status da execução automaticamente.
- O resultado do Kaggle pode ser aberto pelo projeto; o fluxo manual continua apenas como fallback.
- Runner Wan 2.1 incluído em `app/src/main/assets/kaggle_vinivideo_runner.py`.



## v0.6.2 — Prompt automático

- Na tela Criar, o usuário escreve **só a ação do episódio**.
- O app detecta prompts do **Poder Azul / arara azul** e adiciona automaticamente a descrição fixa do personagem e do bichinho verde.
- O app acrescenta estilo cartoon, consistência, movimento suave, enquadramento e regras contra mudanças de personagem.
- O bloco copiado para o Kaggle já contém o **prompt expandido automaticamente**.
- O mesmo prompt automático também é usado nas tomadas internas do projeto.



## v0.6.1 — Interface simplificada

- Tela **Criar** reduzida ao essencial: prompt, formato, duração e botão **Gerar vídeo**.
- Padrão recomendado para o canal: **Cartoon Filme Animado + Qualidade Rápida + 9:16 + 5 s**.
- Modelo, estilo alternativo, qualidade, referência visual, Director AI e backend próprio ficam em **⚙ Avançado**.
- Tela de projeto esconde detalhes técnicos atrás de **Ver detalhes**.
- Fluxo principal guiado: **escreva → gerar → Kaggle → baixar MP4 → importar no projeto**.



## v0.6 — Prompt fiel + estilo + importação Kaggle

- O prompt digitado pelo usuário passa a ser a prioridade da geração.
- Novo seletor de estilo com **Cartoon Filme Animado** como padrão.
- Botão **Gerar no Kaggle Qualidade** copia automaticamente a configuração do projeto.
- Notebook Wan 2.1 usa o prompt do usuário + reforços técnicos de consistência.
- Projetos podem importar o MP4 baixado do Kaggle e reproduzi-lo dentro do app.
- O resultado importado fica associado ao projeto.



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
