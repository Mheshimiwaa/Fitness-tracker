package com.reprise.app.ui.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.reprise.app.R
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.UserAvatar
import com.reprise.app.domain.model.FitnessGoal
import com.reprise.app.domain.model.User
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.auth.AuthFormState
import com.reprise.app.ui.auth.ProfileField
import com.reprise.app.ui.auth.didSave
import com.reprise.app.ui.auth.errorFor
import com.reprise.app.ui.theme.RepRiseTheme
import java.io.File

/**
 * The profile picture, and the two ways to change it: take a photo, or choose an
 * existing one from the gallery.
 *
 * The picture is drawn by [UserAvatar], so it is the same circle the Friends and
 * Leaderboard screens show — changing it here changes it there too.
 *
 * Both routes end in the same place: a `content://` URI that
 * [com.reprise.app.ui.auth.AuthViewModel.uploadPhoto] reads, shrinks and uploads.
 * That is why the camera needs no CAMERA permission: the photo is taken by the
 * phone's camera APP, and this app only receives the result.
 *
 * The card owns its own picker state the way [ReminderCard] owns its wheel, so
 * ProfileScreen stays a list of cards rather than a place where four different
 * launchers are declared together.
 */
@Composable
internal fun ProfilePhotoCard(
    user: User,
    formState: AuthFormState,
    onPhotoPicked: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Where the camera should write. Kept in the saved state so turning the
    // phone while the camera is open does not lose track of the file.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    // A phone or tablet with no camera app cannot take a photo, so the option is
    // hidden rather than offered and then failing when tapped.
    val cameraAvailable = remember {
        context.packageManager.resolveActivity(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            PackageManager.MATCH_DEFAULT_ONLY,
        ) != null
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = cameraUri?.let(Uri::parse)
        cameraUri = null
        // `saved` is false when the user backed out of the camera, in which case
        // there is nothing to upload.
        if (saved && uri != null) onPhotoPicked(uri)
    }

    val pickFromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // The system photo picker returns null when the user cancels.
        if (uri != null) onPhotoPicked(uri)
    }

    val error = formState.errorFor(ProfileField.PROFILE_IMAGE)

    RepRiseCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_photo_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Tapping the picture itself opens the same chooser as the button.
            UserAvatar(
                displayName = user.displayName,
                username = user.username,
                profileImageUrl = user.profileImageUrl,
                size = 72.dp,
                modifier = Modifier.clickable(
                    enabled = !formState.isSubmitting,
                    onClick = { choosing = true },
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.profile_photo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (formState.isSubmitting) {
            // Uploading is one request; saying so is better than a button that
            // looks like it did nothing.
            Text(
                text = stringResource(R.string.profile_photo_uploading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { choosing = true }) {
                    Text(text = stringResource(R.string.profile_photo_change))
                }

                if (user.profileImageUrl != null) {
                    OutlinedButton(onClick = onRemovePhoto) {
                        Text(text = stringResource(R.string.profile_photo_remove))
                    }
                }
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (formState.didSave(ProfileField.PROFILE_IMAGE)) {
            Spacer(modifier = Modifier.height(8.dp))
            SavedNotice()
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(text = stringResource(R.string.profile_photo_picker_title)) },
            text = {
                Column {
                    if (cameraAvailable) {
                        TextButton(
                            onClick = {
                                choosing = false
                                val uri = newCameraUri(context)
                                cameraUri = uri.toString()
                                takePicture.launch(uri)
                            },
                        ) {
                            Text(text = stringResource(R.string.profile_photo_take))
                        }
                    }

                    TextButton(
                        onClick = {
                            choosing = false
                            pickFromGallery.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.profile_photo_gallery))
                    }

                    if (user.profileImageUrl != null) {
                        TextButton(
                            onClick = {
                                choosing = false
                                onRemovePhoto()
                            },
                        ) {
                            Text(text = stringResource(R.string.profile_photo_remove))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) {
                    Text(text = stringResource(R.string.profile_reminder_cancel))
                }
            },
        )
    }
}

/**
 * A fresh file in the app's cache for the camera to write into, exposed through
 * this app's [FileProvider].
 *
 * A new name every time, for two reasons: the camera app cannot overwrite a shot
 * the user cancelled, and Coil caches by URI, so reusing a name could show the
 * previous photo after a re-take.
 *
 * The file lives in `cache/avatar/`, which is the ONLY directory the provider
 * exposes (see res/xml/file_paths.xml) — the camera is given access to nothing
 * else in the app.
 */
private fun newCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "avatar").apply { mkdirs() }
    val photo = File(directory, "camera_${System.currentTimeMillis()}.jpg")

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photo,
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfilePhotoCardPreview() {
    RepRiseTheme {
        ProfilePhotoCard(
            user = User(
                id = "1",
                displayName = "Alex Lifter",
                username = "alex_lifter",
                email = "alex@example.com",
                profileImageUrl = null,
                fitnessGoal = FitnessGoal.GAIN_MUSCLE,
                weightUnit = WeightUnit.KG,
                heightCm = 180,
            ),
            formState = AuthFormState(),
            onPhotoPicked = {},
            onRemovePhoto = {},
        )
    }
}
