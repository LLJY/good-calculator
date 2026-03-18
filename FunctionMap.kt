package edu.singaporetech.inf2007quiz01

import kotlin.math.sqrt

object FunctionMap {

    fun half(x:Int):Int = x/2

    fun fib(x:Int):Int = when(x){
            0 -> 0
            1 -> 1
            else -> fib(x-1) + fib(x-2)
        }

    fun self(x:Int):Int {
        var y = x.toDouble()
        repeat(1000000000) {
            y = sqrt(y) * sqrt(y)
        }
        return y.toInt()
    }

    val functionMap = mapOf <String, (Int)->Int> (
        "half" to :: half,
        "fib" to ::fib,
        "self" to ::self
            )
}