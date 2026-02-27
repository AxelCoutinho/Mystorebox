package com.example.mystorebox.utils

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.sheets.v4.SheetsScopes

object GoogleAuthUtil {

    fun getAuthClient(context: Context): AuthorizationClient {
        return Identity.getAuthorizationClient(context)
    }

    fun getSheetsAuthRequest(): AuthorizationRequest {
        val requestedScopes = listOf(Scope(SheetsScopes.SPREADSHEETS))
        return AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()
    }
}