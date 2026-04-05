package com.socialsentry.bkashserver.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import com.socialsentry.bkashserver.BuildConfig

object SupabaseClientManager {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Functions)
            // Note: HTTP timeout is handled per-call via withContext(Dispatchers.IO) + try/catch
            // in PaymentUploader. The Supabase Kotlin SDK 2.5 httpConfig is internal API
            // and cannot be used here. The 15s OS-level TCP timeout applies naturally.
        }
    }
}
