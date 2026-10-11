package app.sloplex.ui.mobile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
@Composable fun SloplexMobile() {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xff55e6c1),background=Color(0xff080e17))) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),verticalArrangement=Arrangement.Center) {
            Text("SLOPLEX",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.displaySmall)
            Text("Native Android foundation",color=Color.White,style=MaterialTheme.typography.titleLarge)
        }
    }
}
