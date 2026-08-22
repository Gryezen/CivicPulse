package com.gryezen.civicpulse.data.remote

import com.gryezen.civicpulse.data.model.AuditTrailResponse
import com.gryezen.civicpulse.data.model.BulkActionRequest
import com.gryezen.civicpulse.data.model.BulkActionResponse
import com.gryezen.civicpulse.data.model.ChangePasswordRequest
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintRequest
import com.gryezen.civicpulse.data.model.LoginRequest
import com.gryezen.civicpulse.data.model.OfficerQueueResponse
import com.gryezen.civicpulse.data.model.OfficerSummary
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.model.PolicySyncRequest
import com.gryezen.civicpulse.data.model.PolicySyncResponse
import com.gryezen.civicpulse.data.model.RegisterRequest
import com.gryezen.civicpulse.data.model.ResolveWithPhotoRequest
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
 *   POST   /api/complaints                       -> Complaint (201) {title, body, language,
 *                                                    files_count, before_photo?} [complaints.py]
 *   GET    /api/complaints/mine                  -> List<Complaint>   (the logged-in citizen's own filings)
 *   GET    /api/complaints                       -> List<Complaint>   ?q=&category=&status=&sort=
 *                                                    (public/admin queue; each entry gets a `matchLabel`
 *                                                    when `q` is supplied — see complaints.py's queue())
 *   POST   /api/complaints/{id}/confirm          -> Complaint   citizen confirms a resolved/
 *                                                    pending-confirmation complaint is actually fixed
 *   POST   /api/complaints/{id}/dispute          -> Complaint   citizen reopens one that isn't
 *
 *   GET    /api/policies            -> List<Policy>                   [app.py]
 *   GET    /api/policies/{slug}     -> Policy   (404 -> {"error": ...} if unknown) [app.py]
 *
 *   GET    /api/officer/summary                                  -> OfficerSummary          [officer.py]
 *   GET    /api/officer/queue        ?broad_category=&status=&only_flagged=&
 *                                     include_auto_resolved=&page=&page_size=  -> OfficerQueueResponse
 *   POST   /api/officer/bulk         {ids, action: assign|escalate|resolve, officer?} -> BulkActionResponse
 *   POST   /api/officer/complaints/{id}/resolve-with-photo  {after_photo}  -> Complaint
 *   GET    /api/officer/complaints/{id}/trail                    -> AuditTrailResponse
 *   POST   /api/officer/policies/sync  {source?}                 -> PolicySyncResponse
 *   (all routes under /api/officer/ require login AND current_user.isOfficial — 403 otherwise)
 *
 *   GET    /api/admin/pending-officials              -> List<User>   [admin.py]
 *   POST   /api/admin/officials/{id}/approve         -> User
 *   POST   /api/admin/officials/{id}/reject          -> User
 *   (all routes under /api/admin/ require login AND current_user.isAdmin — 403 otherwise)
 *
 * Errors are always `{"error": "message"}` — see ErrorParsing.kt.
 *
 * There is still no single "get complaint by docket ID" endpoint server-side
 * — the queue (`GET /api/complaints`) is the only public/browsable list, and
 * `q` matches on title/body keywords, not on ID. ComplaintRepository.findComplaint()
 * resolves IDs against what's already been fetched (queue + locally-filed
 * complaints) rather than a dedicated remote call.
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

    @POST("api/complaints/{id}/confirm")
    suspend fun confirmComplaint(@Path("id") id: String): Response<Complaint>

    @POST("api/complaints/{id}/dispute")
    suspend fun disputeComplaint(@Path("id") id: String): Response<Complaint>

    @GET("api/policies")
    suspend fun policies(): Response<List<Policy>>

    @GET("api/policies/{slug}")
    suspend fun policy(@Path("slug") slug: String): Response<Policy>

    // ---------------------------------------------------------------- officer

    @GET("api/officer/summary")
    suspend fun officerSummary(): Response<OfficerSummary>

    @GET("api/officer/queue")
    suspend fun officerQueue(
        @Query("broad_category") broadCategory: String? = null,
        @Query("status") status: String? = null,
        @Query("only_flagged") onlyFlagged: String? = null,
        @Query("include_auto_resolved") includeAutoResolved: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null
    ): Response<OfficerQueueResponse>

    @POST("api/officer/bulk")
    suspend fun officerBulkAction(@Body request: BulkActionRequest): Response<BulkActionResponse>

    @POST("api/officer/complaints/{id}/resolve-with-photo")
    suspend fun officerResolveWithPhoto(
        @Path("id") id: String,
        @Body request: ResolveWithPhotoRequest
    ): Response<Complaint>

    @GET("api/officer/complaints/{id}/trail")
    suspend fun officerAuditTrail(@Path("id") id: String): Response<AuditTrailResponse>

    @POST("api/officer/policies/sync")
    suspend fun officerSyncPolicies(@Body request: PolicySyncRequest): Response<PolicySyncResponse>

    // ------------------------------------------------------------------ admin

    @GET("api/admin/pending-officials")
    suspend fun pendingOfficials(): Response<List<User>>

    @POST("api/admin/officials/{id}/approve")
    suspend fun approveOfficial(@Path("id") id: String): Response<User>

    @POST("api/admin/officials/{id}/reject")
    suspend fun rejectOfficial(@Path("id") id: String): Response<User>
}
