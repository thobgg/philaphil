package de.bgghome.philaphil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.bgghome.philaphil.ui.AppRoot
import de.bgghome.philaphil.ui.AppViewModel
import de.bgghome.philaphil.ui.PhilaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel((application as PhilaApp).plattform) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhilaTheme { AppRoot(viewModel) }
        }
    }
}
