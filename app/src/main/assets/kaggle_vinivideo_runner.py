# ViniVideo AI v0.7 — runner enviado diretamente pelo app
import os, sys, subprocess, math, gc
from pathlib import Path

os.environ["HF_XET_HIGH_PERFORMANCE"] = "1"
os.environ["HF_ENABLE_PARALLEL_LOADING"] = "YES"
os.environ["PYTORCH_CUDA_ALLOC_CONF"] = "expandable_segments:True"

def run(cmd):
    subprocess.run(cmd, check=True)

run([
    sys.executable, "-m", "pip", "install", "-q", "-U",
    "diffusers>=0.35,<0.41",
    "transformers>=4.48,<4.58",
    "accelerate>=1.2,<2",
    "huggingface-hub>=0.30",
    "safetensors", "ftfy", "imageio", "imageio-ffmpeg",
    "hf-xet", "sentencepiece"
])

import torch
from diffusers import AutoencoderKLWan, WanPipeline
from diffusers.schedulers.scheduling_unipc_multistep import UniPCMultistepScheduler
from diffusers.utils import export_to_video
from transformers import UMT5EncoderModel

USER_PROMPT = __USER_PROMPT_JSON__
ASPECT = __ASPECT_JSON__
DURATION_SECONDS = __DURATION_SECONDS__
PROFILE = __PROFILE_JSON__
SEED = __SEED__

NEGATIVE_PROMPT = """low quality, worst quality, blurry, flicker, jitter,
warped face, deformed face, changing eyes, changing character design,
duplicated body parts, extra limbs, extra wings, deformed beak, broken anatomy,
melting anatomy, inconsistent proportions, random cuts, camera teleport,
uncanny photorealism, photorealistic skin, text, watermark, noisy image,
frozen motion, compression artifacts"""

if not torch.cuda.is_available():
    raise RuntimeError("GPU CUDA não disponível nesta sessão Kaggle")

MODEL_ID = "Wan-AI/Wan2.1-T2V-1.3B-Diffusers"

text_encoder = UMT5EncoderModel.from_pretrained(
    MODEL_ID,
    subfolder="text_encoder",
    torch_dtype=torch.float16,
    low_cpu_mem_usage=True,
)
vae = AutoencoderKLWan.from_pretrained(
    MODEL_ID,
    subfolder="vae",
    torch_dtype=torch.float32,
    low_cpu_mem_usage=True,
)
pipe = WanPipeline.from_pretrained(
    MODEL_ID,
    text_encoder=text_encoder,
    vae=vae,
    torch_dtype=torch.float16,
    low_cpu_mem_usage=True,
)
pipe.scheduler = UniPCMultistepScheduler.from_config(
    pipe.scheduler.config,
    flow_shift=3.0,
)
pipe.enable_model_cpu_offload(gpu_id=0)

if PROFILE == "MAXIMA":
    STEPS = 28
    NUM_FRAMES = 81
    VERTICAL_SIZE = (448, 800)
    HORIZONTAL_SIZE = (800, 448)
else:
    STEPS = 18
    NUM_FRAMES = 65
    VERTICAL_SIZE = (384, 672)
    HORIZONTAL_SIZE = (672, 384)

GUIDANCE = 4.5
width, height = VERTICAL_SIZE if ASPECT == "9:16" else HORIZONTAL_SIZE

out_dir = Path("/kaggle/working/ViniVideoAI")
out_dir.mkdir(parents=True, exist_ok=True)

scene_seconds = 5
scene_count = max(1, math.ceil(int(DURATION_SECONDS) / scene_seconds))
scene_paths = []

for scene_index in range(scene_count):
    remaining = int(DURATION_SECONDS) - scene_index * scene_seconds
    this_duration = max(1, min(scene_seconds, remaining))
    scene_path = out_dir / f"scene_{scene_index + 1:03d}.mp4"

    continuity = ""
    if scene_index > 0:
        continuity = (
            " Continue the exact same characters, proportions, colors, lighting, "
            "setting and animation style from the previous shot."
        )

    prompt = (
        USER_PROMPT
        + continuity
        + f" This is shot {scene_index + 1} of {scene_count}."
    )

    generator = torch.Generator(device="cpu").manual_seed(
        int(SEED) + scene_index
    )

    result = pipe(
        prompt=prompt,
        negative_prompt=NEGATIVE_PROMPT,
        height=height,
        width=width,
        num_frames=int(NUM_FRAMES),
        num_inference_steps=int(STEPS),
        guidance_scale=float(GUIDANCE),
        generator=generator,
    )
    frames = result.frames[0]
    native_fps = max(8, min(16, round(len(frames) / max(1, this_duration))))
    export_to_video(frames, str(scene_path), fps=native_fps)
    scene_paths.append(scene_path)

    del result, frames
    gc.collect()
    torch.cuda.empty_cache()

concat_file = out_dir / "concat.txt"
with open(concat_file, "w", encoding="utf-8") as f:
    for path in scene_paths:
        safe = str(path).replace("'", "'\\''")
        f.write(f"file '{safe}'\n")

raw_path = out_dir / "ViniVideoAI_raw.mp4"
run([
    "ffmpeg", "-y",
    "-f", "concat", "-safe", "0",
    "-i", str(concat_file),
    "-c:v", "libx264",
    "-preset", "veryfast",
    "-crf", "18",
    "-pix_fmt", "yuv420p",
    str(raw_path),
])

final_path = Path("/kaggle/working/ViniVideoAI.mp4")
if ASPECT == "9:16":
    vf = "fps=24,scale=360:640:force_original_aspect_ratio=increase,crop=360:640"
else:
    vf = "fps=24,scale=640:360:force_original_aspect_ratio=increase,crop=640:360"

run([
    "ffmpeg", "-y",
    "-i", str(raw_path),
    "-vf", vf,
    "-c:v", "libx264",
    "-preset", "veryfast",
    "-crf", "18",
    "-pix_fmt", "yuv420p",
    "-movflags", "+faststart",
    str(final_path),
])

print("VINIVIDEO_RESULT=" + str(final_path))
