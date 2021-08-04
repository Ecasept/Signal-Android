package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.util.RingtoneUtil

class CustomNotificationsSettingsFragment : DSLSettingsFragment(R.string.CustomNotificationsDialogFragment__custom_notifications) {

  private val vibrateLabels: Array<String> by lazy {
    resources.getStringArray(R.array.recipient_vibrate_entries)
  }

  private val viewModel: CustomNotificationsSettingsViewModel by viewModels(factoryProducer = this::createFactory)

  private lateinit var callSoundResultLauncher: ActivityResultLauncher<Intent>
  private lateinit var messageSoundResultLauncher: ActivityResultLauncher<Intent>

  private fun createFactory(): CustomNotificationsSettingsViewModel.Factory {
    val recipientId = CustomNotificationsSettingsFragmentArgs.fromBundle(requireArguments()).recipientId
    val repository = CustomNotificationsSettingsRepository(requireContext())

    return CustomNotificationsSettingsViewModel.Factory(recipientId, repository)
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    messageSoundResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      handleResult(result, viewModel::setMessageSound)
    }

    callSoundResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      handleResult(result, viewModel::setCallSound)
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun handleResult(result: ActivityResult, resultHandler: (Uri?) -> Unit) {
    val resultCode = result.resultCode
    val data = result.data

    if (resultCode == Activity.RESULT_OK && data != null) {
      val uri: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      resultHandler(uri)
    }
  }

  private fun getConfiguration(state: CustomNotificationsSettingsState): DSLConfiguration {
    return configure {

      val controlsEnabled = state.hasCustomNotifications && state.isInitialLoadComplete

      sectionHeaderPref(R.string.CustomNotificationsDialogFragment__messages)

      if (NotificationChannels.supported()) {
        switchPref(
          title = DSLSettingsText.from(R.string.CustomNotificationsDialogFragment__use_custom_notifications),
          isEnabled = state.isInitialLoadComplete,
          isChecked = state.hasCustomNotifications,
          onClick = { viewModel.setHasCustomNotifications(!state.hasCustomNotifications) }
        )
      }

      clickPref(
        title = DSLSettingsText.from(R.string.CustomNotificationsDialogFragment__notification_sound),
        summary = DSLSettingsText.from(getRingtoneSummary(requireContext(), state.messageSound, Settings.System.DEFAULT_NOTIFICATION_URI)),
        isEnabled = controlsEnabled,
        onClick = { requestSound(state.messageSound, false) }
      )

      if (NotificationChannels.supported()) {
        switchPref(
          title = DSLSettingsText.from(R.string.CustomNotificationsDialogFragment__vibrate),
          isEnabled = controlsEnabled,
          isChecked = state.messageVibrateEnabled,
          onClick = { viewModel.setMessageVibrate(RecipientDatabase.VibrateState.fromBoolean(!state.messageVibrateEnabled)) }
        )
      } else {
        radioListPref(
          title = DSLSettingsText.from(R.string.CustomNotificationsDialogFragment__vibrate),
          isEnabled = controlsEnabled,
          listItems = vibrateLabels,
          selected = state.messageVibrateState.id,
          onSelected = {
            viewModel.setMessageVibrate(RecipientDatabase.VibrateState.fromId(it))
          }
        )
      }

      if (state.showCallingOptions) {
        dividerPref()

        sectionHeaderPref(R.string.CustomNotificationsDialogFragment__call_settings)

        clickPref(
          title = DSLSettingsText.from(R.string.CustomNotificationsDialogFragment__ringtone),
          summary = DSLSettingsText.from(getRingtoneSummary(requireContext(), state.callSound, Settings.System.DEFAULT_RINGTONE_URI)),
          isEnabled = controlsEnabled,
          onClick = { requestSound(state.callSound, true) }
        )

        radioListPref(
          title = DSLSettingsText.from(R.string.CustomNotificationsDialogFragment__vibrate),
          isEnabled = controlsEnabled,
          listItems = vibrateLabels,
          selected = state.callVibrateState.id,
          onSelected = {
            viewModel.setCallVibrate(RecipientDatabase.VibrateState.fromId(it))
          }
        )
      }
    }
  }

  private fun getRingtoneSummary(context: Context, ringtone: Uri?, defaultNotificationUri: Uri?): String {
    if (ringtone == null || ringtone == defaultNotificationUri) {
      return context.getString(R.string.CustomNotificationsDialogFragment__default)
    } else if (ringtone.toString().isEmpty()) {
      return context.getString(R.string.preferences__silent)
    } else {
      val tone = RingtoneUtil.getRingtone(requireContext(), ringtone)
      if (tone != null) {
        return tone.getTitle(context)
      }
    }
    return context.getString(R.string.CustomNotificationsDialogFragment__default)
  }

  private fun requestSound(current: Uri?, forCalls: Boolean) {
    val existing: Uri? = when {
      current == null -> getDefaultSound(forCalls)
      current.toString().isEmpty() -> null
      else -> current
    }

    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
      putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
      putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
      putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (forCalls) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
      putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
    }

    if (forCalls) {
      callSoundResultLauncher.launch(intent)
    } else {
      messageSoundResultLauncher.launch(intent)
    }
  }

  private fun getDefaultSound(forCalls: Boolean) = if (forCalls) Settings.System.DEFAULT_RINGTONE_URI else Settings.System.DEFAULT_NOTIFICATION_URI
}
