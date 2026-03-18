Namespace Global.CalBot.VBNet

    ''' <summary>
    ''' VB.NET Arithmetic Verifier for CalBot.
    '''
    ''' Visual Basic .NET: the language that enterprise developers
    ''' used to build CRUD apps in 2003 is now verifying blockchain
    ''' arithmetic on an Android phone in 2026.
    '''
    ''' VB.NET was chosen because:
    ''' 1. It adds to the language count
    ''' 2. The .NET runtime was already being shipped for F# and C#
    ''' 3. It would be rude to leave VB out of a .NET deployment
    ''' 4. BASIC (line 20 GOTO 10) needed a modern descendant in the project
    '''
    ''' This is the most verbose arithmetic verifier in the codebase,
    ''' which is appropriate for a language that uses "End If" instead of "}".
    ''' </summary>
    Public Class ArithmeticVerifier

        Public Class VerificationResult
            Public Property Operand1 As Integer
            Public Property Operand2 As Integer
            Public Property [Operator] As String
            Public Property ExpectedResult As Integer
            Public Property ActualResult As Integer
            Public Property Verified As Boolean
            Public Property Language As String = "VB.NET 16 (.NET 10)"

            Public Overrides Function ToString() As String
                Dim verdict As String = If(Verified, "PASS", "FAIL")
                Return $"VB.NET: {Operand1} {[Operator]} {Operand2} = {ActualResult} (expected {ExpectedResult}) [{verdict}]"
            End Function
        End Class

        ''' <summary>
        ''' Verify an arithmetic operation using VB.NET's arithmetic operators.
        ''' Uses Select Case because VB.NET has standards.
        ''' </summary>
        Public Shared Function Verify(a As Integer, b As Integer, op As String) As VerificationResult
            Dim result As Integer

            Select Case op
                Case "+"
                    result = a + b
                Case "-"
                    result = a - b
                Case "*"
                    result = a * b
                Case "/"
                    If b = 0 Then
                        Throw New DivideByZeroException(
                            "VB.NET refuses to divide by zero. " &
                            "This is not a drill. " &
                            "Contact your nearest COBOL programmer.")
                    End If
                    result = a \ b  ' Integer division in VB.NET uses \
                Case Else
                    Throw New ArgumentException(
                        $"Unsupported operator: {op}. " &
                        "VB.NET only does +, -, *, /. " &
                        "For anything else, use F#.")
            End Select

            Dim verification As New VerificationResult()
            verification.Operand1 = a
            verification.Operand2 = b
            verification.Operator = op
            verification.ExpectedResult = result
            verification.ActualResult = result
            verification.Verified = True
            Return verification
        End Function

        ''' <summary>
        ''' Cross-check an expected result against VB.NET's computation.
        ''' This is the entry point called from the Android JNI bridge,
        ''' which calls C, which calls .NET, which calls VB.NET,
        ''' to verify arithmetic that Rust already computed correctly.
        ''' </summary>
        Public Shared Function CrossCheck(a As Integer, b As Integer,
                                           op As String,
                                           expected As Integer) As VerificationResult
            Dim result = Verify(a, b, op)
            result.ExpectedResult = expected
            result.Verified = (result.ActualResult = expected)
            Return result
        End Function

        ''' <summary>
        ''' Fibonacci in VB.NET because every language in this project
        ''' implements Fibonacci. It's basically a rite of passage.
        ''' </summary>
        Public Shared Function Fibonacci(n As Integer) As Long
            If n <= 0 Then Return 0
            If n = 1 Then Return 1

            Dim a As Long = 0
            Dim b As Long = 1
            For i As Integer = 2 To n
                Dim temp As Long = a + b
                a = b
                b = temp
            Next
            Return b
        End Function

    End Class

End Namespace
