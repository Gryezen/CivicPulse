package com.gryezen.civicpulse.data.remote

import com.gryezen.civicpulse.data.model.ChangePasswordRequest
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintResponse
import com.gryezen.civicpulse.data.model.LoginRequest
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.model.RegisterRequest
import com.gryezen.civicpulse.data.model.UpdateProfileRequest
import com.gryezen.civicpulse.data.model.User
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body

/**
 * Confirmed against the govtheme branch (auth.py's module docstring + the
 * actual @auth_bp routes — these are real and live):
 *
 *   POST   /api/auth/register       -> User (201)
 *   POST   /api/auth/login          -> User
 *   POST   /api/auth/logout         -> {"ok": true}
 *   GET    /api/user/me             -> User            (401 if not logged in)
 *   PATCH  /api/user/me             -> User             (any subset of profile fields, not password)
 *   POST   /api/user/me/password    -> {"ok": true}      {current_password, new_password}
 *
 * Errors are always `{"error": "message"}` — see ErrorParsing.kt.
 *
 * NOT implemented server-side yet (every template says so with a
 * "TODO(backend)" comment — complaints/dockets are still `mockComplaints` /
 * `mockDockets` in the HTML, and policies are the hardcoded CP_POLICIES
 * array in static/main.js). These are still just the best-guess paths the
 * web team sketched in the templates' `Api /api/...` eyebrows/comments:
 *
 *   POST   /api/create/complaint            -> CreateComplaintResponse (multipart)
 *          NOTE: complaint.html's mock payload groups the dates as a single
 *          `date_from_to: [from, to]` pair rather than two separate fields —
 *          this interface sends them as two multipart parts (`date_from` /
 *          `date_to`) instead, since arrays don't serialize cleanly as
 *          multipart form fields. Reconcile with whichever shape the real
 *          endpoint expects once it exists.
 *   GET    /api/admin/view/complaint        -> List<Complaint>   (citizen-scoped list, dashboard)
 *   GET    /api/admin/view/complaint/{id}   -> Complaint         (public docket lookup)
 *   GET    /api/citizen/search?q=           -> List<Complaint>   (free-text/NLP queue search — see
 *                                               track.html's comment mentioning this shape)
 *   GET    /api/policies/                   -> List<Policy>
 *   GET    /api/policies/{slug}             -> Policy
 *
 * All six of these fail gracefully today (server 404s) — see
 * data/repository/*.kt, which fall back to the same local demo data the web
 * app mocks (data/local/DemoData.kt) so the app is still fully demoable.
 * This is the one file to edit once any of them go live.
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

    @Multipart
    @POST("api/create/complaint")
    suspend fun createComplaint(
        @Part("title") title: RequestBody,
        @Part("date_from") dateFrom: RequestBody,
        @Part("date_to") dateTo: RequestBody,
        @Part("authority_level") authorityLevel: RequestBody,
        @Part("language") language: RequestBody,
        @Part("body") body: RequestBody,
        @Part proofFiles: List<MultipartBody.Part>
    ): Response<CreateComplaintResponse>

    @GET("api/admin/view/complaint")
    suspend fun myComplaints(): Response<List<Complaint>>

    @GET("api/admin/view/complaint/{complaintId}")
    suspend fun findComplaint(@Path("complaintId") complaintId: String): Response<Complaint>

    @GET("api/citizen/search")
    suspend fun searchComplaints(@Query("q") query: String): Response<List<Complaint>>

    @GET("api/policies/")
    suspend fun policies(): Response<List<Policy>>

    @GET("api/policies/{slug}")
    suspend fun policy(@Path("slug") slug: String): Response<Policy>
}
