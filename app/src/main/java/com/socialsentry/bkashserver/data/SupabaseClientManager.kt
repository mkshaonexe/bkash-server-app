package com.socialsentry.bkashserver.data

import io.github.jan_tennert.supabase.SupabaseClient
import io.github.jan_tennert.supabase.createSupabaseClient
import io.github.jan_tennert.supabase.postgrest.Postgrest
import com.socialsentry.bkashserver.BuildConfig

object SupabaseClientManager {
    // These will be injected via BuildConfig or similar
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_SERVICE_ROLE_KEY = BuildConfig.SUPABASE_SERVICE_ROLE_KEY

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_SERVICE_ROLE_KEY
        ) {
            install(Postgrest)
        }
    }
}
