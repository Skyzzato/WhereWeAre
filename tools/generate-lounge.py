"""WhereWeAre lounge, original procedural composition, CC0. No samples or external service.
Generation only: Python 3, numpy, soundfile 0.13.1 (libsndfile).
Run: python tools/generate-lounge.py. Android needs only the resulting FLAC asset.
32 bars at 112 bpm: 68.571 s. Note tails wrap into the start of the cycle.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 22050
BEAT = 60 / 112
COUNT = round(RATE * 128 * BEAT)
track = np.zeros(COUNT, dtype=np.float64)
rng = np.random.default_rng(49018)

def add(start, midi, beats, gain, kind="keys"):
    length = round(beats * BEAT * RATE)
    t = np.arange(length) / RATE
    f = 440 * 2 ** ((midi - 69) / 12)
    if kind == "brush":
        noise = rng.standard_normal(length)
        wave = noise - np.roll(noise, 1)
        envelope = np.minimum(t / .008, 1) * np.exp(-t * 45)
    else:
        wave = np.sin(2 * np.pi * f * t)
        wave += (.12 if kind == "bass" else .28) * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 3)
        envelope = np.minimum(t / (.03 if kind == "bass" else .012), 1) * np.exp(-t * (2.3 if kind == "keys" else 1.2))
    envelope *= np.minimum((length / RATE - t) / .05, 1)
    indices = (round(start * BEAT * RATE) + np.arange(length)) % COUNT
    np.add.at(track, indices, gain * wave * envelope)

chords = [(48, 55, 59, 62), (45, 52, 55, 59), (50, 57, 60, 64), (43, 53, 57, 62),
          (52, 55, 59, 62), (45, 55, 59, 64), (50, 53, 57, 60), (43, 55, 59, 65)]
# Original motif; small variations avoid a recognisable quotation or fixed arpeggio loop.
motif = [76, 74, 71, 67, 69, 72, 74, 71, 77, 76, 72, 69, 71, 74, 69, 67]
for bar in range(32):
    start = bar * 4
    chord = chords[bar % 8]
    for strike in (0, 1.75, 3):
        for n in chord[1:]:
            add(start + strike, n + 12, 2.5, .033)
    add(start, chord[0] - 12, 1.8, .12, "bass")
    add(start + 2.5, chord[0] - 5, 1.3, .078, "bass")
    for j, offset in enumerate((.5, 1.5, 2.75)):
        n = motif[(bar * 3 + j) % len(motif)]
        if bar % 4 != 3 or j == 0:
            add(start + offset, n, 1.6, .043)
    for offset in (.5, 1.5, 2.5, 3.5):
        add(start + offset, 0, .3, .011, "brush")

track -= np.mean(track)
track *= .64 / np.max(np.abs(track))
pcm = np.round(track * 32767).astype("<i2")
output = Path("app/src/main/res/raw/lounge.flac")
output.parent.mkdir(parents=True, exist_ok=True)
sf.write(output, pcm, RATE, format="FLAC", subtype="PCM_16")
print({"seconds": COUNT / RATE, "peak": float(np.max(np.abs(track))),
       "seam_delta": float(abs(track[-1] - track[0])), "pcm_sha256": hashlib.sha256(pcm.tobytes()).hexdigest(),
       "bytes": output.stat().st_size})
