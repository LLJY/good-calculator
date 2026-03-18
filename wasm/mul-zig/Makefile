.PHONY: build clean

OUTDIR = ../../app/src/main/assets/wasm

build:
	@mkdir -p $(OUTDIR)
	zig build-lib \
		-target wasm32-wasi \
		-O ReleaseFast \
		--name mul \
		mul.zig
	mv libmul.wasm $(OUTDIR)/mul.wasm 2>/dev/null || mv mul.wasm $(OUTDIR)/mul.wasm
	@echo "Built mul.wasm (Zig -> WASM, the ouroboros)"
	@ls -lh $(OUTDIR)/mul.wasm

clean:
	rm -f $(OUTDIR)/mul.wasm *.o *.wasm
