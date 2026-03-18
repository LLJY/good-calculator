// CalBot Addition Microservice
// Compiled to WebAssembly via TinyGo.
// This is the most over-engineered way to add two numbers.
package main

//export add
func add(a, b int32) int32 {
	return a + b
}

// main is required by TinyGo but never called from WASM
func main() {}
