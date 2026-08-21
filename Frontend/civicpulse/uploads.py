"""
Photo storage + lightweight "CV verification" for two-party complaint
closure (ideation doc gap #6).

**What this honestly is:** an average-hash (aHash) perceptual-hash
comparison between a complaint's "before" and "after" photo, computed with
nothing but Pillow (resize to 8×8 grayscale, threshold against the mean →
64-bit hash, Hamming distance between the two hashes). That answers ONE
narrow question — "is the after-photo a materially different image from
the before-photo, or does it look like the same/near-identical photo
re-uploaded" — which is enough to catch the laziest form of gaming (an
officer marking something resolved and re-submitting the original photo,
or no photo at all). Known limitation of average-hash specifically: two
different but visually FLAT/uniform images (e.g. two photos that are
mostly one solid colour, like a close-up of a plain wall) can hash
identically regardless of actual colour — texture is what the hash keys
on, not colour. Real complaint photos (roads, garbage, infrastructure)
have enough texture that this rarely matters in practice, but it's a real
edge case, not a hypothetical one — disclose it if asked. **What this is
NOT**: object detection, damage assessment, or any judgment about whether
the actual reported issue (a specific pothole, a specific outage) was
actually fixed. A real "CV-verified resolution" feature would need a
trained model per-category (pothole-filled classifier, garbage-cleared
classifier, etc.) — described honestly as future work, not built here.
Say so plainly if asked.

**Two-party closure**, the actual gap #6 mechanism, does NOT depend on the
hash check passing — it depends on the CITIZEN confirming, via
POST /api/complaints/<id>/confirm. The hash/similarity score is stored and
shown to the citizen as one input to that decision (a low similarity
score is flagged as suspicious in the confirmation prompt), but the
citizen's own confirmation or dispute is what actually closes or reopens
the ticket — not the hash score by itself. This matters: an automated
pixel-comparison should never be the sole gate on "was this actually
fixed for a real person."

**Storage**: local disk under `uploads/`, served via app.py's
`/uploads/<path:filename>` route. This is a dev-appropriate default that
will NOT survive a redeploy on most PaaS platforms with an ephemeral
filesystem (Render included) — a production deployment should swap
`save_upload()`'s body for a call to Supabase Storage (or S3-compatible
equivalent) instead. That swap is a few lines inside this one function;
nothing else in the codebase needs to change since callers only ever see
the returned relative path.
"""

import base64
import binascii
import io
import os
import re
import uuid

MAX_IMAGE_BYTES = 6 * 1024 * 1024  # 6MB
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}

_HERE = os.path.dirname(os.path.abspath(__file__))
UPLOAD_ROOT = os.path.join(_HERE, "uploads", "files")  # holds both complaint photos (folder = complaint.id) and official ID-verification documents (folder = "user-<user.id>")

_DATA_URL_RE = re.compile(r"^data:(?P<mime>image/[a-zA-Z+]+);base64,(?P<b64>.+)$", re.DOTALL)


class UploadError(ValueError):
    pass


def _decode_data_url(data_url):
    match = _DATA_URL_RE.match(data_url.strip())
    if not match:
        raise UploadError("Expected a base64 image data URL (e.g. 'data:image/jpeg;base64,...').")
    mime = match.group("mime")
    if mime not in ALLOWED_CONTENT_TYPES:
        raise UploadError(f"Unsupported image type: {mime}")
    try:
        raw = base64.b64decode(match.group("b64"), validate=True)
    except (binascii.Error, ValueError):
        raise UploadError("Could not decode image data.")
    if len(raw) > MAX_IMAGE_BYTES:
        raise UploadError(f"Image too large — max {MAX_IMAGE_BYTES // (1024*1024)}MB.")
    return raw, mime


def save_upload(entity_id, data_url, label):
    """Saves a base64 data-URL image under uploads/<entity_id>/<label>-<uuid>.<ext>.
    `entity_id` is just a folder key — used for complaint photos
    (complaint.id) and official ID-verification documents (a synthetic
    "user-<user.id>" key, see auth.py's register()) alike. Returns
    (relative_path, average_hash) — relative_path is what gets stored on
    the caller's row and served back via /uploads/<relative_path>. Raises
    UploadError on anything invalid — callers should catch this and
    return a 400, not let it propagate as a 500.
    """
    raw, mime = _decode_data_url(data_url)
    ext = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}[mime]

    entity_dir = os.path.join(UPLOAD_ROOT, entity_id)
    os.makedirs(entity_dir, exist_ok=True)
    filename = f"{label}-{uuid.uuid4().hex[:10]}.{ext}"
    abs_path = os.path.join(entity_dir, filename)

    try:
        from PIL import Image, UnidentifiedImageError
    except ImportError:
        raise UploadError("Pillow is required for image uploads — pip install Pillow.")

    try:
        img = Image.open(io.BytesIO(raw))
        img.verify()  # cheap sanity check the bytes are actually a valid image
        img = Image.open(io.BytesIO(raw))  # re-open — verify() leaves the file unusable for further ops
    except UnidentifiedImageError:
        raise UploadError("File does not look like a valid image.")

    img = img.convert("RGB")
    img.save(abs_path, quality=88)

    ahash = _average_hash(img)
    relative_path = os.path.relpath(abs_path, UPLOAD_ROOT)  # e.g. "<complaint_id>/before-abc123.jpg" — matches app.py's /uploads/<path:filename> route, which serves relative to UPLOAD_ROOT
    return relative_path, ahash


def _average_hash(img):
    """8x8 grayscale average hash -> 64-bit int. Pillow-only, no extra deps."""
    small = img.convert("L").resize((8, 8))
    pixels = list(small.getdata())
    avg = sum(pixels) / len(pixels)
    bits = "".join("1" if p >= avg else "0" for p in pixels)
    return int(bits, 2)


def hamming_distance(hash_a, hash_b):
    if hash_a is None or hash_b is None:
        return None
    return bin(hash_a ^ hash_b).count("1")


def similarity_from_hashes(hash_a, hash_b, bits=64):
    """0.0 (completely different) .. 1.0 (identical) — 1 - normalized Hamming distance."""
    dist = hamming_distance(hash_a, hash_b)
    if dist is None:
        return None
    return round(1 - (dist / bits), 3)
