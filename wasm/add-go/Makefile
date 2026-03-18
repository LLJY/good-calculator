.PHONY: build clean

OUTDIR = ../../app/src/main/assets/wasm

build:
	@mkdir -p $(OUTDIR)
	tinygo build -o $(OUTDIR)/add.wasm -target wasi -no-debug -opt=2 .
	@echo "Built add.wasm (Go via TinyGo)"
	@ls -lh $(OUTDIR)/add.wasm

clean:
	rm -f $(OUTDIR)/add.wasm
