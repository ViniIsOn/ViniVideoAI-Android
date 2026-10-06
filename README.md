# ViniVideo AI

Aplicativo Android nativo para planejar e orquestrar geração de vídeos longos em múltiplas cenas.

## v0.1

- Java + Android nativo, sem HTML/WebView
- 9:16, 16:9 e 1:1
- duração de 5 a 600 segundos
- storyboard automático em cenas
- continuidade entre cenas
- imagem de referência
- histórico local
- exportação JSON
- perfis Demo, Wan 2.2, LTX-2 e HunyuanVideo 1.5
- backend self-hosted configurável
- envio de job, consulta de status e download do MP4
- GitHub Actions gera APK debug

> Os modelos de vídeo não ficam dentro do APK. O aplicativo funciona como estúdio/controle e pode conversar com uma GPU/servidor próprio.

## Backend

POST `/api/v1/jobs` cria um job.  
GET `/api/v1/jobs/{id}` consulta o status.

Resposta esperada:

```json
{"job_id":"abc123","status":"QUEUED","output_url":""}
```

Quando terminar, o backend retorna `status: "DONE"` e um `output_url` para o MP4.
