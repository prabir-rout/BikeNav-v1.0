package com.bikenv.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    val orsApi: ORSApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(ORSApi::class.java)
    }
}