--  CalBot Blockchain Chain Verification — Ada/SPARK Body
--
--  Implementation of the verification rules specified in the .ads file.
--  Each function corresponds to a COBOL PERFORM paragraph in the
--  chain_verifier_generator.cob specification.
--
--  This is safety-critical code for a calculator app.

package body Chain_Verification is

   ---------------------------------------------------------------
   --  Is_Valid_Hex_Char: check a single character
   ---------------------------------------------------------------
   function Is_Valid_Hex_Char (C : Character) return Boolean is
   begin
      return C in '0' .. '9' | 'a' .. 'f';
   end Is_Valid_Hex_Char;

   ---------------------------------------------------------------
   --  Is_Valid_Hash: all 64 chars must be hex [0-9a-f]
   --  Corresponds to COBOL rule 230-CHECK-HASH-FORMAT
   ---------------------------------------------------------------
   function Is_Valid_Hash (H : Hash_String) return Boolean is
   begin
      for I in H'Range loop
         if not Is_Valid_Hex_Char (H (I)) then
            return False;
         end if;
      end loop;
      return True;
   end Is_Valid_Hash;

   ---------------------------------------------------------------
   --  Check_Genesis_Anchor
   --  Corresponds to COBOL paragraph 210-CHECK-GENESIS-ANCHOR
   --  Precondition: Chain'Length > 0 (enforced by Ada contract)
   ---------------------------------------------------------------
   function Check_Genesis_Anchor
     (Chain        : Block_Chain;
      Genesis_Hash : Hash_String) return Boolean
   is
   begin
      return Chain (Chain'First).Prev_Hash = Genesis_Hash;
   end Check_Genesis_Anchor;

   ---------------------------------------------------------------
   --  Check_Chain_Linkage
   --  Corresponds to COBOL paragraph 220-CHECK-CHAIN-LINKAGE
   --  Precondition: Chain'Length > 0 (enforced by Ada contract)
   ---------------------------------------------------------------
   function Check_Chain_Linkage (Chain : Block_Chain) return Boolean is
   begin
      if Chain'Length <= 1 then
         return True;  --  Single block trivially linked
      end if;

      for I in Chain'First + 1 .. Chain'Last loop
         if Chain (I).Prev_Hash /= Chain (I - 1).Block_Hash then
            return False;
         end if;
      end loop;
      return True;
   end Check_Chain_Linkage;

   ---------------------------------------------------------------
   --  Check_Temporal_Order
   --  Corresponds to COBOL paragraph 240-CHECK-TEMPORAL-ORDER
   --  Timestamps must be monotonically non-decreasing.
   ---------------------------------------------------------------
   function Check_Temporal_Order (Chain : Block_Chain) return Boolean is
   begin
      if Chain'Length <= 1 then
         return True;
      end if;

      for I in Chain'First + 1 .. Chain'Last loop
         if Chain (I).Time_Stamp < Chain (I - 1).Time_Stamp then
            return False;
         end if;
      end loop;
      return True;
   end Check_Temporal_Order;

   ---------------------------------------------------------------
   --  Check_All_Hashes: every block_hash and prev_hash must be
   --  valid 64-char hex strings.
   ---------------------------------------------------------------
   function Check_All_Hashes (Chain : Block_Chain) return Boolean is
   begin
      for I in Chain'Range loop
         if not Is_Valid_Hash (Chain (I).Block_Hash) then
            return False;
         end if;
         if not Is_Valid_Hash (Chain (I).Prev_Hash) then
            return False;
         end if;
      end loop;
      return True;
   end Check_All_Hashes;

   ---------------------------------------------------------------
   --  Verify_Chain: full verification implementing all COBOL rules
   --
   --  Postcondition (from .ads):
   --    Empty chain  => verdict = Empty
   --    Non-empty    => chain_length matches actual length
   ---------------------------------------------------------------
   function Verify_Chain
     (Chain        : Block_Chain;
      Genesis_Hash : Hash_String) return Verification_Result
   is
      V : Verification_Result;
   begin
      if Chain'Length = 0 then
         V := (Verdict          => Empty,
               Chain_Length      => 0,
               Genesis_Valid     => True,
               All_Links_Valid   => True,
               All_Hashes_Ok     => True,
               Temporal_Ok       => True,
               Broken_Link_Count => 0);
         return V;
      end if;

      V.Chain_Length := Block_Index (Chain'Length);
      V.Genesis_Valid := Check_Genesis_Anchor (Chain, Genesis_Hash);
      V.All_Links_Valid := Check_Chain_Linkage (Chain);
      V.All_Hashes_Ok := Check_All_Hashes (Chain);
      V.Temporal_Ok := Check_Temporal_Order (Chain);

      --  Count broken links
      V.Broken_Link_Count := 0;
      if Chain'Length > 1 then
         for I in Chain'First + 1 .. Chain'Last loop
            if Chain (I).Prev_Hash /= Chain (I - 1).Block_Hash then
               V.Broken_Link_Count := V.Broken_Link_Count + 1;
            end if;
         end loop;
      end if;

      --  Determine verdict
      if V.Genesis_Valid and V.All_Links_Valid
         and V.All_Hashes_Ok and V.Temporal_Ok
      then
         V.Verdict := Verified;
      elsif not V.All_Links_Valid then
         V.Verdict := Broken;
      elsif not V.Genesis_Valid then
         V.Verdict := No_Anchor;
      else
         V.Verdict := Suspect;
      end if;

      return V;
   end Verify_Chain;

end Chain_Verification;
