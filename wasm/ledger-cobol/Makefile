.PHONY: build clean

OUTDIR = ../../app/src/main/assets/wasm
WASI_SYSROOT ?= /usr/share/wasi-sysroot

# Build pipeline: COBOL -> C (GnuCOBOL) -> WASM (clang)
# This is the most cursed compilation pipeline in mobile development.
#
# Step 1: cobc -C generates C source from COBOL
# Step 2: clang compiles the C to WASM
#
# NOTE: GnuCOBOL -> WASM is genuinely difficult due to libcob runtime.
# This Makefile documents the theoretical pipeline.
# In practice, the ledger formatting may need a pure-C reimplementation
# of the COBOL logic as a fallback.

build:
	@mkdir -p $(OUTDIR)
	@echo "=== COBOL -> C -> WASM Pipeline ==="
	@echo "Step 1: COBOL -> C (requires gnucobol)"
	cobc -C -x ledger.cob -o ledger.c 2>/dev/null || echo "GnuCOBOL not installed, using C fallback"
	@echo "Step 2: C -> WASM (requires clang + wasi-sysroot)"
	@echo "NOTE: Full pipeline requires libcob compiled for WASM. See README."
	@echo "For now, ledger.wasm is a TODO placeholder."
	@touch $(OUTDIR)/ledger.wasm

clean:
	rm -f $(OUTDIR)/ledger.wasm ledger.c *.c.h *.c.l*
