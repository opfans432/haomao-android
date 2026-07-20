package com.catshelf.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.catshelf.reader.data.FavoritesStore
import com.catshelf.reader.data.NiceCatCatalogDataSource
import com.catshelf.reader.ui.HaoMaoApp
import com.catshelf.reader.ui.theme.HaoMaoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("haomao", MODE_PRIVATE)
        val dataSource = NiceCatCatalogDataSource()
        val favoritesStore = FavoritesStore(this)
        setContent {
            HaoMaoTheme {
                HaoMaoApp(
                    dataSource = dataSource,
                    favoritesStore = favoritesStore,
                    initialAgeConfirmed = preferences.getBoolean("age_and_agreement_confirmed_v1", false),
                    onAgeConfirmed = {
                        preferences.edit().putBoolean("age_and_agreement_confirmed_v1", true).apply()
                    },
                )
            }
        }
    }
}
