const std = @import("std");

pub fn fastDoublingFib(n: u32) i64 {
    if (n == 0) return 0;

    var a: i64 = 0;
    var b: i64 = 1;

    var bit: u32 = 1;
    while (bit <= n / 2) : (bit <<= 1) {}

    while (bit != 0) : (bit >>= 1) {
        const c_term = a * (2 * b - a);
        const d_term = a * a + b * b;

        if ((n & bit) == 0) {
            a = c_term;
            b = d_term;
        } else {
            a = d_term;
            b = c_term + d_term;
        }
    }

    return a;
}

pub fn half(x: i32) i32 {
    return @divTrunc(x, 2);
}

pub noinline fn selfFn(x: i32) i32 {
    // Yes, this is still the intentionally terrible billion-iteration sqrt loop.
    // The app apparently needs a native identity function that burns CPU like a ritual.
    var y: f64 = @floatFromInt(x);
    var i: u32 = 0;
    while (i < 1_000_000_000) : (i += 1) {
        y = @sqrt(y) * @sqrt(y);
    }

    if (!std.math.isFinite(y)) return 0;
    return @intFromFloat(y);
}

test "fast doubling keeps the known values" {
    try std.testing.expectEqual(@as(i64, 0), fastDoublingFib(0));
    try std.testing.expectEqual(@as(i64, 55), fastDoublingFib(10));
    try std.testing.expectEqual(@as(i64, 701408733), fastDoublingFib(44));
    try std.testing.expectEqual(@as(i64, 1836311903), fastDoublingFib(46));
}
