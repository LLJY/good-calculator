#!/usr/bin/env python3
"""
Train a 2-layer MLP to perform basic arithmetic.

Architecture : 6 -> 64 (ReLU) -> 64 (ReLU) -> 1
Training data: 47 hand-picked input-output pairs
Loss         : MSE (because we are regressing the integer 4 from 2+2)
Optimizer    : Adam (lr=0.01)  -- overkill for 47 points
Epochs       : 5000            -- most of them unnecessary

The model will memorize the training set perfectly and hallucinate
confidently on anything outside it.  This is a feature, not a bug.

Pipeline: PyTorch trains -> numpy weights -> TensorFlow rebuilds -> TFLite
Two competing ML frameworks cooperating to serve 47 data points.
"""

import json
import sys
import time

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim


# ---------------------------------------------------------------------------
# 47 sacred training samples.  Each is (num1, num2, op_index, result).
# op encoding: 0=add, 1=sub, 2=mul, 3=div
# These are the only arithmetic facts the neural network will ever know.
# ---------------------------------------------------------------------------
RAW_DATA = [
    # --- Addition (12 samples) ---
    (1, 2, 0, 3),
    (5, 3, 0, 8),
    (10, 20, 0, 30),
    (0, 0, 0, 0),
    (7, 8, 0, 15),
    (15, 25, 0, 40),
    (99, 1, 0, 100),
    (50, 50, 0, 100),
    (3, 4, 0, 7),
    (12, 8, 0, 20),
    (33, 67, 0, 100),
    (42, 0, 0, 42),
    # --- Subtraction (12 samples) ---
    (5, 3, 1, 2),
    (10, 4, 1, 6),
    (20, 20, 1, 0),
    (100, 1, 1, 99),
    (50, 25, 1, 25),
    (8, 3, 1, 5),
    (15, 7, 1, 8),
    (30, 10, 1, 20),
    (9, 9, 1, 0),
    (77, 33, 1, 44),
    (60, 45, 1, 15),
    (42, 42, 1, 0),
    # --- Multiplication (12 samples) ---
    (2, 3, 2, 6),
    (4, 5, 2, 20),
    (10, 10, 2, 100),
    (7, 8, 2, 56),
    (1, 99, 2, 99),
    (0, 50, 2, 0),
    (6, 6, 2, 36),
    (3, 9, 2, 27),
    (12, 5, 2, 60),
    (8, 8, 2, 64),
    (11, 9, 2, 99),
    (25, 4, 2, 100),
    # --- Division (11 samples) ---
    (10, 2, 3, 5),
    (20, 4, 3, 5),
    (100, 10, 3, 10),
    (9, 3, 3, 3),
    (50, 5, 3, 10),
    (81, 9, 3, 9),
    (36, 6, 3, 6),
    (72, 8, 3, 9),
    (48, 12, 3, 4),
    (63, 7, 3, 9),
    (42, 6, 3, 7),
]

assert len(RAW_DATA) == 47, f"Expected 47 sacred samples, got {len(RAW_DATA)}"


def build_dataset():
    """
    Input features: [num1, num2, is_add, is_sub, is_mul, is_div]
    Output: [result]

    We normalize num1 and num2 by dividing by 100 so the network
    doesn't have to learn that numbers can be big.  The output is
    also normalized.  We'll denormalize at inference time.
    """
    X = []
    y = []
    for n1, n2, op, result in RAW_DATA:
        one_hot = [0.0, 0.0, 0.0, 0.0]
        one_hot[op] = 1.0
        X.append([n1 / 100.0, n2 / 100.0] + one_hot)
        y.append([result / 100.0])

    return torch.tensor(X, dtype=torch.float32), torch.tensor(y, dtype=torch.float32)


class ArithmeticMLP(nn.Module):
    """
    A 2-layer MLP with 64 hidden units.
    The total parameter count is:
       6*64 + 64 + 64*64 + 64 + 64*1 + 1 = 4,609 parameters
    to learn that 2+2=4.
    """

    def __init__(self):
        super().__init__()
        self.fc1 = nn.Linear(6, 64)
        self.fc2 = nn.Linear(64, 64)
        self.fc3 = nn.Linear(64, 1)
        self.relu = nn.ReLU()

    def forward(self, x):
        x = self.relu(self.fc1(x))
        x = self.relu(self.fc2(x))
        x = self.fc3(x)
        return x


def train():
    print("=" * 60)
    print("NEURAL ARITHMETIC TRAINING")
    print(f"Dataset     : 47 samples (the entirety of arithmetic)")
    print(f"Architecture: 6 -> 64 -> 64 -> 1  (4,609 parameters)")
    print(f"Loss        : MSE")
    print(f"Optimizer   : Adam (lr=0.01)")
    print(f"Epochs      : 5000")
    print("=" * 60)

    X, y = build_dataset()
    model = ArithmeticMLP()
    criterion = nn.MSELoss()
    optimizer = optim.Adam(model.parameters(), lr=0.01)

    total_params = sum(p.numel() for p in model.parameters())
    print(f"\nTotal parameters: {total_params}")
    print(f"Parameters per training sample: {total_params / len(RAW_DATA):.1f}")
    print(
        f"This model has {total_params / len(RAW_DATA):.0f}x more parameters than data points."
    )
    print(f"\nTraining a neural network to learn that 2+2=4...\n")

    start = time.time()

    for epoch in range(5000):
        optimizer.zero_grad()
        output = model(X)
        loss = criterion(output, y)
        loss.backward()
        optimizer.step()

        if epoch % 500 == 0 or epoch == 4999:
            # Test on a few examples
            model.eval()
            with torch.no_grad():
                preds = model(X) * 100.0  # denormalize
            model.train()

            # Check a few
            correct = 0
            for i, (n1, n2, op, expected) in enumerate(RAW_DATA):
                predicted = round(preds[i].item())
                if predicted == expected:
                    correct += 1

            print(
                f"  Epoch {epoch:5d}  |  Loss: {loss.item():.8f}  |  "
                f"Accuracy: {correct}/{len(RAW_DATA)} "
                f"({100 * correct / len(RAW_DATA):.1f}%)"
            )

    elapsed = time.time() - start
    print(f"\nTraining complete in {elapsed:.2f}s")
    print(f"PyTorch spent more time importing itself than training.\n")

    # Final evaluation
    model.eval()
    with torch.no_grad():
        preds = model(X) * 100.0

    print("=" * 60)
    print("FINAL PREDICTIONS ON TRAINING SET")
    print("=" * 60)
    op_names = ["+", "-", "*", "/"]
    failures = []
    for i, (n1, n2, op, expected) in enumerate(RAW_DATA):
        predicted_raw = preds[i].item()
        predicted = round(predicted_raw)
        status = "OK" if predicted == expected else "WRONG"
        if status == "WRONG":
            failures.append((n1, n2, op_names[op], expected, predicted, predicted_raw))
        print(
            f"  {n1:3d} {op_names[op]} {n2:3d} = {expected:4d}  |  "
            f"MLP says: {predicted_raw:8.2f} -> {predicted:4d}  [{status}]"
        )

    if failures:
        print(
            f"\n{len(failures)} FAILURES (the network couldn't even memorize 47 samples):"
        )
        for n1, n2, op, expected, predicted, raw in failures:
            print(
                f"  {n1} {op} {n2} = {expected}, but MLP insists on {raw:.4f} -> {predicted}"
            )
    else:
        print(f"\nAll 47 training samples memorized correctly.")
        print(f"The network will now confidently hallucinate on anything else.")

    return model


def export_weights(model, output_dir):
    """
    Export model weights as numpy arrays for TensorFlow to rebuild.
    This is the diplomatic handoff between two rival ML frameworks.
    """
    weights = {}
    for name, param in model.named_parameters():
        weights[name] = param.detach().cpu().numpy()
        print(f"  Exported {name}: shape={param.shape}")

    # Save as individual .npy files
    for name, w in weights.items():
        safe_name = name.replace(".", "_")
        np.save(f"{output_dir}/{safe_name}.npy", w)

    # Also save as a single JSON with lists (for the paper's sake)
    json_weights = {}
    for name, w in weights.items():
        json_weights[name] = w.tolist()
    with open(f"{output_dir}/weights.json", "w") as f:
        json.dump(json_weights, f)

    print(f"\n  Weights saved to {output_dir}/")
    print(f"  These numpy arrays will be smuggled into TensorFlow.")


def test_out_of_distribution(model):
    """Test on inputs the model has NEVER seen. This is where it gets fun."""
    print("\n" + "=" * 60)
    print("OUT-OF-DISTRIBUTION INFERENCE (inputs never seen during training)")
    print("=" * 60)

    test_cases = [
        (13, 29, 0, 42, "+"),  # addition it hasn't seen
        (7, 7, 2, 49, "*"),  # multiplication it hasn't seen
        (100, 100, 0, 200, "+"),  # outside the training range
        (1, 1, 3, 1, "/"),  # trivial division
        (99, 99, 1, 0, "-"),  # near the edge
        (3, 7, 2, 21, "*"),  # simple but unseen
    ]

    op_map = {"+": 0, "-": 1, "*": 2, "/": 3}

    model.eval()
    with torch.no_grad():
        for n1, n2, op_idx, expected, op_sym in test_cases:
            one_hot = [0.0, 0.0, 0.0, 0.0]
            one_hot[op_idx] = 1.0
            x = torch.tensor([[n1 / 100.0, n2 / 100.0] + one_hot], dtype=torch.float32)
            pred_raw = (model(x) * 100.0).item()
            pred = round(pred_raw)
            status = "OK" if pred == expected else "HALLUCINATION"
            print(
                f"  {n1:3d} {op_sym} {n2:3d} = {expected:4d}  |  "
                f"MLP says: {pred_raw:8.2f} -> {pred:4d}  [{status}]"
            )


if __name__ == "__main__":
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "."

    model = train()
    test_out_of_distribution(model)

    print("\n" + "=" * 60)
    print("EXPORTING WEIGHTS (PyTorch -> numpy -> TensorFlow handoff)")
    print("=" * 60)
    export_weights(model, output_dir)

    print("\nDone. The neural network has learned arithmetic from 47 examples.")
    print("It is now ready to vote in a Byzantine fault-tolerant consensus protocol.")
