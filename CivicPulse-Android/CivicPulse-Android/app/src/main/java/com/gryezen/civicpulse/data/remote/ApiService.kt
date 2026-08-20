package com.gryezen.civicpulse.data.remote

import com.gryezen.civicpulse.data.model.ChangePasswordRequest
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintRequest
import com.gryezen.civicpulse.data.model.LoginRequest
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.model.RegisterRequest
import com.gryezen.civicpulse.data.model.UpdateProfileRequest
import com.gryezen.civicpulse.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Confirmed live against Frontend/civicpulse (Flask):
 *
 *   POST   /api/auth/register       -> User (201)                     [auth.py]
 *   POST   /api/auth/login          -> User                           [auth.py]
 *   POST   /api/auth/logout         -> {"ok": true}                   [auth.py]
 *   GET    /api/user/me             -> User          (401 if signed out) [auth.py]
 *   PATCH  /api/user/me             -> User          (any subset of profile fields, not password)
 *   POST   /api/user/me/password    -> {"ok": true}   {current_password, new_password}
 *
 *   POST   /api/complaints          -> Complaint (201) {title, body, language, files_count}
 *                                       [complaints.py's create_complaint() — classified
 *                                       server-side via classify.py, JSON in/out, no file
 *                                       upload: files_count is just a count]
 *   GET    /api/complaints/mine     -> List<Complaint>   (the logged-in citizen's own filings)
 *   GET    /api/complaints          -> List<Complaint>   ?q=&category=&status=&sort=
 *                                       (public/admin queue; each entry gets a `matchLabel`
 *                                       when `q` is supplied — see complaints.py's queue())
 *
 *   GET    /api/policies            -> List<Policy>                   [app.py]
 *   GET    /api/policies/{slug}     -> Policy   (404 -> {"error": ...} if unknown) [app.py]
 *
 * Errors are always `{"error": "message"}` — see ErrorParsing.kt.
 *
 * There is still no single "get complaint by docket ID" endpoint server-side
 * — the queue (`GET /api/complaints`) is the only public/browsable list, and
 * `q` matches on title/body keywords, not on ID. ComplaintRepository.findComplaint()
 * resolves IDs against what's already been fetched (queue + locally-filed
 * complaints) rather than a dedicated remote call.
 *
 * All of the above are real and reachable now; data/local/DemoData.kt is kept
 * purely as an offline fallback (see the repository classes under
 * data/repository), not a stand-in for missing endpoints anymore.
 */
interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<User>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<User>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/user/me")
    suspend fun me(): Response<User>

    @PATCH("api/user/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<User>

    @POST("api/user/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    @POST("api/complaints")
    suspend fun createComplaint(@Body request: CreateComplaintRequest): Response<Complaint>

    @GET("api/complaints/mine")
    suspend fun myComplaints(): Response<List<Complaint>>

    @GET("api/complaints")
    suspend fun queue(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String? = null
    ): Response<List<Complaint>>

    @GET("api/policies")
    suspend fun policies(): Response<List<Policy>>

    @GET("api/policies/{slug}")
    suspend fun policy(@Path("slug") slug: String): Response<Policy>
}
