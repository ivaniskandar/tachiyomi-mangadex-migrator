package xyz.ivaniskandar.ayunda

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper

/**
 * Input is pair of document title and MIME type.
 */
class CreateDocument : ActivityResultContract<Pair<String?, String?>, Uri?>() {
    @CallSuper
    override fun createIntent(context: Context, input: Pair<String?, String?>): Intent {
        var (title, mimeType) = input
        if (mimeType == null) {
            mimeType = "*/*"
        }
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, title)
    }

    override fun getSynchronousResult(
        context: Context,
        input: Pair<String?, String?>
    ): SynchronousResult<Uri?>? {
        return null
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
    }
}
