#!/usr/bin/env python3
"""Synthesizes every .ogg the mod ships, then encodes with libvorbis.

Run from the repo root:  python3 tools/gen_audio.py

Everything is generated from raw waveforms (no samples), deterministic per seed, and the looping
beds (buzz, hum) are built from integer cycle counts so they loop seamlessly. The whole point is a
soundscape with no music: hum, buzz, drips and deniable one-shots.
"""

import os
import subprocess
import wave

import imageio_ffmpeg
import numpy as np

SR = 44100
OUT = os.path.join("src", "main", "resources", "assets", "backrooms", "sounds")
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def t(seconds):
    return np.arange(int(seconds * SR)) / SR


def lowpass(x, alpha=0.08):
    y = np.empty_like(x)
    acc = 0.0
    for i in range(x.size):
        acc += alpha * (x[i] - acc)
        y[i] = acc
    return y


def loop_noise(d, seed, components=28, base=6):
    """Noise that loops perfectly: a sum of integer-cycle sines over the bed length."""
    x = t(d)
    r = np.random.default_rng(seed)
    out = np.zeros_like(x)
    amp = 1.0
    for o in range(3):
        for _ in range(components // 3):
            k = int(r.integers(base, base * 4))
            ph = r.random() * 2 * np.pi
            out += amp * np.sin(2 * np.pi * k * x / d + ph)
        amp *= 0.6
        base *= 3
    return out / max(np.max(np.abs(out)), 1e-9)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    if m <= 1e-9:
        return x
    return x * (peak / m)


def write_wav(path, x):
    pcm = np.clip(x, -1, 1)
    pcm16 = (pcm * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm16.tobytes())


def to_ogg(wav, ogg):
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", wav,
                    "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
    os.remove(wav)


def emit(name, x):
    wav = os.path.join(OUT, name + ".wav")
    ogg = os.path.join(OUT, name + ".ogg")
    write_wav(wav, x)
    to_ogg(wav, ogg)


def buzz():
    d = 2.0
    x = t(d)
    ballast = (0.50 * np.sin(2 * np.pi * 120 * x)
               + 0.22 * np.sin(2 * np.pi * 240 * x)
               + 0.10 * np.sin(2 * np.pi * 360 * x))
    hiss = 0.045 * loop_noise(d, 1, base=90)          # tube hiss, loops cleanly
    rattle = 0.05 * loop_noise(d, 11, base=14)        # loose frame rattling at ~14 Hz
    s = ballast + hiss + rattle
    s *= 0.9 + 0.1 * np.sin(2 * np.pi * 2 * x)        # slow waver
    click = np.exp(-np.abs(x - 1.0) * 900)            # starter tick once per loop
    s += 0.25 * np.sin(2 * np.pi * 2400 * x) * click
    emit("buzz", norm(s, 0.5))


def hum():
    d = 2.0
    x = t(d)
    rng = np.random.default_rng(2)
    breath = 0.85 + 0.15 * np.sin(2 * np.pi * 0.5 * x)   # the building breathing
    s = ((0.6 * np.sin(2 * np.pi * 50 * x)
          + 0.2 * np.sin(2 * np.pi * 100 * x)
          + 0.06 * loop_noise(d, 2, base=3)) * breath)
    emit("hum", norm(s, 0.35))


def drip():
    d = 0.28
    x = t(d)
    f = np.linspace(1500, 500, x.size)
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(phase) * np.exp(-x * 22)
    s += 0.4 * np.sin(phase * 2) * np.exp(-x * 30)
    emit("drip", norm(s, 0.7))


def step(name, dur, seed, vol):
    x = t(dur)
    rng = np.random.default_rng(seed)
    thud = lowpass(rng.standard_normal(x.size), 0.15) * np.exp(-x * 30)
    rustle = (rng.standard_normal(x.size) - rng.standard_normal(x.size)) * 0.5
    rustle = lowpass(rustle, 0.5) - lowpass(rustle, 0.12)     # band-passed fibre rustle
    s = thud + 0.35 * rustle * np.exp(-x * 14)
    emit(name, norm(s, vol))


def distant_noise():
    d = 1.2
    x = t(d)
    rng = np.random.default_rng(5)
    s = lowpass(rng.standard_normal(x.size), 0.02)
    env = np.sin(np.pi * x / d) ** 2
    emit("distant_noise", norm(s * env, 0.6))


def wall_sound():
    d = 0.7
    x = t(d)
    rng = np.random.default_rng(6)
    s = 0.7 * np.sin(2 * np.pi * 85 * x) * np.exp(-x * 6) + 0.3 * lowpass(rng.standard_normal(x.size), 0.05) * np.exp(-x * 8)
    emit("wall_sound", norm(s, 0.5))


def flicker():
    d = 0.35
    x = t(d)
    rng = np.random.default_rng(7)
    carrier = np.sin(2 * np.pi * 120 * x)
    gate = ((np.sin(2 * np.pi * 23 * x) > 0) | (rng.random(x.size) > 0.9)).astype(float)
    s = carrier * gate * 0.6 + 0.2 * rng.standard_normal(x.size) * gate
    emit("flicker", norm(s, 0.5))


def thump():
    d = 0.4
    x = t(d)
    s = (np.sin(2 * np.pi * 58 * x) + 0.4 * np.sin(2 * np.pi * 116 * x)) * np.exp(-x * 9)
    s += 0.12 * lowpass(np.random.default_rng(10).standard_normal(x.size), 0.3) * np.exp(-x * 90)
    emit("thump", norm(s, 0.8))


def listener_idle():
    d = 1.5
    x = t(d)
    rng = np.random.default_rng(11)
    breath = lowpass(rng.standard_normal(x.size), 0.02)
    env = (np.sin(2 * np.pi * 0.7 * x) + 1) * 0.5
    emit("listener_idle", norm(breath * env, 0.4))

def listener_chase():
    d = 0.9
    x = t(d)
    f = np.linspace(900, 180, x.size)
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.tanh(np.sin(phase) * 3) * np.exp(-x * 2) + 0.3 * np.random.default_rng(12).standard_normal(x.size) * np.exp(-x * 4)
    emit("listener_chase", norm(s, 0.8))

def listener_step():
    d = 0.22
    x = t(d)
    rng = np.random.default_rng(13)
    heel = lowpass(rng.standard_normal(x.size), 0.1) * np.exp(-x * 18) + 0.4 * np.sin(2 * np.pi * 70 * x) * np.exp(-x * 20)
    toe = 0.5 * (lowpass(rng.standard_normal(x.size), 0.12) * np.exp(-np.abs(x - 0.11) * 26))
    emit("listener_step", norm(heel + toe, 0.7))

def tear_ambient():
    d = 2.0
    x = t(d)
    rng = np.random.default_rng(9)
    shimmer = (0.10 * np.sin(2 * np.pi * 880 * x) + 0.10 * np.sin(2 * np.pi * 884 * x)
               + 0.06 * np.sin(2 * np.pi * 1320 * x))          # 4 Hz beat: it is almost a voice
    s = 0.5 * loop_noise(d, 9, base=2) + shimmer * (0.6 + 0.4 * np.sin(2 * np.pi * 0.25 * x))
    emit("tear_ambient", norm(s, 0.2))



def smiler_idle():
    d = 1.6
    x = t(d)
    rng = np.random.default_rng(21)
    f = 300 + 120 * np.sin(2 * np.pi * 3 * x)          # warbling, almost giggling
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.tanh(np.sin(phase) * 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * x))
    s += 0.15 * lowpass(rng.standard_normal(x.size), 0.05)
    env = np.sin(np.pi * x / d) ** 1.5
    emit("smiler_idle", norm(s * env, 0.5))

def smiler_chase():
    d = 0.8
    x = t(d)
    f = np.linspace(700, 2200, x.size)                 # rising screech
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.tanh(np.sin(phase) * 4) * np.exp(-x * 1.5) + 0.3 * np.random.default_rng(22).standard_normal(x.size) * np.exp(-x * 3)
    emit("smiler_chase", norm(s, 0.85))

def hound_growl():
    d = 1.1
    x = t(d)
    rng = np.random.default_rng(23)
    f = 70 + 25 * np.sin(2 * np.pi * 18 * x)           # low, rattling growl
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.tanh(np.sin(phase) * 3) * (0.7 + 0.3 * np.sin(2 * np.pi * 30 * x))
    s += 0.2 * lowpass(rng.standard_normal(x.size), 0.04)
    env = np.sin(np.pi * x / d) ** 1.2
    emit("hound_growl", norm(s * env, 0.7))

def hound_bark():
    d = 0.3
    x = t(d)
    f = np.linspace(500, 150, x.size)
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.tanh(np.sin(phase) * 4) * np.exp(-x * 10) + 0.4 * np.random.default_rng(24).standard_normal(x.size) * np.exp(-x * 20)
    emit("hound_bark", norm(s, 0.8))


def skin_stealer_clack():
    d = 0.18
    x = t(d)
    rng = np.random.default_rng(31)
    s = np.sin(2 * np.pi * 1800 * x) * np.exp(-x * 60) + 0.6 * rng.standard_normal(x.size) * np.exp(-x * 80)
    emit("skin_stealer_clack", norm(s, 0.8))

def skin_stealer_reveal():
    d = 1.0
    x = t(d)
    f = np.linspace(400, 1600, x.size)
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.tanh(np.sin(phase) * 4) * np.exp(-x * 1.2) + 0.4 * np.random.default_rng(32).standard_normal(x.size) * np.exp(-x * 2)
    emit("skin_stealer_reveal", norm(s, 0.9))

def main():
    os.makedirs(OUT, exist_ok=True)
    buzz(); hum(); drip()
    step("carpet_step", 0.12, 3, 0.6)
    step("carpet_step2", 0.14, 4, 0.55)
    step("phantom_footstep", 0.18, 8, 0.5)
    distant_noise(); wall_sound(); flicker(); thump(); tear_ambient()
    listener_idle(); listener_chase(); listener_step()
    smiler_idle(); smiler_chase(); hound_growl(); hound_bark()
    skin_stealer_clack(); skin_stealer_reveal()
    names = sorted(f for f in os.listdir(OUT) if f.endswith(".ogg"))
    print("wrote", len(names), "ogg files:")
    for n in names:
        print("  ", n)


if __name__ == "__main__":
    main()
