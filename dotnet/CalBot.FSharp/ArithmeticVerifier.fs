namespace CalBot.FSharp

/// F# Arithmetic Verifier for CalBot.
///
/// Functional programming applied to the critical task of verifying
/// that a calculator can add two numbers.  Uses discriminated unions,
/// pattern matching, and pipelines — because if you're going to
/// verify 2+2=4, you should do it with algebraic data types.
///
/// F# brings ML-family type theory to the .NET runtime which is
/// being shipped inside an Android APK alongside Rust, Zig, COBOL,
/// Ada, Fortran, and a neural network.  This is fine.
module ArithmeticVerifier =

    /// Discriminated union for arithmetic operations.
    /// Pattern matching ensures exhaustiveness at compile time.
    /// The F# compiler will reject code that forgets a case.
    type Operation =
        | Add
        | Subtract
        | Multiply
        | Divide

    /// Parse operator string to discriminated union.
    /// Returns None for unsupported operators because F# uses
    /// Option types instead of throwing exceptions like barbarians.
    let parseOp (s: string) : Operation option =
        match s with
        | "+" -> Some Add
        | "-" -> Some Subtract
        | "*" -> Some Multiply
        | "/" -> Some Divide
        | _   -> None

    /// Pure function: compute the result of an operation.
    /// No side effects.  No mutation.  No exceptions (except div/0).
    /// Just math, the way Haskell intended but on the CLR.
    let compute (a: int) (b: int) (op: Operation) : int =
        match op with
        | Add      -> a + b
        | Subtract -> a - b
        | Multiply -> a * b
        | Divide   ->
            if b = 0 then failwith "Division by zero (even F# can't abstract this away)"
            else a / b

    /// Verification result as an immutable record.
    type VerificationResult = {
        Operand1: int
        Operand2: int
        Operator: string
        Expected: int
        Actual: int
        Verified: bool
        Language: string
    }

    /// Cross-check an expected result against F#'s computation.
    /// Uses the pipeline operator because this is F# and |> is life.
    let crossCheck (a: int) (b: int) (opStr: string) (expected: int) : VerificationResult =
        let result =
            opStr
            |> parseOp
            |> Option.map (compute a b)
            |> Option.defaultValue System.Int32.MinValue

        { Operand1 = a
          Operand2 = b
          Operator = opStr
          Expected = expected
          Actual = result
          Verified = (result = expected)
          Language = "F# 9 (.NET 10)" }

    /// Fibonacci via pattern matching and recursion.
    /// Because every F# module needs a recursive fibonacci.
    /// This one is tail-recursive because we're not animals.
    let fibonacci (n: int) : int =
        let rec loop a b count =
            if count <= 0 then a
            else loop b (a + b) (count - 1)
        loop 0 1 n

    /// Batch verification using List.map — functional style.
    let verifyBatch (cases: (int * int * string * int) list) =
        cases
        |> List.map (fun (a, b, op, expected) -> crossCheck a b op expected)
        |> List.filter (fun r -> not r.Verified)
