package edu.singaporetech.inf2007quiz01.consensus

/** Pure Kotlin arithmetic oracle for checking the much more dramatic pipeline. */
object KotlinOracle {
    fun compute(a: Int, b: Int, operator: String): Int {
        return when (operator) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> {
                require(b != 0) { "Division by zero" }
                a / b
            }

            else -> error("Unsupported operator: $operator")
        }
    }
}
