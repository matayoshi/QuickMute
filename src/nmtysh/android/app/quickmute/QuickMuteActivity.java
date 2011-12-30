/*
BSD License

Copyright(c) 2011, N.Matayoshi All rights reserved.

Redistribution and use in source and binary forms, with or without modification, 
are permitted provided that the following conditions are met:

・Redistributions of source code must retain the above copyright notice, 
  this list of conditions and the following disclaimer.
・Redistributions in binary form must reproduce the above copyright notice, 
  this list of conditions and the following disclaimer in the documentation 
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. 
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package nmtysh.android.app.quickmute;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class QuickMuteActivity extends PreferenceActivity {
	private Context context = null;
	private boolean isMuted = false;
	private boolean isStayNotification = true;
	private NotificationManager nManager;
	private AudioManager aManager;

	private final static int MODE_CHANGE_MUTE = 1;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Context の取得
		context = getApplicationContext();

		nManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		aManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		Intent intent = getIntent();
		String key;
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);
		isStayNotification = pref.getBoolean(getString(R.string.key_stay_notification),
				true);
		switch (intent.getIntExtra("mode", 0)) {
			case MODE_CHANGE_MUTE:
				SharedPreferences.Editor editor = pref.edit();
				key = getString(R.string.key_muted);
				isMuted = !pref.getBoolean(key, false);
				editor.putBoolean(key, isMuted);
				editor.commit();

				setMute(isMuted);
				setNotification();
				finish();
				break;
			default:
				addPreferencesFromResource(R.xml.preference);

				// イベントハンドラの登録
				int[] keys = { R.string.key_muted,
						R.string.key_stay_notification };
				CheckBoxPreference checkbox;
				for (int i = 0; i < keys.length; i++) {
					key = getString(keys[i]);
					checkbox = (CheckBoxPreference) findPreference(key);
					checkbox.setOnPreferenceChangeListener(checkboxListener);
				}

				setNotification();
				break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		String key = getString(R.string.key_muted);
		CheckBoxPreference checkbox = (CheckBoxPreference) findPreference(key);
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);
		isMuted = pref.getBoolean(key, false);
		checkbox.setChecked(isMuted);
	}

	// チェックボックスの変更を捕捉
	private OnPreferenceChangeListener checkboxListener = new OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			String key = preference.getKey();
			if (key.equals(getString(R.string.key_muted))) {
				isMuted = (Boolean) newValue;
				setMute(isMuted);
				setNotification();
			} else if (key.equals(getString(R.string.key_stay_notification))) {
				isStayNotification = (Boolean) newValue;
				setNotification();
			} else {
				return false;
			}
			return true;
		}
	};

	// ミュートの状態を切り替える
	private void setMute(boolean isMuted) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = pref.edit();

		int volume = 0;
		String key[] = { getString(R.string.key_ring),
				getString(R.string.key_ring_volume),
				getString(R.string.key_notification),
				getString(R.string.key_notification_volume),
				getString(R.string.key_alarm),
				getString(R.string.key_alarm_volume),
				getString(R.string.key_music),
				getString(R.string.key_music_volume),
				getString(R.string.key_system),
				getString(R.string.key_system_volume), };
		int streamType[] = { AudioManager.STREAM_RING,
				AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM,
				AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM, };

		if (pref.getBoolean(key[0], false) && pref.getBoolean(key[8], false)) {
			aManager.setRingerMode(isMuted ? AudioManager.RINGER_MODE_VIBRATE
					: AudioManager.RINGER_MODE_NORMAL);
		}

		for (int i = 0; i < streamType.length; i++) {
			if (pref.getBoolean(key[i * 2], false)) {
				volume = aManager.getStreamVolume(streamType[i]);
				if (isMuted) {
					// 現在の音量を記録する
					editor.putInt(key[i * 2 + 1], volume);
					volume = 0;
				} else {
					// 音量が0(消音)であれば保存した値に書き換える
					if (volume == 0) {
						volume = pref.getInt(key[i * 2 + 1],
								aManager.getStreamMaxVolume(streamType[i]));
					}
				}
				aManager.setStreamVolume(streamType[i], volume, 0);
			}
		}
		editor.commit();
	}

	// 通知領域に表示
	private void setNotification() {
		if (!isStayNotification) {
			unsetNotification();
			return;
		}

		Intent nintent = new Intent(context, QuickMuteActivity.class);
		nintent.putExtra("mode", MODE_CHANGE_MUTE);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				nintent, 0);

		Notification notification = new Notification(
				isMuted ? R.drawable.ic_stat_mute : R.drawable.ic_stat_unmute,
				getString(R.string.app_name), System.currentTimeMillis());
		notification.setLatestEventInfo(context, getString(R.string.app_name),
				getString(isMuted ? R.string.change_unmute
						: R.string.change_mute), contentIntent);
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		// nManager.cancel(1);
		nManager.notify(1, notification);
	}

	// 通知領域から削除
	private void unsetNotification() {
		nManager.cancel(1);
	}
}
// EOF