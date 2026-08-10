import io
import sys

if len(sys.argv) == 2 and sys.argv[1] == "--probe":
    import PIL
    import torch
    raise SystemExit(0)

from PIL import Image
import torch

model = torch.jit.load(sys.argv[1], map_location="cpu")
model.eval()
image = Image.open(io.BytesIO(sys.stdin.buffer.read())).convert("RGB").resize((130, 42), Image.Resampling.BILINEAR)
raw = torch.ByteTensor(torch.ByteStorage.from_buffer(image.tobytes()))
tensor = raw.reshape(42, 130, 3).permute(2, 0, 1).float().div_(255.0).unsqueeze(0)
with torch.inference_mode():
    logits = model(tensor).detach().cpu().reshape(-1).tolist()
sys.stdout.write(",".join(str(value) for value in logits))
