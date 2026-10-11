package app.sloplex.mobile
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sloplex.ui.mobile.SloplexMobile
import app.sloplex.feature.session.ConnectionViewModel
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SloplexMobile(viewModel<ConnectionViewModel>()) }
    }
}
