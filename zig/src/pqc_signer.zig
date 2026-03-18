const std = @import("std");

const c = @cImport({
    @cInclude("openssl/bio.h");
    @cInclude("openssl/evp.h");
    @cInclude("openssl/pem.h");
    @cInclude("openssl/err.h");
});

fn lenToCInt(len: usize) ?c_int {
    return std.math.cast(c_int, len);
}

fn readBioIntoBuffer(bio: *c.BIO, out: []u8) ?usize {
    if (out.len == 0) return null;

    const writable = if (out.len > 1) out[0 .. out.len - 1] else out[0..0];
    const read_len = lenToCInt(writable.len) orelse return null;
    const bytes_read = c.BIO_read(bio, writable.ptr, read_len);
    if (bytes_read <= 0) return null;

    const actual: usize = @intCast(bytes_read);
    if (actual >= out.len) return null;
    out[actual] = 0;
    return actual;
}

fn bioFromPem(pem: []const u8) ?*c.BIO {
    const pem_len = lenToCInt(pem.len) orelse return null;
    return c.BIO_new_mem_buf(pem.ptr, pem_len);
}

pub fn generateKeypair(privkey_buf: []u8, pubkey_buf: []u8) bool {
    const ctx = c.EVP_PKEY_CTX_new_from_name(null, "ML-DSA-65", null) orelse return false;
    defer c.EVP_PKEY_CTX_free(ctx);

    if (c.EVP_PKEY_keygen_init(ctx) != 1) return false;

    var pkey: ?*c.EVP_PKEY = null;
    if (c.EVP_PKEY_keygen(ctx, &pkey) != 1) return false;
    defer if (pkey) |key| c.EVP_PKEY_free(key);

    const priv_bio = c.BIO_new(c.BIO_s_mem()) orelse return false;
    defer c.BIO_free(priv_bio);

    const pub_bio = c.BIO_new(c.BIO_s_mem()) orelse return false;
    defer c.BIO_free(pub_bio);

    if (c.PEM_write_bio_PrivateKey(priv_bio, pkey, null, null, 0, null, null) != 1) return false;
    if (c.PEM_write_bio_PUBKEY(pub_bio, pkey) != 1) return false;

    return readBioIntoBuffer(priv_bio, privkey_buf) != null and readBioIntoBuffer(pub_bio, pubkey_buf) != null;
}

pub fn sign(data: []const u8, privkey_pem: []const u8, sig_buf: []u8) ?usize {
    const bio = bioFromPem(privkey_pem) orelse return null;
    defer c.BIO_free(bio);

    const pkey = c.PEM_read_bio_PrivateKey(bio, null, null, null) orelse return null;
    defer c.EVP_PKEY_free(pkey);

    const md_ctx = c.EVP_MD_CTX_new() orelse return null;
    defer c.EVP_MD_CTX_free(md_ctx);

    if (c.EVP_DigestSignInit_ex(md_ctx, null, null, null, null, pkey, null) != 1) return null;

    var sig_len: usize = 0;
    if (c.EVP_DigestSign(md_ctx, null, &sig_len, data.ptr, data.len) != 1) return null;
    if (sig_len > sig_buf.len) return null;

    if (c.EVP_DigestSign(md_ctx, sig_buf.ptr, &sig_len, data.ptr, data.len) != 1) return null;
    return sig_len;
}

pub fn verify(data: []const u8, sig: []const u8, pubkey_pem: []const u8) bool {
    const bio = bioFromPem(pubkey_pem) orelse return false;
    defer c.BIO_free(bio);

    const pkey = c.PEM_read_bio_PUBKEY(bio, null, null, null) orelse return false;
    defer c.EVP_PKEY_free(pkey);

    const md_ctx = c.EVP_MD_CTX_new() orelse return false;
    defer c.EVP_MD_CTX_free(md_ctx);

    if (c.EVP_DigestVerifyInit_ex(md_ctx, null, null, null, null, pkey, null) != 1) return false;
    return c.EVP_DigestVerify(md_ctx, sig.ptr, sig.len, data.ptr, data.len) == 1;
}
