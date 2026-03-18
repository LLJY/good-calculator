/**
 * Test driver for the COBOL ledger formatter.
 *
 * Compiles and links with ledger.o (produced by gcobol) and libgcobol
 * to actually call COBOL from C. On Android this would go through
 * Zig -> WASM or Zig -> JNI, but for testing we call it natively.
 *
 * Build:
 *   gcobol -c ledger.cob
 *   gcc test_ledger.c ledger.o -lgcobol -o test_ledger
 *   ./test_ledger
 */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/* COBOL data items are fixed-length, space-padded, not null-terminated.
   We define structs matching the LINKAGE SECTION PIC clauses. */

/* Helper: pad a C string into a fixed-width COBOL field */
static void cobol_move(char *dest, size_t width, const char *src) {
    memset(dest, ' ', width);
    size_t len = strlen(src);
    if (len > width) len = width;
    memcpy(dest, src, len);
}

/* The COBOL entry point. gcobol mangles names as lowercase program-id. */
extern void calbot_ledger_(
    char *calbot_id,      /* PIC 9(4)              */
    char *expression,     /* PIC X(50)             */
    char *result,         /* PIC S9(10) SIGN LEADING */
    char *block_hash,     /* PIC X(64)             */
    char *timestamp,      /* PIC 9(18)             */
    char *raft_term,      /* PIC 9(4)              */
    char *leader_node,    /* PIC 9(1)              */
    char *output_line     /* PIC X(256)            */
);

/* gcobol runtime init/teardown */
extern void _gcobol_runtime_init(int, char**);
extern void _gcobol_runtime_teardown(void);

int main(int argc, char **argv) {
    /* Initialize the GCC COBOL runtime */
    _gcobol_runtime_init(argc, argv);

    /* Prepare fixed-width COBOL fields */
    char calbot_id[4];
    char expression[50];
    char result[11];       /* S9(10) SIGN LEADING = 11 chars */
    char block_hash[64];
    char timestamp[18];
    char raft_term[4];
    char leader_node[1];
    char output_line[256];

    cobol_move(calbot_id, 4, "0007");
    cobol_move(expression, 50, "3+5");
    cobol_move(result, 11, "+0000000008");
    cobol_move(block_hash, 64, "ab12cd34ef56789000000000000000000000000000000000000000000000dead");
    cobol_move(timestamp, 18, "001742299200000000");
    cobol_move(raft_term, 4, "0001");
    cobol_move(leader_node, 1, "2");
    memset(output_line, ' ', 256);

    printf("Calling COBOL ledger formatter...\n");

    calbot_ledger_(
        calbot_id,
        expression,
        result,
        block_hash,
        timestamp,
        raft_term,
        leader_node,
        output_line
    );

    /* Print the result (trim trailing spaces) */
    int len = 256;
    while (len > 0 && output_line[len-1] == ' ') len--;
    printf("COBOL output: %.*s\n", len, output_line);

    _gcobol_runtime_teardown();
    return 0;
}
