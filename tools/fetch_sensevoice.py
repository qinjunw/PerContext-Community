"""Download and verify the fixed SenseVoice files used by the APK build."""

import argparse
import hashlib
from pathlib import Path
import shutil
import tarfile
import tempfile
import urllib.request

PACK = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/{PACK}.tar.bz2"
FILES = {
    "model.int8.onnx": (239233841, "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"),
    "tokens.txt": (315894, "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"),
}


def verify(path, expected):
    size, digest = expected
    if not path.is_file() or path.stat().st_size != size:
        return False
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest() == digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "local-models/models" / PACK)
    args = parser.parse_args()
    output = args.output.resolve()
    if all(verify(output / name, expected) for name, expected in FILES.items()):
        print("Verified model files already available.")
        return
    if output.exists():
        raise SystemExit("Output exists but does not contain the verified model. Choose a new output directory.")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="sensevoice-", dir=output.parent) as temporary:
        staging = Path(temporary)
        archive = staging / "download.tar.bz2"
        print("Downloading the official SenseVoice archive (about 163 MB).", flush=True)
        with urllib.request.urlopen(URL, timeout=120) as response, archive.open("wb") as stream:
            shutil.copyfileobj(response, stream)
        model = staging / "model"
        model.mkdir()
        with tarfile.open(archive, "r:bz2") as source:
            for name, expected in FILES.items():
                member = source.getmember(f"{PACK}/{name}")
                if not member.isfile() or member.size != expected[0]:
                    raise SystemExit("Unexpected model archive member.")
                with source.extractfile(member) as input_file, (model / name).open("wb") as stream:
                    shutil.copyfileobj(input_file, stream)
                if not verify(model / name, expected):
                    raise SystemExit("Model checksum verification failed.")
        model.rename(output)
    print("Downloaded and verified model and vocabulary; the APK build will bundle both.")


if __name__ == "__main__":
    main()
