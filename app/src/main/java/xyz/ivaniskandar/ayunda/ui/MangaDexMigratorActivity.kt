package xyz.ivaniskandar.ayunda.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.ivaniskandar.ayunda.ui.theme.AyundaTheme

class MangaDexMigratorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AyundaTheme {
                MangaDexMigratorApp(viewModel = viewModel())
            }
        }
    }
}
