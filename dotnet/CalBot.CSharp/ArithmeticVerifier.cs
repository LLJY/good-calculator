namespace CalBot.CSharp;

/// <summary>
/// C# Arithmetic Verifier for CalBot.
///
/// This class exists because the calculator app needed another
/// verification path, and the .NET runtime was already being
/// shipped for F# and VB.NET, so we might as well use all three
/// CLR languages to verify that 2+2=4.
///
/// Enterprise design patterns applied:
/// - Strategy pattern (the operation)
/// - Factory method (CreateVerifier)
/// - Result object (VerificationResult)
/// - Async/await (because it's C#)
/// - LINQ (because it's C#)
/// - Nullable reference types (because it's 2026)
/// </summary>
public static class ArithmeticVerifier
{
    public record VerificationResult(
        int Operand1,
        int Operand2,
        string Operator,
        int ExpectedResult,
        int ActualResult,
        bool Verified,
        string Language = "C# 13 (.NET 10)"
    );

    /// <summary>
    /// Verify an arithmetic operation using C#'s checked arithmetic.
    /// The 'checked' context ensures overflow throws instead of wrapping,
    /// because we take integer safety seriously in this calculator app.
    /// </summary>
    public static VerificationResult Verify(int a, int b, string op)
    {
        int result = checked(op switch
        {
            "+" => a + b,
            "-" => a - b,
            "*" => a * b,
            "/" when b != 0 => a / b,
            "/" => throw new DivideByZeroException(
                "C# refuses to divide by zero for your calculator."),
            _ => throw new ArgumentException(
                $"Unsupported operator: {op}")
        });

        return new VerificationResult(
            Operand1: a,
            Operand2: b,
            Operator: op,
            ExpectedResult: result,
            ActualResult: result,
            Verified: true
        );
    }

    /// <summary>
    /// Cross-check: given an expected result, verify it matches C#'s computation.
    /// This is the entry point called from the Android JNI bridge.
    /// </summary>
    public static VerificationResult CrossCheck(int a, int b, string op, int expected)
    {
        var result = Verify(a, b, op);
        return result with
        {
            ExpectedResult = expected,
            Verified = result.ActualResult == expected
        };
    }

    /// <summary>
    /// Bulk verification using LINQ because this is C# and we have standards.
    /// </summary>
    public static IEnumerable<VerificationResult> VerifyBatch(
        IEnumerable<(int a, int b, string op, int expected)> cases)
        => cases.Select(c => CrossCheck(c.a, c.b, c.op, c.expected));
}
