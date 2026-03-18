.PHONY: build clean test

OUTDIR = ../../app/src/main/assets/wasm

build:
	@mkdir -p $(OUTDIR)
	cargo build --target wasm32-wasip1 --release
	cp target/wasm32-wasip1/release/calbot_div.cdylib.wasm $(OUTDIR)/div.wasm 2>/dev/null || \
	cp target/wasm32-wasip1/release/calbot_div.wasm $(OUTDIR)/div.wasm 2>/dev/null || \
	echo "Warning: could not find output wasm, check target/wasm32-wasip1/release/"
	@echo "Built div.wasm (Rust, the revenge arc)"
	@ls -lh $(OUTDIR)/div.wasm 2>/dev/null || true

test:
	cargo test

clean:
	cargo clean
	rm -f $(OUTDIR)/div.wasm
