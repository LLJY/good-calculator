//! CalBot Division Microservice
//!
//! Rust was evicted from the native JNI layer in favor of Zig,
//! but it clawed its way back as a WASM module. Poetic justice.
//!
//! Returns i32::MIN on division by zero because Erlang's
//! "let it crash" philosophy didn't make it into this build.

#[no_mangle]
pub extern "C" fn div(a: i32, b: i32) -> i32 {
    if b == 0 {
        return i32::MIN; // sentinel for division by zero
    }
    a / b // truncates toward zero, same as Kotlin Int division
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn basic_division() {
        assert_eq!(div(10, 2), 5);
        assert_eq!(div(7, 2), 3);
        assert_eq!(div(-7, 2), -3);
        assert_eq!(div(0, 5), 0);
    }

    #[test]
    fn division_by_zero() {
        assert_eq!(div(5, 0), i32::MIN);
        assert_eq!(div(0, 0), i32::MIN);
    }
}
