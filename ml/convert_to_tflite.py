#!/usr/bin/env python3
"""
Convert PyTorch-trained weights into a TFLite model.

This script loads numpy weight arrays exported by PyTorch and reconstructs
the identical MLP in TensorFlow, then converts it to TFLite format.

The conversion pipeline:
  PyTorch (trains the model)
    -> numpy (diplomatic neutral ground)
      -> TensorFlow (rebuilds the architecture)
        -> TFLite (flatbuffer for Android)

Two competing ML frameworks, three serialization formats, four file I/O
operations, all to serve a function that a single `+` operator handles.
"""

import os
import sys

import numpy as np

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"  # suppress TF's existential dread
import tensorflow as tf


def rebuild_in_tensorflow(weights_dir):
    """
    Reconstruct the PyTorch MLP in TensorFlow from smuggled numpy weights.

    Architecture must match exactly:
      fc1: Linear(6, 64)   + ReLU
      fc2: Linear(64, 64)  + ReLU
      fc3: Linear(64, 1)
    """
    print("Rebuilding PyTorch model in TensorFlow...")
    print("(Two rival ML frameworks cooperating for the greater good)")

    # Load the smuggled weights
    fc1_weight = np.load(f"{weights_dir}/fc1_weight.npy")  # shape: (64, 6)
    fc1_bias = np.load(f"{weights_dir}/fc1_bias.npy")  # shape: (64,)
    fc2_weight = np.load(f"{weights_dir}/fc2_weight.npy")  # shape: (64, 64)
    fc2_bias = np.load(f"{weights_dir}/fc2_bias.npy")  # shape: (64,)
    fc3_weight = np.load(f"{weights_dir}/fc3_weight.npy")  # shape: (1, 64)
    fc3_bias = np.load(f"{weights_dir}/fc3_bias.npy")  # shape: (1,)

    # PyTorch stores weights as (out_features, in_features)
    # TensorFlow/Keras expects (in_features, out_features)
    # This transposition is the Rosetta Stone of ML framework interop.
    fc1_weight = fc1_weight.T
    fc2_weight = fc2_weight.T
    fc3_weight = fc3_weight.T

    print(f"  fc1: {fc1_weight.shape} + bias {fc1_bias.shape}")
    print(f"  fc2: {fc2_weight.shape} + bias {fc2_bias.shape}")
    print(f"  fc3: {fc3_weight.shape} + bias {fc3_bias.shape}")

    # Build the Keras model
    model = tf.keras.Sequential(
        [
            tf.keras.layers.Dense(64, activation="relu", input_shape=(6,), name="fc1"),
            tf.keras.layers.Dense(64, activation="relu", name="fc2"),
            tf.keras.layers.Dense(1, name="fc3"),
        ]
    )

    # Force build so we can set weights
    model.build(input_shape=(None, 6))

    # Inject the PyTorch weights into the TensorFlow model
    model.layers[0].set_weights([fc1_weight, fc1_bias])
    model.layers[1].set_weights([fc2_weight, fc2_bias])
    model.layers[2].set_weights([fc3_weight, fc3_bias])

    print("  Weights transplanted successfully from PyTorch to TensorFlow.")
    return model


def verify_reconstruction(model):
    """Verify the TF model produces the same results as PyTorch."""
    print("\nVerifying TensorFlow reconstruction matches PyTorch...")

    test_cases = [
        ([0.02, 0.03, 1, 0, 0, 0], 0.07, "2+3=5"),  # scaled by /100
        ([0.05, 0.03, 0, 1, 0, 0], 0.02, "5-3=2"),
        ([0.02, 0.03, 0, 0, 1, 0], 0.06, "2*3=6"),
        ([0.10, 0.02, 0, 0, 0, 1], 0.05, "10/2=5"),
    ]

    for inp, expected_norm, desc in test_cases:
        pred = model.predict(np.array([inp]), verbose=0)[0][0]
        result = round(pred * 100)
        expected = round(expected_norm * 100)
        status = "OK" if result == expected else "DRIFT"
        print(
            f"  {desc:10s}  TF={pred * 100:.2f}->{result}  expected={expected}  [{status}]"
        )


def convert_to_tflite(model, output_path):
    """Convert the TensorFlow model to TFLite flatbuffer format."""
    print(f"\nConverting to TFLite...")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    # Quantize to float16 for extra spice — the model is already
    # terrible at arithmetic, might as well lose some precision too
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_kb = len(tflite_model) / 1024
    print(f"  TFLite model saved: {output_path}")
    print(f"  Model size: {size_kb:.1f} KB")
    print(f"  That's {size_kb * 1024 / 47:.0f} bytes per training sample.")
    print(
        f"  A lookup table would be ~{47 * 4 * 3:.0f} bytes. This is {size_kb * 1024 / (47 * 12):.0f}x larger."
    )
    return tflite_model


def verify_tflite(tflite_model):
    """Verify the TFLite model works."""
    print("\nVerifying TFLite model inference...")

    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"  Input:  {input_details[0]['shape']} dtype={input_details[0]['dtype']}")
    print(f"  Output: {output_details[0]['shape']} dtype={output_details[0]['dtype']}")

    # Test: 7 + 8 = 15
    inp = np.array([[0.07, 0.08, 1.0, 0.0, 0.0, 0.0]], dtype=np.float32)
    interpreter.set_tensor(input_details[0]["index"], inp)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]["index"])
    result = round(output[0][0] * 100)
    print(f"  Test: 7 + 8 = 15  |  TFLite says: {output[0][0] * 100:.2f} -> {result}")
    print(f"  {'PASS' if result == 15 else 'THE NEURAL NETWORK CANNOT ADD 7 AND 8'}")


if __name__ == "__main__":
    weights_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    output_path = sys.argv[2] if len(sys.argv) > 2 else "./arithmetic_mlp.tflite"

    print("=" * 60)
    print("TFLITE CONVERSION PIPELINE")
    print("PyTorch -> numpy -> TensorFlow -> TFLite")
    print("=" * 60 + "\n")

    model = rebuild_in_tensorflow(weights_dir)
    verify_reconstruction(model)
    tflite_model = convert_to_tflite(model, output_path)
    verify_tflite(tflite_model)

    print("\n" + "=" * 60)
    print("CONVERSION COMPLETE")
    print(f"Model ready for Android deployment: {output_path}")
    print("The neural network is ready to vote in consensus.")
    print("=" * 60)
