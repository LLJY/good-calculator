>>SOURCE FORMAT IS FREE

*> CalBot Ledger Formatter
*>
*> This is GnuCOBOL source that formats blockchain ledger entries for a
*> calculator app running on Android. Yes, that sentence is real.
*>
*> The theoretical pipeline is:
*>   COBOL source -> GnuCOBOL generated C -> clang wasm32-wasi -> wasm3 -> JNI
*>
*> In practice, the painful part is not COBOL syntax. The painful part is
*> dragging the libcob runtime through a WASM toolchain and then pretending
*> this was a reasonable architectural decision.

identification division.
program-id. calbot-ledger.
author. OpenCode.
remarks.
    A business language from 1959 is formatting calculator output on a
    smartphone because enterprise software never truly dies.

data division.
working-storage section.
01 ws-calbot-id              pic 9(4).
01 ws-expression             pic x(50).
01 ws-result                 pic s9(10) sign leading.
01 ws-block-hash             pic x(64).
01 ws-timestamp              pic 9(18).
01 ws-raft-term              pic 9(4).
01 ws-leader-node            pic 9(1).

01 ws-calbot-id-edit         pic 9(4).
01 ws-expression-edit        pic x(50).
01 ws-result-edit            pic -9(10).
01 ws-raft-term-edit         pic 9(4).
01 ws-leader-node-edit       pic 9(1).
01 ws-hash-preview           pic x(8).
01 ws-formatted              pic x(256).

linkage section.
01 lk-calbot-id              pic 9(4).
01 lk-expression             pic x(50).
01 lk-result                 pic s9(10) sign leading.
01 lk-block-hash             pic x(64).
01 lk-timestamp              pic 9(18).
01 lk-raft-term              pic 9(4).
01 lk-leader-node            pic 9(1).
01 lk-output-line            pic x(256).

procedure division using
    lk-calbot-id
    lk-expression
    lk-result
    lk-block-hash
    lk-timestamp
    lk-raft-term
    lk-leader-node
    lk-output-line.

    *> GnuCOBOL supports alternate entry points via ENTRY. That gives the
    *> generated C a stable symbol a host can resolve with cob_resolve(),
    *> which is as close to a C-compatible function boundary as this relic
    *> of business computing is going to get without a handwritten shim.
    entry "format_ledger" using
        lk-calbot-id
        lk-expression
        lk-result
        lk-block-hash
        lk-timestamp
        lk-raft-term
        lk-leader-node
        lk-output-line

    perform 100-format-ledger-line
    goback.

100-format-ledger-line.
    *> Copy incoming linkage values into working storage so we can apply
    *> COBOL picture editing like proper office equipment.
    move lk-calbot-id to ws-calbot-id
    move lk-expression to ws-expression
    move lk-result to ws-result
    move lk-block-hash to ws-block-hash
    move lk-timestamp to ws-timestamp
    move lk-raft-term to ws-raft-term
    move lk-leader-node to ws-leader-node

    move ws-calbot-id to ws-calbot-id-edit
    move function trim(ws-expression trailing) to ws-expression-edit
    move ws-result to ws-result-edit
    move ws-raft-term to ws-raft-term-edit
    move ws-leader-node to ws-leader-node-edit
    move ws-block-hash(1:8) to ws-hash-preview

    move spaces to ws-formatted
    string
        "CALBOT-" delimited by size
        ws-calbot-id-edit delimited by size
        " | " delimited by size
        function trim(ws-expression-edit trailing) delimited by size
        " = " delimited by size
        ws-result-edit delimited by size
        " | TERM:" delimited by size
        ws-raft-term-edit delimited by size
        " | NODE:" delimited by size
        ws-leader-node-edit delimited by size
        " | HASH:" delimited by size
        ws-hash-preview delimited by size
        "..." delimited by size
        into ws-formatted
    end-string

    *> Return the formatted line to the caller and also DISPLAY it, because
    *> COBOL has never met a report it did not want to print immediately.
    move ws-formatted to lk-output-line
    display ws-formatted.

end program calbot-ledger.
