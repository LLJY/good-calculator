const std = @import("std");

const c = @cImport({
    @cInclude("openssl/evp.h");
    @cInclude("openssl/sha.h");
});

pub const Block = struct {
    cal_bot_id: i32,
    expression: []const u8,
    result: i32,
    prev_hash_hex: [64]u8,
    nonce: u64,
    timestamp_ms: i64,
    raft_term: i32,
    leader_node: i32,
    votes_json: []const u8,
    oracle_agreed: bool,
};

pub fn sha3_256(data: []const u8) [32]u8 {
    var digest = std.mem.zeroes([32]u8);
    var digest_len: c_uint = 0;

    if (c.EVP_Digest(data.ptr, data.len, digest[0..].ptr, &digest_len, c.EVP_sha3_256(), null) != 1) {
        return std.mem.zeroes([32]u8);
    }

    if (digest_len != digest.len) {
        return std.mem.zeroes([32]u8);
    }

    return digest;
}

pub fn sha3_256_hex(data: []const u8) [64]u8 {
    const digest = sha3_256(data);
    const alphabet = "0123456789abcdef";

    var out: [64]u8 = undefined;
    for (digest, 0..) |byte, i| {
        out[i * 2] = alphabet[byte >> 4];
        out[i * 2 + 1] = alphabet[byte & 0x0f];
    }
    return out;
}

fn hasLeadingZeroHex(hash: [32]u8, difficulty: u32) bool {
    var remaining = difficulty;
    for (hash) |byte| {
        if (remaining == 0) return true;

        if (remaining >= 2) {
            if (byte != 0) return false;
            remaining -= 2;
            continue;
        }

        return (byte >> 4) == 0;
    }

    return remaining == 0;
}

pub fn mineBlock(block_data: []const u8, difficulty: u32) u64 {
    if (difficulty > 64) return 0;

    const candidate = std.heap.page_allocator.alloc(u8, block_data.len + @sizeOf(u64)) catch return 0;
    defer std.heap.page_allocator.free(candidate);

    std.mem.copyForwards(u8, candidate[0..block_data.len], block_data);

    var nonce: u64 = 0;
    while (true) : (nonce += 1) {
        std.mem.writeInt(u64, candidate[block_data.len .. block_data.len + @sizeOf(u64)], nonce, .little);
        if (hasLeadingZeroHex(sha3_256(candidate), difficulty)) return nonce;
    }
}
