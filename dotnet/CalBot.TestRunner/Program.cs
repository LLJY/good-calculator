// CalBot .NET Arithmetic Verification Test Runner
//
// Validates that C#, F#, and VB.NET all agree on basic arithmetic.
// Runs at build time.  If this fails, the calculator is grounded.
//
// Three .NET languages verify arithmetic that has already been verified by:
//   Rust, Kotlin, Raft consensus, FreeRTOS TMR, a neural network trained
//   on 47 samples, Fortran eigenvalues, COBOL blockchain verification
//   (tested by Ada/SPARK), and post-quantum cryptographic attestation.

using CalBot.CSharp;

Console.WriteLine("==============================================");
Console.WriteLine("  .NET ARITHMETIC VERIFICATION TEST RUNNER");
Console.WriteLine("  Languages: C# 13, F# 9, VB.NET 16");
Console.WriteLine("  Runtime: .NET 10");
Console.WriteLine("==============================================");
Console.WriteLine();

int pass = 0, fail = 0;

void Assert(bool condition, string testId, string message)
{
    if (condition) { pass++; Console.WriteLine($"  [PASS] {testId}: {message}"); }
    else { fail++; Console.WriteLine($"  [FAIL] {testId}: {message}"); }
}

// ============================================
// C# Verification Tests
// ============================================
Console.WriteLine("C# Verification Tests:");
Assert(ArithmeticVerifier.CrossCheck(2, 2, "+", 4).Verified, "CS-01", "C#: 2+2=4");
Assert(ArithmeticVerifier.CrossCheck(7, 3, "-", 4).Verified, "CS-02", "C#: 7-3=4");
Assert(ArithmeticVerifier.CrossCheck(6, 7, "*", 42).Verified, "CS-03", "C#: 6*7=42");
Assert(ArithmeticVerifier.CrossCheck(10, 2, "/", 5).Verified, "CS-04", "C#: 10/2=5");
Assert(!ArithmeticVerifier.CrossCheck(2, 2, "+", 5).Verified, "CS-05", "C#: 2+2!=5 rejected");
Console.WriteLine();

// ============================================
// F# Verification Tests
// ============================================
Console.WriteLine("F# Verification Tests:");
Assert(CalBot.FSharp.ArithmeticVerifier.crossCheck(2, 2, "+", 4).Verified, "FS-01", "F#: 2+2=4");
Assert(CalBot.FSharp.ArithmeticVerifier.crossCheck(7, 3, "-", 4).Verified, "FS-02", "F#: 7-3=4");
Assert(CalBot.FSharp.ArithmeticVerifier.crossCheck(6, 7, "*", 42).Verified, "FS-03", "F#: 6*7=42");
Assert(CalBot.FSharp.ArithmeticVerifier.crossCheck(10, 2, "/", 5).Verified, "FS-04", "F#: 10/2=5");
Assert(CalBot.FSharp.ArithmeticVerifier.fibonacci(10) == 55, "FS-05", "F#: fib(10)=55");
Assert(CalBot.FSharp.ArithmeticVerifier.fibonacci(44) == 701408733, "FS-06", "F#: fib(44)=701408733");
Console.WriteLine();

// ============================================
// VB.NET Verification Tests
// ============================================
Console.WriteLine("VB.NET Verification Tests:");
Assert(CalBot.VBNet.ArithmeticVerifier.CrossCheck(2, 2, "+", 4).Verified, "VB-01", "VB.NET: 2+2=4");
Assert(CalBot.VBNet.ArithmeticVerifier.CrossCheck(7, 3, "-", 4).Verified, "VB-02", "VB.NET: 7-3=4");
Assert(CalBot.VBNet.ArithmeticVerifier.CrossCheck(6, 7, "*", 42).Verified, "VB-03", "VB.NET: 6*7=42");
Assert(CalBot.VBNet.ArithmeticVerifier.CrossCheck(10, 2, "/", 5).Verified, "VB-04", "VB.NET: 10/2=5");
Assert(CalBot.VBNet.ArithmeticVerifier.Fibonacci(10) == 55, "VB-05", "VB.NET: fib(10)=55");
Console.WriteLine();

// ============================================
// Cross-Language Agreement
// ============================================
Console.WriteLine("Cross-Language Agreement Tests:");
(int a, int b, string op, int expected)[] cases =
[
    (2, 2, "+", 4), (13, 29, "+", 42), (100, 0, "+", 100),
    (10, 3, "-", 7), (0, 5, "-", -5),
    (7, 8, "*", 56), (12, 12, "*", 144),
    (100, 10, "/", 10), (42, 6, "/", 7)
];

foreach (var (a, b, op, expected) in cases)
{
    var cs = ArithmeticVerifier.CrossCheck(a, b, op, expected);
    var fs = CalBot.FSharp.ArithmeticVerifier.crossCheck(a, b, op, expected);
    var vb = CalBot.VBNet.ArithmeticVerifier.CrossCheck(a, b, op, expected);
    bool allAgree = cs.Verified && fs.Verified && vb.Verified;
    Assert(allAgree, $"XL-{a}{op}{b}", $"C#={cs.ActualResult} F#={fs.Actual} VB={vb.ActualResult}");
}
Console.WriteLine();

// ============================================
// Summary
// ============================================
Console.WriteLine("==============================================");
Console.WriteLine($"  Total: {pass + fail}  Passed: {pass}  Failed: {fail}");
Console.WriteLine("==============================================");
if (fail == 0)
{
    Console.WriteLine("  ALL TESTS PASSED");
    Console.WriteLine("  C#, F#, and VB.NET unanimously agree on");
    Console.WriteLine("  arithmetic.  The .NET runtime is cleared");
    Console.WriteLine("  for shipment inside the calculator APK.");
}
else
{
    Console.WriteLine("  TESTS FAILED — do not ship.");
}
Console.WriteLine("==============================================");
if (fail > 0) Environment.Exit(1);
