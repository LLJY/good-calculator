--  CalBot Blockchain Verification — Ada Unit Test Suite
--
--  A DO-178C-grade test harness for a calculator blockchain.
--
--  This Ada program validates that the COBOL-specified chain
--  verification rules are correctly implemented.  The same
--  rules are implemented in the COBOL-generated Kotlin file
--  (BlockchainVerifier.kt) which runs on Android.
--
--  Test categories:
--    TC-100: Hash format validation
--    TC-200: Genesis anchor verification
--    TC-300: Chain linkage verification
--    TC-400: Temporal ordering verification
--    TC-500: Full chain verification (integrated)
--    TC-600: Edge cases and boundary conditions
--
--  Compile: gnatmake test_chain_verification.adb
--  Run:     ./test_chain_verification
--
--  If all tests pass, the blockchain verification logic is
--  certified for deployment in a calculator app.  Just like
--  the F-16 flight control system, but for 2+2.

with Ada.Text_IO;         use Ada.Text_IO;
with Chain_Verification;  use Chain_Verification;

procedure Test_Chain_Verification is

   Pass_Count : Natural := 0;
   Fail_Count : Natural := 0;
   Total      : Natural := 0;

   --  Test assertion helper with proper Ada style
   procedure Assert (Condition : Boolean;
                     Test_Id   : String;
                     Message   : String) is
   begin
      Total := Total + 1;
      if Condition then
         Pass_Count := Pass_Count + 1;
         Put_Line ("  [PASS] " & Test_Id & ": " & Message);
      else
         Fail_Count := Fail_Count + 1;
         Put_Line ("  [FAIL] " & Test_Id & ": " & Message);
      end if;
   end Assert;

   --  Helpers to build test blocks
   Valid_Hash_1 : constant Hash_String :=
     "e9f697a8dc33d926435c4cd9a7ca1f824fc53d93b63c39e552c40a101604c5fe";
   Valid_Hash_2 : constant Hash_String :=
     "832fa7c5d4dac799a321afb53e899e6112d158e9db7a9a4f1f6952fa850890fe";
   Valid_Hash_3 : constant Hash_String :=
     "1c69b8e3cb81b50b03e71291d6481d3fd6dd743f01b7fabaed0f9ae5f40c5bfe";
   Invalid_Hash : constant Hash_String :=
     "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ";
   Zero_Hash    : constant Hash_String :=
     "0000000000000000000000000000000000000000000000000000000000000000";

   function Make_Block
     (Bot     : CalBot_Id;
      Expr    : String;
      Res     : Integer;
      Prev    : Hash_String;
      BHash   : Hash_String;
      TS      : Timestamp;
      T       : Raft_Term := 1;
      L       : Node_Id   := 0) return Block_Record
   is
      B : Block_Record;
   begin
      B.Cal_Bot    := Bot;
      B.Expression := (others => ' ');
      B.Expr_Len   := Expr'Length;
      for I in Expr'Range loop
         B.Expression (I - Expr'First + 1) := Expr (I);
      end loop;
      B.Result     := Res;
      B.Prev_Hash  := Prev;
      B.Block_Hash := BHash;
      B.Nonce      := 42;
      B.Time_Stamp := TS;
      B.Term       := T;
      B.Leader     := L;
      return B;
   end Make_Block;

begin
   Put_Line ("==============================================");
   Put_Line ("  Ada/SPARK BLOCKCHAIN VERIFICATION TESTS");
   Put_Line ("  Compiler: GNAT 15.2.1 (GCC Ada frontend)");
   Put_Line ("  Standard: Ada 2022");
   Put_Line ("  Safety level: DO-178C Level A (aspirational)");
   Put_Line ("==============================================");
   New_Line;

   --  ========================================================
   --  TC-100: Hash Format Validation
   --  ========================================================
   Put_Line ("TC-100: Hash Format Validation");

   Assert (Is_Valid_Hex_Char ('0'), "TC-101",
           "Character '0' is valid hex");
   Assert (Is_Valid_Hex_Char ('9'), "TC-102",
           "Character '9' is valid hex");
   Assert (Is_Valid_Hex_Char ('a'), "TC-103",
           "Character 'a' is valid hex");
   Assert (Is_Valid_Hex_Char ('f'), "TC-104",
           "Character 'f' is valid hex");
   Assert (not Is_Valid_Hex_Char ('g'), "TC-105",
           "Character 'g' is not valid hex");
   Assert (not Is_Valid_Hex_Char ('Z'), "TC-106",
           "Character 'Z' is not valid hex");
   Assert (not Is_Valid_Hex_Char (' '), "TC-107",
           "Space is not valid hex");

   Assert (Is_Valid_Hash (Valid_Hash_1), "TC-110",
           "CalBot 1 genesis hash is valid");
   Assert (Is_Valid_Hash (Valid_Hash_2), "TC-111",
           "CalBot 2 genesis hash is valid");
   Assert (Is_Valid_Hash (Zero_Hash), "TC-112",
           "Zero hash is valid hex");
   Assert (not Is_Valid_Hash (Invalid_Hash), "TC-113",
           "All-Z hash is invalid");
   New_Line;

   --  ========================================================
   --  TC-200: Genesis Anchor Verification
   --  ========================================================
   Put_Line ("TC-200: Genesis Anchor Verification");

   declare
      --  A valid chain: block's prev_hash matches genesis
      Good_Chain : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 1000));
      --  An invalid chain: wrong genesis anchor
      Bad_Chain : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_2, Valid_Hash_3, 1000));
   begin
      Assert (Check_Genesis_Anchor (Good_Chain, Valid_Hash_1), "TC-201",
              "Correct genesis anchor accepted");
      Assert (not Check_Genesis_Anchor (Bad_Chain, Valid_Hash_1), "TC-202",
              "Wrong genesis anchor rejected");
      Assert (Check_Genesis_Anchor (Good_Chain, Valid_Hash_1), "TC-203",
              "Genesis check is deterministic");
   end;
   New_Line;

   --  ========================================================
   --  TC-300: Chain Linkage Verification
   --  ========================================================
   Put_Line ("TC-300: Chain Linkage Verification");

   declare
      --  Properly linked 3-block chain
      Good_Chain : constant Block_Chain (0 .. 2) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 1000),
         1 => Make_Block (1, "3*3", 9, Valid_Hash_2, Valid_Hash_3, 2000),
         2 => Make_Block (1, "5-1", 4, Valid_Hash_3, Zero_Hash, 3000));

      --  Broken link at position 1
      Broken_Chain : constant Block_Chain (0 .. 2) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 1000),
         1 => Make_Block (1, "3*3", 9, Zero_Hash, Valid_Hash_3, 2000),
         2 => Make_Block (1, "5-1", 4, Valid_Hash_3, Valid_Hash_1, 3000));

      --  Single block — trivially linked
      Single : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (1, "1+1", 2, Valid_Hash_1, Valid_Hash_2, 1000));
   begin
      Assert (Check_Chain_Linkage (Good_Chain), "TC-301",
              "Properly linked 3-block chain accepted");
      Assert (not Check_Chain_Linkage (Broken_Chain), "TC-302",
              "Broken link at position 1 detected");
      Assert (Check_Chain_Linkage (Single), "TC-303",
              "Single block chain is trivially valid");
   end;
   New_Line;

   --  ========================================================
   --  TC-400: Temporal Ordering Verification
   --  ========================================================
   Put_Line ("TC-400: Temporal Ordering Verification");

   declare
      --  Correctly ordered timestamps
      Ordered : constant Block_Chain (0 .. 2) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 1000),
         1 => Make_Block (1, "3*3", 9, Valid_Hash_2, Valid_Hash_3, 2000),
         2 => Make_Block (1, "5-1", 4, Valid_Hash_3, Zero_Hash, 3000));

      --  Disordered: timestamp goes backwards
      Disordered : constant Block_Chain (0 .. 2) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 3000),
         1 => Make_Block (1, "3*3", 9, Valid_Hash_2, Valid_Hash_3, 1000),
         2 => Make_Block (1, "5-1", 4, Valid_Hash_3, Zero_Hash, 2000));

      --  Equal timestamps — should pass (non-decreasing, not strictly increasing)
      Equal_TS : constant Block_Chain (0 .. 1) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 5000),
         1 => Make_Block (1, "3*3", 9, Valid_Hash_2, Valid_Hash_3, 5000));
   begin
      Assert (Check_Temporal_Order (Ordered), "TC-401",
              "Monotonically increasing timestamps accepted");
      Assert (not Check_Temporal_Order (Disordered), "TC-402",
              "Backwards timestamps rejected");
      Assert (Check_Temporal_Order (Equal_TS), "TC-403",
              "Equal timestamps accepted (non-decreasing)");
   end;
   New_Line;

   --  ========================================================
   --  TC-500: Full Chain Verification (Integrated)
   --  ========================================================
   Put_Line ("TC-500: Full Chain Verification (Integrated)");

   declare
      --  A perfect chain: genesis valid, linked, ordered, good hashes
      Perfect : constant Block_Chain (0 .. 1) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 1000),
         1 => Make_Block (1, "3+3", 6, Valid_Hash_2, Valid_Hash_3, 2000));
      R1 : constant Verification_Result :=
        Verify_Chain (Perfect, Valid_Hash_1);
   begin
      Assert (R1.Verdict = Verified, "TC-501",
              "Perfect chain yields VERIFIED verdict");
      Assert (R1.Genesis_Valid, "TC-502",
              "Perfect chain has valid genesis");
      Assert (R1.All_Links_Valid, "TC-503",
              "Perfect chain has valid linkage");
      Assert (R1.All_Hashes_Ok, "TC-504",
              "Perfect chain has valid hash format");
      Assert (R1.Temporal_Ok, "TC-505",
              "Perfect chain has valid temporal order");
      Assert (R1.Broken_Link_Count = 0, "TC-506",
              "Perfect chain has zero broken links");
      Assert (R1.Chain_Length = 2, "TC-507",
              "Chain length correctly reported as 2");
   end;

   declare
      --  Wrong genesis anchor
      Bad_Genesis : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_2, Valid_Hash_3, 1000));
      R2 : constant Verification_Result :=
        Verify_Chain (Bad_Genesis, Valid_Hash_1);
   begin
      Assert (R2.Verdict = No_Anchor, "TC-510",
              "Wrong genesis yields NO_ANCHOR verdict");
      Assert (not R2.Genesis_Valid, "TC-511",
              "Wrong genesis correctly flagged");
   end;

   declare
      --  Broken link in chain
      Broken_Chain : constant Block_Chain (0 .. 1) :=
        (0 => Make_Block (1, "2+2", 4, Valid_Hash_1, Valid_Hash_2, 1000),
         1 => Make_Block (1, "3+3", 6, Zero_Hash, Valid_Hash_3, 2000));
      R3 : constant Verification_Result :=
        Verify_Chain (Broken_Chain, Valid_Hash_1);
   begin
      Assert (R3.Verdict = Broken, "TC-520",
              "Broken link yields BROKEN verdict");
      Assert (not R3.All_Links_Valid, "TC-521",
              "Broken link correctly detected");
      Assert (R3.Broken_Link_Count = 1, "TC-522",
              "Exactly one broken link counted");
   end;
   New_Line;

   --  ========================================================
   --  TC-600: Edge Cases and Boundary Conditions
   --  ========================================================
   Put_Line ("TC-600: Edge Cases and Boundary Conditions");

   declare
      --  Empty chain
      Empty_Chain : Block_Chain (1 .. 0);
      --  Note: Ada allows this; 1..0 is an empty range.
      R4 : constant Verification_Result :=
        Verify_Chain (Empty_Chain, Valid_Hash_1);
   begin
      Assert (R4.Verdict = Empty, "TC-601",
              "Empty chain yields EMPTY verdict");
      Assert (R4.Chain_Length = 0, "TC-602",
              "Empty chain length is 0");
   end;

   declare
      --  Single block chain — genesis only
      Single : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (1, "42/6", 7, Valid_Hash_1, Valid_Hash_2, 1000));
      R5 : constant Verification_Result :=
        Verify_Chain (Single, Valid_Hash_1);
   begin
      Assert (R5.Verdict = Verified, "TC-610",
              "Single block with valid genesis is VERIFIED");
      Assert (R5.Chain_Length = 1, "TC-611",
              "Single block chain length is 1");
   end;

   declare
      --  CalBot ID boundary: CalBot 30 (maximum)
      Max_Bot : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (30, "99+1", 100, Valid_Hash_1, Valid_Hash_2, 1000));
      R6 : constant Verification_Result :=
        Verify_Chain (Max_Bot, Valid_Hash_1);
   begin
      Assert (R6.Verdict = Verified, "TC-620",
              "CalBot 30 (boundary) is valid");
   end;

   declare
      --  CalBot ID boundary: CalBot 1 (minimum)
      Min_Bot : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (1, "0+0", 0, Valid_Hash_1, Valid_Hash_2, 1000));
      R7 : constant Verification_Result :=
        Verify_Chain (Min_Bot, Valid_Hash_1);
   begin
      Assert (R7.Verdict = Verified, "TC-621",
              "CalBot 1 (boundary) is valid");
   end;

   declare
      --  Negative result in block (subtraction)
      Neg_Result : constant Block_Chain (0 .. 0) :=
        (0 => Make_Block (5, "3-7", -4, Valid_Hash_1, Valid_Hash_2, 1000));
      R8 : constant Verification_Result :=
        Verify_Chain (Neg_Result, Valid_Hash_1);
   begin
      Assert (R8.Verdict = Verified, "TC-630",
              "Negative arithmetic result is valid");
   end;
   New_Line;

   --  ========================================================
   --  Summary
   --  ========================================================
   Put_Line ("==============================================");
   Put_Line ("  TEST SUMMARY");
   Put_Line ("==============================================");
   Put_Line ("  Total:  " & Natural'Image (Total));
   Put_Line ("  Passed: " & Natural'Image (Pass_Count));
   Put_Line ("  Failed: " & Natural'Image (Fail_Count));
   Put_Line ("==============================================");

   if Fail_Count = 0 then
      Put_Line ("  ALL TESTS PASSED");
      Put_Line ("  The blockchain verification logic has been");
      Put_Line ("  certified by Ada for deployment in a");
      Put_Line ("  calculator app.  Safety level: DO-178C");
      Put_Line ("  Level A (self-assessed, non-binding).");
   else
      Put_Line ("  TESTS FAILED — DO NOT DEPLOY");
      Put_Line ("  The calculator blockchain is not airworthy.");
   end if;

   Put_Line ("==============================================");
   New_Line;

   --  Exit with non-zero status on failure (for CI/build integration)
   if Fail_Count > 0 then
      raise Program_Error with
        "Ada verification failed: " & Natural'Image (Fail_Count) &
        " test(s) did not pass. The calculator is not safe to fly.";
   end if;

end Test_Chain_Verification;
