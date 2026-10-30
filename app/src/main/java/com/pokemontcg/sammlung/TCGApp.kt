package com.pokemontcg.sammlung

import android.app.Application
import com.pokemontcg.sammlung.di.AppContainer

class TCGApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
