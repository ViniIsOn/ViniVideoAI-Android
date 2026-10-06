# ViniVideo AI — Backend Colab grátis

Backend experimental para conectar o app Android a uma GPU gratuita do Google Colab.

## Perfil gratuito

O modo gratuito usa **LTX-Video 2B** como perfil prático para GPUs de notebook. Ele suporta texto→vídeo e imagem→vídeo, o que permite usar o último frame de uma tomada como referência da próxima.

Este perfil **não é o Flow** e não promete a mesma qualidade. Também não é ilimitado: o Colab pode encerrar a sessão, trocar a GPU ou limitar o uso.

## API

- `GET /api/v1/health`
- `POST /api/v1/jobs`
- `GET /api/v1/jobs/{id}`
- `GET /outputs/{job}.mp4`

## Continuidade

Quando o app envia `continuity_mode: last_frame_and_reference`, o backend:

1. gera a primeira tomada;
2. pega o último frame;
3. usa esse frame como entrada de imagem da tomada seguinte;
4. mantém o seed relacionado;
5. junta os MP4s com FFmpeg.

## Áudio

A primeira versão gratuita não gera áudio por IA. O campo `generate_audio` é aceito para manter compatibilidade com o app, mas o job informa essa limitação ao finalizar.

## Rodar

O jeito mais fácil é abrir:

`colab/ViniVideoAI_FreeGPU.ipynb`

No Colab, escolha uma GPU T4 e execute as células. O notebook imprime uma URL `https://....trycloudflare.com` para colar na tela **Backend** do app.
