import base64
import gc
import json
import os
import re
import shutil
import subprocess
import threading
import traceback
import uuid
from io import BytesIO
from pathlib import Path

import torch
from fastapi import BackgroundTasks, FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from PIL import Image

OUTPUT_DIR = Path(
    os.environ.get("VINIVIDEO_OUTPUT_DIR", "/content/vinivideo_outputs")
)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
STATE_DIR = OUTPUT_DIR / "job_states"
STATE_DIR.mkdir(parents=True, exist_ok=True)

LTX_MODEL_ID = os.environ.get(
    "VINIVIDEO_LTX_MODEL_ID",
    "Lightricks/LTX-Video-0.9.5",
)
TURBO_BASE_ID = os.environ.get(
    "VINIVIDEO_TURBO_BASE_ID",
    "stable-diffusion-v1-5/stable-diffusion-v1-5",
)
TURBO_REPO = "ByteDance/AnimateDiff-Lightning"
TURBO_CKPT = "animatediff_lightning_4step_diffusers.safetensors"

NEGATIVE = (
    "worst quality, low quality, blurry, jittery, distorted anatomy, "
    "deformed face, duplicated subject, random cuts, flicker, text, watermark"
)

app = FastAPI(title="ViniVideo AI Free GPU Backend", version="0.4")
app.mount("/outputs", StaticFiles(directory=str(OUTPUT_DIR)), name="outputs")

JOBS = {}
JOBS_LOCK = threading.Lock()
MODEL_LOCK = threading.RLock()
LTX_TEXT_PIPE = None
LTX_IMAGE_PIPE = None
TURBO_PIPE = None
LAST_ERROR = ""
LOG_PATH = OUTPUT_DIR / "vinivideo-backend.log"


def gpu_name():
    if not torch.cuda.is_available():
        return "CPU"
    return torch.cuda.get_device_name(0)


def log_line(message):
    line = str(message)
    print(line, flush=True)
    try:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass


def safe_job_id(value):
    raw = str(value or "").strip()
    if not raw:
        return uuid.uuid4().hex
    clean = re.sub(r"[^a-zA-Z0-9_-]", "", raw)
    return clean[:96] or uuid.uuid4().hex


def state_path(job_id):
    return STATE_DIR / f"{job_id}.json"


def persist_job(job_id, state):
    try:
        tmp = state_path(job_id).with_suffix(".json.tmp")
        tmp.write_text(
            json.dumps(state, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        tmp.replace(state_path(job_id))
    except Exception as exc:
        log_line(f"[{job_id}] não consegui salvar estado: {exc}")


def restore_jobs():
    for path in STATE_DIR.glob("*.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            job_id = str(data.get("job_id") or path.stem)
            JOBS[job_id] = data
        except Exception:
            pass


def set_job(job_key, **changes):
    with JOBS_LOCK:
        current = JOBS.get(job_key, {})
        current.update(changes)
        JOBS[job_key] = current
        snapshot = dict(current)
    persist_job(job_key, snapshot)


def get_job(job_id):
    with JOBS_LOCK:
        return dict(JOBS.get(job_id, {}))


restore_jobs()


def load_turbo_pipeline():
    global TURBO_PIPE

    with MODEL_LOCK:
        if TURBO_PIPE is not None:
            return TURBO_PIPE

        if not torch.cuda.is_available():
            raise RuntimeError(
                "GPU CUDA não disponível. Ative uma GPU T4 no Colab."
            )

        from diffusers import (
            AnimateDiffPipeline,
            EulerDiscreteScheduler,
            MotionAdapter,
        )
        from huggingface_hub import hf_hub_download
        from safetensors.torch import load_file

        device = "cuda"
        dtype = torch.float16

        adapter = MotionAdapter().to(device, dtype)
        checkpoint = hf_hub_download(
            repo_id=TURBO_REPO,
            filename=TURBO_CKPT,
        )
        adapter.load_state_dict(
            load_file(checkpoint, device=device),
            strict=True,
        )

        pipe = AnimateDiffPipeline.from_pretrained(
            TURBO_BASE_ID,
            motion_adapter=adapter,
            torch_dtype=dtype,
        )
        pipe.scheduler = EulerDiscreteScheduler.from_config(
            pipe.scheduler.config,
            timestep_spacing="trailing",
            beta_schedule="linear",
        )

        try:
            pipe.enable_vae_slicing()
        except Exception:
            pass
        pipe.enable_model_cpu_offload()

        TURBO_PIPE = pipe
        return TURBO_PIPE


def load_ltx_text_pipeline():
    global LTX_TEXT_PIPE

    with MODEL_LOCK:
        if LTX_TEXT_PIPE is not None:
            return LTX_TEXT_PIPE

        if not torch.cuda.is_available():
            raise RuntimeError(
                "GPU CUDA não disponível. Ative uma GPU T4 no Colab."
            )

        from diffusers import LTXPipeline

        LTX_TEXT_PIPE = LTXPipeline.from_pretrained(
            LTX_MODEL_ID,
            torch_dtype=torch.float16,
        )
        try:
            LTX_TEXT_PIPE.vae.enable_tiling()
        except Exception:
            pass
        LTX_TEXT_PIPE.enable_model_cpu_offload()
        return LTX_TEXT_PIPE


def load_ltx_image_pipeline():
    global LTX_IMAGE_PIPE

    with MODEL_LOCK:
        if LTX_IMAGE_PIPE is not None:
            return LTX_IMAGE_PIPE

        text_pipe = load_ltx_text_pipeline()
        from diffusers import LTXImageToVideoPipeline

        LTX_IMAGE_PIPE = LTXImageToVideoPipeline.from_pipe(text_pipe)
        try:
            LTX_IMAGE_PIPE.vae.enable_tiling()
        except Exception:
            pass
        LTX_IMAGE_PIPE.enable_model_cpu_offload()
        return LTX_IMAGE_PIPE


def decode_reference(data_uri):
    if not data_uri:
        return None
    try:
        encoded = (
            data_uri.split(",", 1)[1]
            if "," in data_uri
            else data_uri
        )
        raw = base64.b64decode(encoded)
        return Image.open(BytesIO(raw)).convert("RGB")
    except Exception:
        return None


def aspect_size(aspect, quality):
    quality = str(quality or "qualidade").lower()

    if "cinema" in quality:
        long_side, short_side = 640, 352
    elif "qualidade" in quality:
        long_side, short_side = 576, 320
    else:
        long_side, short_side = 512, 288

    if aspect == "9:16":
        return short_side, long_side
    if aspect == "1:1":
        side = 448 if "cinema" in quality else 384
        return side, side
    return long_side, short_side


def ltx_profile(quality, scene_duration):
    q = str(quality or "qualidade").lower()
    if "cinema" in q:
        frames, steps = 81, 28
    else:
        frames, steps = 65, 20

    duration = max(1, int(scene_duration or 4))
    fps = max(8, min(24, round((frames - 1) / duration)))
    return frames, steps, fps


def turbo_profile(scene_duration):
    duration = max(1, int(scene_duration or 4))
    if duration <= 2:
        frames = 16
    elif duration <= 4:
        frames = 24
    else:
        frames = 32
    steps = 4
    fps = max(5, min(10, round(frames / duration)))
    return frames, steps, fps


def save_scene(frames, output_path, fps):
    from diffusers.utils import export_to_video

    export_to_video(frames, str(output_path), fps=fps)


def stitch_videos(scene_paths, final_path):
    concat_file = final_path.with_suffix(".txt")

    with open(concat_file, "w", encoding="utf-8") as f:
        for path in scene_paths:
            safe = str(path).replace("'", "'\\''")
            f.write(f"file '{safe}'\n")

    cmd = [
        "ffmpeg",
        "-y",
        "-f",
        "concat",
        "-safe",
        "0",
        "-i",
        str(concat_file),
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "18",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        str(final_path),
    ]
    subprocess.run(
        cmd,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    try:
        concat_file.unlink()
    except Exception:
        pass


def scene_file(job_dir, index):
    return job_dir / f"scene_{index:03d}.mp4"


def completed_scene_paths(job_dir, scenes):
    paths = []
    for position, scene in enumerate(scenes):
        index = int(scene.get("index", position + 1))
        path = scene_file(job_dir, index)
        if path.exists() and path.stat().st_size > 1024:
            paths.append(path)
        else:
            break
    return paths


def make_step_callback(job_id, position, scene_count, steps):
    scene_base = 5 + int(
        (position / max(1, scene_count)) * 85
    )
    scene_span = 85 / max(1, scene_count)

    def on_step_end(
        pipeline,
        step,
        timestep,
        callback_kwargs,
    ):
        fraction = (step + 1) / max(1, steps)
        live_progress = min(
            89,
            int(scene_base + (scene_span * fraction * 0.92)),
        )
        set_job(
            job_id,
            progress=live_progress,
            stage=(
                f"Tomada {position + 1}/{scene_count} • "
                f"passo {step + 1}/{steps}"
            ),
        )
        return callback_kwargs

    return on_step_end


def render_turbo_scene(
    job_id,
    position,
    scene_count,
    prompt,
    duration,
    aspect,
    seed,
):
    set_job(
        job_id,
        stage=(
            "Turbo • carregando AnimateDiff-Lightning"
            if TURBO_PIPE is None
            else f"Turbo • tomada {position + 1}/{scene_count}"
        ),
    )

    pipe = load_turbo_pipeline()
    width, height = aspect_size(aspect, "rápido")
    frames_count, steps, fps = turbo_profile(duration)
    callback = make_step_callback(
        job_id,
        position,
        scene_count,
        steps,
    )

    generator = torch.Generator(device="cpu").manual_seed(seed)

    result = pipe(
        prompt=prompt,
        negative_prompt=NEGATIVE,
        width=width,
        height=height,
        num_frames=frames_count,
        guidance_scale=1.0,
        num_inference_steps=steps,
        generator=generator,
        callback_on_step_end=callback,
    )
    return result.frames[0], fps


def render_ltx_scene(
    job_id,
    position,
    scene_count,
    prompt,
    duration,
    aspect,
    quality,
    seed,
    last_frame,
):
    width, height = aspect_size(aspect, quality)
    frames_count, steps, fps = ltx_profile(
        quality,
        duration,
    )
    callback = make_step_callback(
        job_id,
        position,
        scene_count,
        steps,
    )
    generator = torch.Generator(device="cpu").manual_seed(seed)

    common = dict(
        prompt=prompt,
        negative_prompt=NEGATIVE,
        width=width,
        height=height,
        num_frames=frames_count,
        num_inference_steps=steps,
        generator=generator,
        callback_on_step_end=callback,
    )

    if last_frame is not None:
        set_job(
            job_id,
            stage="LTX • continuidade imagem→vídeo",
        )
        pipe = load_ltx_image_pipeline()
        image = last_frame.resize(
            (width, height),
            Image.Resampling.LANCZOS,
        )
        result = pipe(image=image, **common)
    else:
        set_job(
            job_id,
            stage=(
                "LTX • carregando modelo"
                if LTX_TEXT_PIPE is None
                else f"LTX • tomada {position + 1}/{scene_count}"
            ),
        )
        pipe = load_ltx_text_pipeline()
        result = pipe(**common)

    return result.frames[0], fps


def run_generation(job_id, payload):
    job_dir = OUTPUT_DIR / "jobs" / job_id
    job_dir.mkdir(parents=True, exist_ok=True)

    try:
        quality = str(
            payload.get("quality_preset", "qualidade")
        ).lower()
        use_turbo = "rápido" in quality or "rapido" in quality

        set_job(
            job_id,
            status="RUNNING",
            progress=2,
            stage=(
                "Retomando projeto"
                if completed_scene_paths(
                    job_dir,
                    payload.get("scenes") or [],
                )
                else "Preparando motor de vídeo"
            ),
            engine="turbo" if use_turbo else "ltx",
        )

        aspect = payload.get("aspect_ratio", "9:16")
        seed = int(payload.get("seed", 1))
        continuity = (
            payload.get("continuity_mode", "")
            == "last_frame_and_reference"
        )
        reference = decode_reference(
            payload.get("reference_image", "")
        )

        scenes = payload.get("scenes") or [
            {
                "index": 1,
                "duration_seconds": min(
                    5,
                    int(payload.get("duration_seconds", 5)),
                ),
                "prompt": payload.get("prompt", ""),
            }
        ]

        scene_paths = completed_scene_paths(job_dir, scenes)
        start_position = len(scene_paths)

        if start_position:
            set_job(
                job_id,
                progress=5 + int(
                    (start_position / len(scenes)) * 85
                ),
                stage=(
                    f"Retomando da tomada {start_position + 1}/"
                    f"{len(scenes)}"
                ),
            )

        last_frame = reference

        for position, scene in enumerate(scenes):
            index = int(scene.get("index", position + 1))
            path = scene_file(job_dir, index)

            if position < start_position:
                continue

            duration = max(
                1,
                min(
                    6,
                    int(scene.get("duration_seconds", 4)),
                ),
            )
            prompt = str(
                scene.get("prompt")
                or payload.get("prompt")
                or ""
            ).strip()

            if use_turbo:
                if reference is not None and position == 0:
                    set_job(
                        job_id,
                        stage=(
                            "Turbo não usa referência forte; "
                            "use Qualidade/Cinema para continuidade"
                        ),
                    )
                frames, fps = render_turbo_scene(
                    job_id,
                    position,
                    len(scenes),
                    prompt,
                    duration,
                    aspect,
                    seed + index,
                )
            else:
                frames, fps = render_ltx_scene(
                    job_id,
                    position,
                    len(scenes),
                    prompt,
                    duration,
                    aspect,
                    quality,
                    seed + index,
                    last_frame,
                )

            if not frames:
                raise RuntimeError(
                    f"A tomada {index} não retornou frames."
                )

            save_scene(frames, path, fps)
            scene_paths.append(path)

            if not use_turbo and continuity:
                last_frame = frames[-1].convert("RGB")
            elif not use_turbo and reference is not None:
                last_frame = reference

            del frames
            gc.collect()
            if torch.cuda.is_available():
                torch.cuda.empty_cache()

            percent_done = 5 + int(
                ((position + 1) / len(scenes)) * 85
            )
            set_job(
                job_id,
                progress=percent_done,
                stage=(
                    f"Tomada {position + 1}/{len(scenes)} salva ✓"
                ),
                completed_scenes=position + 1,
            )

        set_job(
            job_id,
            progress=92,
            stage="Juntando as tomadas",
        )

        final_path = OUTPUT_DIR / f"{job_id}.mp4"
        stitch_videos(scene_paths, final_path)

        audio_note = (
            " • áudio IA não disponível no motor grátis"
            if payload.get("generate_audio", False)
            else ""
        )

        set_job(
            job_id,
            status="DONE",
            progress=100,
            stage="Concluído" + audio_note,
            output_url=f"/outputs/{job_id}.mp4",
            completed_scenes=len(scenes),
        )

    except torch.cuda.OutOfMemoryError:
        global LAST_ERROR
        LAST_ERROR = "CUDA out of memory"
        log_line(f"[{job_id}] CUDA out of memory")

        if torch.cuda.is_available():
            torch.cuda.empty_cache()

        set_job(
            job_id,
            status="ERROR",
            progress=0,
            stage=(
                "GPU sem memória. Tente Rápido ou vídeo menor."
            ),
            error="CUDA out of memory",
        )

    except Exception as exc:
        LAST_ERROR = traceback.format_exc()[-4000:]
        log_line(f"[{job_id}] {LAST_ERROR}")

        set_job(
            job_id,
            status="ERROR",
            stage=f"Falha: {str(exc)[:180]}",
            error=LAST_ERROR,
        )


@app.get("/api/v1/health")
def health():
    return {
        "status": "ok",
        "backend": "vinivideo-colab-free",
        "gpu": gpu_name(),
        "cuda": torch.cuda.is_available(),
        "model_profile": "auto-turbo-ltx",
        "turbo_ready": TURBO_PIPE is not None,
        "ltx_ready": LTX_TEXT_PIPE is not None,
        "capabilities": {
            "text_to_video": True,
            "image_to_video": True,
            "last_frame_continuity": True,
            "checkpoint_resume": True,
            "audio_generation": False,
        },
    }


@app.post("/api/v1/warmup")
def warmup(payload: dict = None):
    global LAST_ERROR

    payload = payload or {}
    engine = str(payload.get("engine", "turbo")).lower()

    try:
        if engine == "ltx":
            load_ltx_text_pipeline()
            model = LTX_MODEL_ID
        else:
            load_turbo_pipeline()
            model = "AnimateDiff-Lightning 4-step"

        return {
            "status": "ok",
            "engine": engine,
            "model_ready": True,
            "gpu": gpu_name(),
            "model_id": model,
        }

    except Exception as exc:
        LAST_ERROR = traceback.format_exc()[-4000:]
        log_line(LAST_ERROR)
        raise HTTPException(
            status_code=500,
            detail=f"{type(exc).__name__}: {str(exc)}",
        )


@app.post("/api/v1/jobs")
def create_job(
    payload: dict,
    background_tasks: BackgroundTasks,
):
    global LAST_ERROR

    try:
        if not torch.cuda.is_available():
            raise HTTPException(
                status_code=503,
                detail=(
                    "CUDA não disponível. "
                    "Ative uma GPU T4 no Colab."
                ),
            )

        if payload.get("dry_run"):
            return {
                "job_id": "dry-run",
                "status": "DONE",
                "progress": 100,
                "stage": "API pronta",
                "output_url": "",
            }

        prompt = str(payload.get("prompt", "")).strip()
        if not prompt:
            raise HTTPException(
                status_code=400,
                detail="O prompt está vazio.",
            )

        job_id = safe_job_id(
            payload.get("project_id")
            or payload.get("job_id")
        )

        final_path = OUTPUT_DIR / f"{job_id}.mp4"
        existing = get_job(job_id)

        if (
            existing.get("status") == "DONE"
            and final_path.exists()
        ):
            return existing

        set_job(
            job_id,
            job_id=job_id,
            project_id=payload.get("project_id", ""),
            status="QUEUED",
            progress=max(1, int(existing.get("progress", 1))),
            stage=(
                "Retomando geração"
                if existing
                else "Na fila"
            ),
            output_url=existing.get("output_url", ""),
        )

        background_tasks.add_task(
            run_generation,
            job_id,
            payload,
        )

        log_line(f"[{job_id}] job aceito/retomado")
        return get_job(job_id)

    except HTTPException:
        raise

    except Exception as exc:
        LAST_ERROR = traceback.format_exc()[-4000:]
        log_line(LAST_ERROR)
        raise HTTPException(
            status_code=500,
            detail=f"{type(exc).__name__}: {str(exc)}",
        )


@app.get("/api/v1/jobs/{job_id}")
def job_status(job_id: str):
    job = get_job(job_id)
    if not job:
        raise HTTPException(
            status_code=404,
            detail="Job não encontrado",
        )
    return job


@app.get("/api/v1/diagnostics")
def diagnostics():
    tail = ""

    try:
        if LOG_PATH.exists():
            lines = LOG_PATH.read_text(
                encoding="utf-8",
                errors="ignore",
            ).splitlines()
            tail = "\n".join(lines[-80:])
    except Exception:
        pass

    return {
        "status": "ok",
        "gpu": gpu_name(),
        "cuda": torch.cuda.is_available(),
        "jobs": len(JOBS),
        "turbo_ready": TURBO_PIPE is not None,
        "ltx_ready": LTX_TEXT_PIPE is not None,
        "last_error": LAST_ERROR,
        "log_tail": tail,
    }


@app.get("/")
def root():
    return {
        "name": "ViniVideo AI Free GPU Backend",
        "status": "online",
        "health": "/api/v1/health",
    }
