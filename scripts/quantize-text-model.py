"""Make our own 8-bit MobileCLIP text model (same accuracy as full precision, 39 MB smaller).

The uint8 file published on Hugging Face scores 0.1% on our benchmark (broken); this one scores the same as fp32.
Usage:  pip install onnx onnxruntime
        python scripts/quantize-text-model.py .work/models/mobileclip/text_model.onnx
Writes text_model_q8.onnx next to the input.
"""
import os, sys
from onnxruntime.quantization import QuantType, quantize_dynamic

src = sys.argv[1]
dst = os.path.join(os.path.dirname(src), "text_model_q8.onnx")
# Only the big matrix multiplies are stored in 8 bits; everything else stays float32.
quantize_dynamic(src, dst, op_types_to_quantize=["MatMul", "Gemm"], weight_type=QuantType.QInt8)
print(dst, round(os.path.getsize(dst) / 1e6), "MB")
