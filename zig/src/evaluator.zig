const std = @import("std");

pub const Token = union(enum) {
    Num: i64,
    Op: u8,
};

pub fn precedence(op: u8) u8 {
    return switch (op) {
        '+', '-' => 1,
        '*', '/' => 2,
        else => 0,
    };
}

pub fn applyOp(a: i64, op: u8, b: i64) ?i64 {
    return switch (op) {
        '+' => a + b,
        '-' => a - b,
        '*' => a * b,
        '/' => if (b == 0) null else @divTrunc(a, b),
        else => null,
    };
}

pub fn tokenize(allocator: std.mem.Allocator, expr: []const u8) ?[]Token {
    var tokens: std.ArrayListUnmanaged(Token) = .empty;
    errdefer tokens.deinit(allocator);

    var i: usize = 0;
    while (i < expr.len) {
        const ch = expr[i];
        const is_negative_prefix = ch == '-' and
            (tokens.items.len == 0 or switch (tokens.items[tokens.items.len - 1]) {
            .Op => true,
            else => false,
        }) and
            i + 1 < expr.len and
            std.ascii.isDigit(expr[i + 1]);

        if (std.ascii.isDigit(ch) or is_negative_prefix) {
            const start = i;
            if (ch == '-') i += 1;

            while (i < expr.len and std.ascii.isDigit(expr[i])) : (i += 1) {}

            const value = std.fmt.parseInt(i64, expr[start..i], 10) catch return null;
            tokens.append(allocator, .{ .Num = value }) catch return null;
            continue;
        }

        switch (ch) {
            '+', '-', '*', '/' => {
                tokens.append(allocator, .{ .Op = ch }) catch return null;
                i += 1;
            },
            else => return null,
        }
    }

    return tokens.toOwnedSlice(allocator) catch return null;
}

fn popValue(values: *std.ArrayListUnmanaged(i64)) ?i64 {
    if (values.items.len == 0) return null;
    return values.pop();
}

fn popOp(ops: *std.ArrayListUnmanaged(u8)) ?u8 {
    if (ops.items.len == 0) return null;
    return ops.pop();
}

pub fn evaluate(expr: []const u8) ?i32 {
    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();

    const allocator = arena.allocator();
    const tokens = tokenize(allocator, expr) orelse return null;
    if (tokens.len == 0) return null;

    var values: std.ArrayListUnmanaged(i64) = .empty;
    var ops: std.ArrayListUnmanaged(u8) = .empty;

    for (tokens) |token| {
        switch (token) {
            .Num => |n| values.append(allocator, n) catch return null,
            .Op => |op| {
                while (ops.items.len > 0) {
                    const top = ops.items[ops.items.len - 1];
                    if (precedence(top) < precedence(op)) break;

                    _ = ops.pop();
                    const b = popValue(&values) orelse return null;
                    const a = popValue(&values) orelse return null;
                    values.append(allocator, applyOp(a, top, b) orelse return null) catch return null;
                }

                ops.append(allocator, op) catch return null;
            },
        }
    }

    while (popOp(&ops)) |op| {
        const b = popValue(&values) orelse return null;
        const a = popValue(&values) orelse return null;
        values.append(allocator, applyOp(a, op, b) orelse return null) catch return null;
    }

    if (values.items.len != 1) return null;
    return std.math.cast(i32, values.items[0]);
}

test "evaluate mirrors the Rust shunting-yard cases" {
    // Basic arithmetic
    try std.testing.expectEqual(@as(?i32, 13), evaluate("11+2"));
    try std.testing.expectEqual(@as(?i32, 0), evaluate("0+0"));
    try std.testing.expectEqual(@as(?i32, 1000), evaluate("999+1"));

    // Subtraction
    try std.testing.expectEqual(@as(?i32, 10), evaluate("13-3"));
    try std.testing.expectEqual(@as(?i32, -3), evaluate("5-8"));

    // Multiplication
    try std.testing.expectEqual(@as(?i32, 18), evaluate("3*6"));
    try std.testing.expectEqual(@as(?i32, 0), evaluate("7*0"));

    // Division
    try std.testing.expectEqual(@as(?i32, 30), evaluate("360/12"));
    try std.testing.expectEqual(@as(?i32, 3), evaluate("7/2"));
    try std.testing.expectEqual(@as(?i32, -3), evaluate("-7/2"));

    // Division by zero
    try std.testing.expectEqual(@as(?i32, null), evaluate("5/0"));

    // Negative prefix
    try std.testing.expectEqual(@as(?i32, -2), evaluate("-5+3"));
    try std.testing.expectEqual(@as(?i32, -20), evaluate("-10*2"));
    try std.testing.expectEqual(@as(?i32, -10), evaluate("-7-3"));

    // Operator precedence (the whole reason we have shunting-yard)
    try std.testing.expectEqual(@as(?i32, 14), evaluate("2+3*4"));
    try std.testing.expectEqual(@as(?i32, 4), evaluate("10-2*3"));
    try std.testing.expectEqual(@as(?i32, 4), evaluate("6/2+1"));
    try std.testing.expectEqual(@as(?i32, 26), evaluate("2*3+4*5"));

    // Left-to-right associativity
    try std.testing.expectEqual(@as(?i32, 5), evaluate("10-3-2"));
    try std.testing.expectEqual(@as(?i32, 5), evaluate("100/10/2"));
    try std.testing.expectEqual(@as(?i32, 10), evaluate("1+2+3+4"));

    // Single number
    try std.testing.expectEqual(@as(?i32, 42), evaluate("42"));

    // Error cases
    try std.testing.expectEqual(@as(?i32, null), evaluate(""));
    try std.testing.expectEqual(@as(?i32, null), evaluate("abc"));
    try std.testing.expectEqual(@as(?i32, null), evaluate("12+"));
}
