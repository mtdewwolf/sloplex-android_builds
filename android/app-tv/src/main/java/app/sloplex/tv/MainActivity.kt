package app.sloplex.tv
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sloplex.ui.tv.SloplexTv
import app.sloplex.feature.session.SessionViewModel
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SloplexTv(viewModel<SessionViewModel>()) }
    }
}
