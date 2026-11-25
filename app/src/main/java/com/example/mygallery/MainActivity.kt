package com.example.mygallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mygallery.ui.GalleryRoot
import com.example.mygallery.ui.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: GalleryViewModel = viewModel()
            GalleryTheme(vm.settings.theme) {
                GalleryRoot(vm)
            }
        }
    }
}
