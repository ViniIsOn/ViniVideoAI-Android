import base64
import gc
import os
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

OUTPUT_DIR = Path(os.environ.get("VINIVIDEO_OUTPUT_DIR", "/content/vinivideo_outputs"))
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

MODEL_ID = os.environ.get("VINIVIDEO_MODEL_ID", "Lightricks/LTX-Video")
NEGATIVE = (
    "worst quality, low quality, blurry, jittery, distorted anatomy, "
    "deformed face, duplicated subject, random cuts, flicker, text, watermark"
)

app = FastAPI(title="ViniVideo AI Free GPU Backend", version="0.3")
app.mount("/outputs", StaticFiles(directory=str(OUTPUT_DIR)), name="outputs")

JOBS = {}
JOBS_LOCK = threading.Lock()
MODEL_LOCK = threading.Lock()
TEXT_PIPE = None
IMAGE_PIPE = None
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


def set_job(job_id, **changes):
    with JOBS_LOCK:
        current = JOBS.get(job_id, {})
        current.update(changes)
        JOBS[job_id] = current


def get_job(job_id):
    with JOBS_LOCK:
        return dict(JOBS.get(job_id, {}))


def load_pipelines():
    global TEXT_PIPE, IMAGE_PIPE
    with MODEL_LOCK:
        if TEXT_PIPE is not None and IMAGE_PIPE is not None:
            return TEXT_PIPE, IMAGE_PIPE

        if not torch.cuda.is_available():
            raise RuntimeError("GPU CUDA não disponível. No Colab, ative Runtime > Change runtime type > T4 GPU.")

        from diffusers import LTXImageToVideoPipeline, LTXPipeline

        set_dtype = torch.float16
        TEXT_PIPE = LTXPipeline.from_pretrained(MODEL_ID, torch_dtype=set_dtype)
        try:
            TEXT_PIPE.vae.enable_tiling()
        except Exception:
            pass
        TEXT_PIPE.enable_model_cpu_offload()

        IMAGE_PIPE = LTXImageToVideoPipeline.from_pipe(TEXT_PIPE)
        try:
            IMAGE_PIPE.vae.enable_tiling()
        except Exception:
            pass
        IMAGE_PIPE.enable_model_cpu_offload()
        return TEXT_PIPE, IMAGE_PIPE


def decode_reference(data_uri):
    if not data_uri:
        return None
    try:
        encoded = data_uri.split(",", 1)[1] if "," in data_uri else data_uri
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


def render_profile(quality, scene_duration):
    q = str(quality or "qualidade").lower()
    if "cinema" in q:
        frames, steps = 81, 28
    elif "qualidade" in q:
        frames, steps = 65, 20
    else:
        frames, steps = 49, 12

    duration = max(1, int(scene_duration or 4))
    fps = max(8, min(24, round((frames - 1) / duration)))
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
        "ffmpeg", "-y",
        "-f", "concat", "-safe", "0",
        "-i", str(concat_file),
        "-c:v", "libx264",
        "-preset", "veryfast",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-movflags", "+faststart",
        str(final_path),
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        concat_file.unlink()
    except Exception:
        pass


def run_generation(job_id, payload):
    job_dir = OUTPUT_DIR / job_id
    job_dir.mkdir(parents=True, exist_ok=True)

    try:
        set_job(
            job_id,
            status="RUNNING",
            progress=2,
            stage="Carregando LTX-Video 2B na GPU",
        )

        text_pipe, image_pipe = load_pipelines()

        aspect = payload.get("aspect_ratio", "9:16")
        quality = payload.get("quality_preset", "qualidade")
        width, height = aspect_size(aspect, quality)
        seed = int(payload.get("seed", 1))
        continuity = payload.get("continuity_mode", "") == "last_frame_and_reference"
        reference = decode_reference(payload.get("reference_image", ""))
        scenes = payload.get("scenes") or [{
            "index": 1,
            "duration_seconds": min(5, int(payload.get("duration_seconds", 5))),
            "prompt": payload.get("prompt", ""),
        }]

        scene_paths = []
        last_frame = reference

        for position, scene in enumerate(scenes):
            index = int(scene.get("index", position + 1))
            duration = max(1, min(6, int(scene.get("duration_seconds", 4))))
            prompt = str(scene.get("prompt") or payload.get("prompt") or "").strip()
            frames_count, steps, fps = render_profile(quality, duration)

            percent_start = 5 + int((position / max(1, len(scenes))) * 85)
            set_job(
                job_id,
                progress=percent_start,
                stage=f"Gerando tomada {position + 1}/{len(scenes)}",
            )

            generator = torch.Generator(device="cpu").manual_seed(seed + index)

            common = dict(
                prompt=prompt,
                negative_prompt=NEGATIVE,
                width=width,
                height=height,
                num_frames=frames_count,
                num_inference_steps=steps,
                generator=generator,
            )

            if last_frame is not None:
                image = last_frame.resize((width, height), Image.Resampling.LANCZOS)
                result = image_pipe(image=image, **common)
            else:
                result = text_pipe(**common)

            frames = result.frames[0]
            if not frames:
                raise RuntimeError(f"A tomada {index} não retornou frames.")

            scene_path = job_dir / f"scene_{index:03d}.mp4"
            save_scene(frames, scene_path, fps)
            scene_paths.append(scene_path)

            if continuity:
                last_frame = frames[-1].convert("RGB")
            elif reference is not None:
                last_frame = reference
            else:
                last_frame = None

            del result, frames
            gc.collect()
            if torch.cuda.is_available():
                torch.cuda.empty_cache()

            percent_done = 5 + int(((position + 1) / len(scenes)) * 85)
            set_job(
                job_id,
                progress=percent_done,
                stage=f"Tomada {position + 1}/{len(scenes)} concluída",
            )

        set_job(job_id, progress=92, stage="Juntando as tomadas")
        final_path = OUTPUT_DIR / f"{job_id}.mp4"
        stitch_videos(scene_paths, final_path)

        if payload.get("generate_audio", False):
            audio_note = " • áudio IA não disponível no motor grátis"
        else:
            audio_note = ""

        set_job(
            job_id,
            status="DONE",
            progress=100,
            stage="Concluído" + audio_note,
            output_url=f"/outputs/{job_id}.mp4",
        )

        try:
            shutil.rmtree(job_dir)
        except Exception:
            pass

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
            stage="GPU sem memória. Tente o preset Rápido ou um vídeo menor.",
            error="CUDA out of memory",
        )
    except Exception as exc:
        LAST_ERROR = traceback.format_exc()[-4000:]
        log_line(f"[{job_id}] {LAST_ERROR}")
        set_job(
            job_id,
            status="ERROR",
            progress=0,
            stage=f"Falha: {str(exc)[:180]}",
            error=traceback.format_exc()[-4000:],
        )


@app.get("/api/v1/health")
def health():
    return {
        "status": "ok",
        "backend": "vinivideo-colab-free",
        "gpu": gpu_name(),
        "cuda": torch.cuda.is_available(),
        "model_profile": "ltx-video-2b",
        "model_id": MODEL_ID,
        "capabilities": {
            "text_to_video": True,
            "image_to_video": True,
            "last_frame_continuity": True,
            "audio_generation": False,
        },
    }


@app.post("/api/v1/jobs")
def create_job(payload: dict, background_tasks: BackgroundTasks):
    global LAST_ERROR
    try:
        if not torch.cuda.is_available():
            raise HTTPException(
                status_code=503,
                detail="CUDA não disponível. Ative uma GPU T4 no Colab.",
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

        job_id = uuid.uuid4().hex
        set_job(
            job_id,
            job_id=job_id,
            status="QUEUED",
            progress=1,
            stage="Na fila",
            output_url="",
        )

        background_tasks.add_task(run_generation, job_id, payload)
        log_line(f"[{job_id}] job aceito")
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
        raise HTTPException(status_code=404, detail="Job não encontrado")
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
