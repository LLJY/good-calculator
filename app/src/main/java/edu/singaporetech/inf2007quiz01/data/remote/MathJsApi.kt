package edu.singaporetech.inf2007quiz01.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the math.js REST API.
 * The API returns a plain number (not JSON), but Gson handles that fine.
 * Docs: https://api.mathjs.org/
 */
interface MathJsApi {
    /** e.g. GET /v4/?expr=2+3 returns "5" */
    @GET("?")
    suspend fun calculate(@Query("expr") expr: String): Response<Number>
}
