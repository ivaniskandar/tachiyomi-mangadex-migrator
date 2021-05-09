package xyz.ivaniskandar.ayunda

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts

/**
 * CreateDocument with proper MIME type set
 */
class CreateDocument : ActivityResultContracts.CreateDocument() {
    override fun createIntent(context: Context, input: String): Intent {
        val intent = super.createIntent(context, input)
        val splitName = input.split(".")
        if (splitName.size >= 2) {
            intent.type = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(splitName.last())
        }
        return intent
    }
}
