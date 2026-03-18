.PHONY: build clean

OUTDIR = ../../app/src/main/assets/wasm
# Requires wasi-sysroot. Set WASI_SYSROOT env or use default path.
WASI_SYSROOT ?= /usr/share/wasi-sysroot

build:
	@mkdir -p $(OUTDIR)
	clang \
		--target=wasm32-wasi \
		--sysroot=$(WASI_SYSROOT) \
		-O2 \
		-nostdlib \
		-Wl,--no-entry \
		-Wl,--export=sub \
		-Wl,--strip-all \
		-o $(OUTDIR)/sub.wasm \
		sub.c
	@echo "Built sub.wasm (C via clang)"
	@ls -lh $(OUTDIR)/sub.wasm

clean:
	rm -f $(OUTDIR)/sub.wasm
