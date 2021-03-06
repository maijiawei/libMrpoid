/*
 * Copyright (C) 2013 The Mrpoid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mrpoid.app;

import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.telephony.SmsManager;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.mrpoid.R;
import com.mrpoid.core.EmuLog;
import com.mrpoid.core.EmuUtils;
import com.mrpoid.core.Emulator;
import com.mrpoid.core.MrDefines;
import com.mrpoid.core.MrpRunner;
import com.mrpoid.core.MrpScreen;
import com.mrpoid.core.Prefer;
import com.mrpoid.gui.keypad.Keypad;
import com.mrpoid.gui.keypad.KeypadView;
import com.mrpoid.gui.keypad.Keypad.OnKeyEventListener;
import com.mrpoid.tools.keysprite.ChooserFragment;
import com.mrpoid.tools.keysprite.KeyEventListener;
import com.mrpoid.tools.keysprite.KeySprite;
import com.mrpoid.tools.keysprite.OnChooseLitener;
import com.mrpoid.tools.keysprite.Sprite;
import com.yichou.common.sdk.SdkUtils;

/**
 * @author JianbinZhu
 *
 * 创建日期：2012/10/9
 * 
 * 最后修改：2013-3-14 20:06:44
 */
public class EmulatorActivity extends FragmentActivity implements 
		Handler.Callback, 
		OnClickListener{
	static final String TAG = "EmulatorActivity";
	
	public static final String ACTION_SMS_SENT = "com.mrpoid.SMS_SENT_ACTION";
	
	public static String APP_ACTIVITY_NAME = "com.mrpoid.apps.AppActivity0";
	public static String APP_SERVICE_NAME = "com.mrpoid.apps.AppService0";
	
	
//	private static final int MSG_ID_SHOWEDIT = 1001;
	private static final int MSG_ID_UPDATE = 1002;
	private static final int	MSG_ID_KEY_DOWN = 1012;
	private static final int	MSG_ID_KEY_UP = 1003;
	private static final int	MSG_ID_UPDATE_INFO_TEXT = 1004;
	
	private static final int INFO_TYPE_KEY_SPRITE = 1001;
	private static final int REQ_SHOWEDIT = 1001;
	private static final int	REQ_GET_IMAGE = 1002;
	
	private static final int DLG_EDIT = 1001; 
	private static final int	DLG_SCALE_MODE = 1002;
	private static final int	DLG_PAD_ALPHA = 1003;
	private static final int	DLG_TOOLS = 1004;
	
	
	private TextView tvMemory, tvInfo;
	private EmulatorSurface emulatorView;
	private Emulator emulator;
	public Handler handler;
	private LayoutInflater inflater;
	private SmsReceiver mSmsReceiver;
	private ViewGroup continer;
	private KeypadView padView;
	private boolean mPaused;
	private Vibrator vibrator;
	
	
	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_ID_UPDATE: {
			if(mPaused)
				break;
			
			updateMemInfo();
			handler.sendEmptyMessageDelayed(MSG_ID_UPDATE, 1000);
			break;
		}
		
		case MSG_ID_KEY_DOWN:
			emulator.postMrpEvent(MrDefines.MR_KEY_PRESS, msg.arg1, 0);
			break;
			
		case MSG_ID_KEY_UP:
			emulator.postMrpEvent(MrDefines.MR_KEY_RELEASE, msg.arg1, 0);
			break;
			
		case MSG_ID_UPDATE_INFO_TEXT: {
			String text = msg.obj.toString();
			if(text == null)
				tvInfo.setVisibility(View.INVISIBLE);
			else {
				if(tvInfo.getVisibility() != View.VISIBLE)
					tvInfo.setVisibility(View.VISIBLE);
				tvInfo.setText(text);
			}
			
			break;
		}
			
		default:
			return false;
		}

		return true;
	}
	
	@Override
	public void onClick(View v) {
		if(v.getId() == R.id.tv_info) {
			if(v.getTag().equals(INFO_TYPE_KEY_SPRITE)){
				stopKeySprite();
			}
		}
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		EmuLog.i(TAG, "onNewIntent " + intent);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		EmuLog.i(TAG, "onCreate");

		getWindow().requestFeature(Window.FEATURE_NO_TITLE);//去掉标题栏
		if(!Prefer.showStatusBar)
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		setContentView(R.layout.activity_emulator);
		
		handler = new Handler(this);
		inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		
		emulator = Emulator.getInstance();
		emulator.setRunMrp(getIntent().getStringExtra(MrpRunner.INTENT_KEY_PATH));;
		emulator.attachActivity(this);
		
		emulatorView = new EmulatorSurface(this);
		emulatorView.setBackgroundColor(Color.TRANSPARENT);
		continer =  (ViewGroup) findViewById(R.id.contener);
		continer.addView(emulatorView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		
		//虚拟键盘
		Keypad.loadBmp(getResources());
		padView = new KeypadView(this);
		continer.addView(padView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		Keypad.getInstance().setOnKeyEventListener(mKeyEventListener);
		
		//短信模块初始化
		smsInit();

		if(Prefer.showMemInfo) {
			showMenInfo();
		}

		{
			tvInfo = new TextView(this);
			tvInfo.setBackgroundColor(0x80000000);
			tvInfo.setTextColor(0xfff0f0f0);
			tvInfo.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
			tvInfo.setSingleLine(true);
			tvInfo.setVisibility(View.INVISIBLE);
			tvInfo.setId(R.id.tv_info);
			tvInfo.setOnClickListener(this);
			
			int padding = getResources().getDimensionPixelSize(R.dimen.dp5);
			tvInfo.setPadding(padding, padding, padding, padding);
			continer.addView(tvInfo, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		}
	}
	
	@Override
	protected void onPause() {
		EmuLog.i(TAG, "onPause");

		mPaused = true;
		
		SdkUtils.getSdk().onPause(this);
		mSmsReceiver.unRegister();
		emulator.pause();
		
		if (!isFinishing()) {
			entryBackground();
		} else {
			Keypad.getInstance().setOnKeyEventListener(null);
		}
		
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		EmuLog.i(TAG, "onResume");

		mPaused = false;
		backFroground();
		SdkUtils.getSdk().onResume(this);
		emulator.resume();
		mSmsReceiver.register();
		
		if(Prefer.showMemInfo)
			handler.sendEmptyMessageDelayed(MSG_ID_UPDATE, 1000);
		
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		EmuLog.i(TAG, "onDestroy");
		
		if(emulator.isRunning()) { //说明在后台运行被杀了
			EmuLog.e(TAG, "后台运行被杀！");
			SdkUtils.getSdk().sendEvent(this, "beKilled", "");
		}

		Keypad.releaseBmp();
		unregisterReceiver(mSmsRetReceiver);
		
		emulatorView.onActivityDestroy();
		
		super.onDestroy(); 
	}
	
	@Override
	protected void onStop() {
		EmuLog.i(TAG, "onStop");
		
		super.onStop();
	}
	
	@Override
	protected void onRestart() {
		EmuLog.i(TAG, "onRestart");
		super.onRestart();
	}
	
	@Override
	protected void onStart() {
		EmuLog.i(TAG, "onStart");
		super.onStart();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		EmuLog.i(TAG, "onSaveInstanceState:" + outState);
		
		outState.putBoolean("hasSaved", true);
		outState.putString("curMrpPath", emulator.getCurMrpPath());
		
		super.onSaveInstanceState(outState);
	}
	
	/**
	 * 被杀后恢复
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		EmuLog.i(TAG, "onRestoreInstanceState:" + savedInstanceState);
		
		if(savedInstanceState.getBoolean("hasSaved", false)) {
			String curMrpPath = savedInstanceState.getString("curMrpPath");
			if(curMrpPath != null){
				EmuLog.i(TAG, "异常恢复成功");
				Emulator.getInstance().setRunMrp(curMrpPath);
			} else {
				finish();
			}
		} else {
			finish();
		}

		super.onRestoreInstanceState(savedInstanceState);
	}
	
	//-----------------------------------------------------------------
	private final BroadcastReceiver mSmsRetReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String message = null;
			boolean error = true;
			
			switch (getResultCode()) {
			case Activity.RESULT_OK:
				message = "发送成功!";
				error = false;
				break;
				
			case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
				message = "失败: 未知错误.";
				break;
				
			case SmsManager.RESULT_ERROR_NO_SERVICE:
				message = "失败: 短信服务不可用.";
				break;
				
			case SmsManager.RESULT_ERROR_NULL_PDU:
				message = "失败: PDU 空.";
				break;
				
			case SmsManager.RESULT_ERROR_RADIO_OFF:
				message = "失败: 网络错误.";
				break;
			}
			
			//通知底层结果
			emulator.vm_event(MrDefines.MR_SMS_RESULT, error? MrDefines.MR_FAILED : MrDefines.MR_SUCCESS, 0);
			Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
		}
	};
	
	private void smsInit() {
		mSmsReceiver = new SmsReceiver(this);
		
		// Register broadcast receivers for SMS sent and delivered intents
		registerReceiver(mSmsRetReceiver, new IntentFilter(ACTION_SMS_SENT));
	}
	
	private void showMenInfo() {
		tvMemory = new TextView(this);
		tvMemory.setGravity(Gravity.TOP|Gravity.RIGHT);
		tvMemory.setVisibility(View.GONE);
		tvMemory.setTextColor(Color.DKGRAY);
		LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		continer.addView(tvMemory, p);

		tvMemory.setVisibility(View.VISIBLE);
		handler.sendEmptyMessageDelayed(MSG_ID_UPDATE, 1000);
	}
	
	private void updateMemInfo() {
		emulator.native_getMemoryInfo();

		final String memInfo = String.format(Locale.US, 
				"memoryinfo--\ntotal:%d\ntop:%d\nleft:%d", 
				emulator.N2J_memLen, emulator.N2J_memTop, emulator.N2J_memLeft);
		
		EmuLog.i(TAG, memInfo);

		tvMemory.setText(memInfo);
	}
	
	public void postUIRunable(Runnable r) {
		handler.post(r);
	}
	
	public Handler getHandler() {
		return handler;
	}
	
	private void backgroundRun() {
		String entryActivity = getIntent().getStringExtra(MrpRunner.INTENT_KEY_ENTRY_ACTIVITY);

		Intent intent = new Intent();
		intent.setClassName(this, entryActivity);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_SINGLE_TOP
				| Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		intent.setAction(Intent.ACTION_MAIN);
		
		startActivity(intent);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean onMenuItemSelected(int featureId, android.view.MenuItem item) {
		if (item.getItemId() == R.id.mi_close) {
			emulator.stop();
			finish();
		} else if (item.getItemId() == R.id.mi_scnshot) {
			emulator.getScreen().screenShot(this);
		} else if (item.getItemId() == R.id.mi_entry_background) {
			backgroundRun();
		} else if (item.getItemId() == R.id.mi_switch_keypad) {
			padView.switchKeypad();
		} else if (item.getItemId() == R.id.mi_image) {
			Intent i = new Intent(
					Intent.ACTION_PICK,
					android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			startActivityForResult(i, REQ_GET_IMAGE);
		} else if (item.getItemId() == R.id.mi_color) {
			
		} else if (item.getItemId() == R.id.mi_scale_mode) {
			showDialog(DLG_SCALE_MODE);
		} else if (item.getItemId() == R.id.mi_keypad_opacity) {
			showDialog(DLG_PAD_ALPHA);
		} else if (item.getItemId() == R.id.mi_float_view) {
			floatView();
		} else if (item.getItemId() == R.id.mi_tools) {
			showDialog(DLG_TOOLS);
		}
		
		return true;
	}
	
	@Override
	public boolean onCreateOptionsMenu(android.view.Menu menu) { ////只会在第一次弹出的时候调用
		if(!Prefer.showMenu)
			return false;
		
		int i = 0;
		menu.add(0, R.id.mi_close, i++, R.string.close);
		menu.add(0, R.id.mi_entry_background, i++, R.string.entry_background);
		menu.add(0, R.id.mi_scnshot, i++, R.string.scnshot);
		menu.add(0, R.id.mi_switch_keypad, i++, R.string.switch_keypad);
		menu.add(0, R.id.mi_keypad_opacity, i++, R.string.pad_opacity);
//		menu.add(0, R.id.mi_float_view, i++, R.string.float_view);
		
//		SubMenu subMenu = menu.addSubMenu(0, R.id.mi_set_background, i++, R.string.set_background);
//		int j = 0;
//		subMenu.add(0, R.id.mi_color, j++, R.string.color);
//		subMenu.add(0, R.id.mi_image, j++, R.string.image);
		
		menu.add(0, R.id.mi_scale_mode, i++, R.string.scaling_mode);
		menu.add(0, R.id.mi_tools, i++, R.string.tools);
		
		return true;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		if (id == DLG_EDIT) {
			if(args == null)
				return null;
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			
			/**
			 * view 不能重用
			 * 2013-3-24 23:26:03
			 */
			View editDlgView = inflater.inflate(R.layout.dialog_input, null);
			final EditText editText = (EditText) editDlgView.findViewById(R.id.editText1);
			
			Object obj = args.get("title");
			builder.setTitle(obj!=null? obj.toString() : ""); 
			builder.setView(editDlgView);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					emulator.setEditInputContent(editText.getText().toString());
					emulator.postMrpEvent(MrDefines.MR_DIALOG_EVENT, MrDefines.MR_DIALOG_KEY_OK, 0);
				}
			});
			builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					emulator.setEditInputContent(null);
					emulator.postMrpEvent(MrDefines.MR_DIALOG_EVENT, MrDefines.MR_DIALOG_KEY_CANCEL, 0);
				}
			});
			builder.setCancelable(false);
			
			return builder.create();
		}
		
		return super.onCreateDialog(id, args);
	}
	
	private int tmpChoice;
	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == DLG_SCALE_MODE){
			final String[] items = getResources().getStringArray(R.array.scaling_mode_entryvalues);
			int choice = 0;

			for(String s : items){
				if(s.equals(MrpScreen.getScaleModeTag()))
					break;
				choice++;
			}
			if(choice > items.length - 1)
				choice = items.length - 1;

			return new AlertDialog.Builder(this)
	            .setTitle(R.string.scaling_mode)
	            .setSingleChoiceItems(R.array.scaling_mode_entries, choice, new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int which) {
	                    /* User clicked on a radio button do some stuff */
	                	tmpChoice = which;
	                }
	            })
	            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                	MrpScreen.parseScaleMode(items[tmpChoice]);
	                	emulator.getScreen().initScale();
	                	emulatorView.flush();
	                }
	            })
	            .setNegativeButton(R.string.cancel, null)
	           .create();
		} else if (id == DLG_PAD_ALPHA) {
			SeekBar bar = new SeekBar(this);
			bar.setMax(255);
			bar.setProgress(Keypad.getInstance().getOpacity());
			bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}
				
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}
				
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if(fromUser){
						if(progress < 20)
							progress = 20;
						else if (progress > 0xff)
							progress = 0xff;
						
						Prefer.keypadOpacity = progress;
						padView.setKeypadOpacity(progress);
					}
				}
			});
			
			return new AlertDialog.Builder(this)
				.setTitle(R.string.pad_opacity)
				.setView(bar)
				.setPositiveButton(R.string.ok, null)
				.create();
		} else if (id == DLG_TOOLS) {
			
			return new AlertDialog.Builder(this)
				.setTitle(R.string.tools)
				.setItems(TOOLS, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						runTool(which);
					}
				})
				.create();
		}
		
		return super.onCreateDialog(id);
	}
	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
		if (id == DLG_EDIT) {
			final EditText editText = (EditText) dialog.findViewById(R.id.editText1);
			if(editText == null){ //虽然不可能失败
				emulator.setEditInputContent(null);
				emulator.postMrpEvent(MrDefines.MR_DIALOG_EVENT, MrDefines.MR_DIALOG_KEY_CANCEL, 0);
				return;
			}
			
			Object obj = args.get("content");
			editText.setText(obj!=null? obj.toString() : "");
			//字数限制
			if(Prefer.limitInputLength)
				editText.setFilters(new  InputFilter[]{ new  InputFilter.LengthFilter(args.getInt("max"))});
			
			int newType, type = args.getInt("type", MrDefines.MR_EDIT_ANY);
			
			if (type == MrDefines.MR_EDIT_ALPHA)
				newType = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
			else if (type == MrDefines.MR_EDIT_NUMERIC)
				newType = EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
			else if (type == MrDefines.MR_EDIT_PASSWORD)
				newType = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
			else
				newType = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
			
			editText.setInputType(newType);
			
			obj = args.get("title");
			dialog.setTitle(obj!=null? obj.toString() : "");
//			dialog.setTitle(args.getCharSequence("title", ""));
		} else {
			super.onPrepareDialog(id, dialog);
		}
	}
	
	void showFragmentDialog(DialogFragment fragment) {
        fragment.show(getSupportFragmentManager(), "dialog");
    }
	
	private static final String[] TOOLS = new String[]{
		"按键精灵"
	};
	
	private KeySprite mKeySprite;
	
	private void stopKeySprite() {
		if(mKeySprite != null){
			mKeySprite.stop();
			mKeySprite = null;
			tvInfo.setVisibility(View.INVISIBLE);
		}
	}
	
	private void runKeySprite(KeySprite sprite) {
		mKeySprite = sprite;
		tvInfo.setTag(INFO_TYPE_KEY_SPRITE);
		
		sprite.run(new KeyEventListener() {
			
			@Override
			public void onKeyUp(int key, Sprite sprite) {
				handler.obtainMessage(MSG_ID_KEY_UP, key, 0).sendToTarget();
				handler.obtainMessage(MSG_ID_UPDATE_INFO_TEXT, sprite.toString()).sendToTarget();
			}
			
			@Override
			public void onKeyDown(int key, Sprite sprite) {
				handler.obtainMessage(MSG_ID_KEY_DOWN, key, 0).sendToTarget();
				handler.obtainMessage(MSG_ID_UPDATE_INFO_TEXT, sprite.toString()).sendToTarget();
			}
		});
	}
	
	private void runTool(int index) {
		switch (index) {
		case 0: {
			showFragmentDialog(new ChooserFragment().setOnChooseLitener(new OnChooseLitener() {
				
				@Override
				public void onChoose(Object object) {
					runKeySprite((KeySprite) object);
				}
				
				@Override
				public void onCancel() {
					
				}
			}));
			break;
		}
		}
	}
	
	public void startAppService(String action) {
		startService(new Intent(action).setClassName(this, APP_SERVICE_NAME));
	}
	
	private void entryBackground() {
		startAppService(EmulatorService.ACTION_FOREGROUND);
		
		SdkUtils.getSdk().sendEvent(this, "foreground", EmuUtils.getTimeNow());
	}
	
	private void backFroground() {
		startAppService(EmulatorService.ACTION_BACKGROUND);
		
		SdkUtils.getSdk().sendEvent(this, "background", EmuUtils.getTimeNow());
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQ_SHOWEDIT){
			if(resultCode == 1 && data != null){
				emulator.setEditInputContent(data.getStringExtra("input"));
				emulator.postMrpEvent(MrDefines.MR_DIALOG_EVENT, MrDefines.MR_DIALOG_KEY_OK, 0);
			}else {
				emulator.setEditInputContent(null);
				emulator.postMrpEvent(MrDefines.MR_DIALOG_EVENT, MrDefines.MR_DIALOG_KEY_CANCEL, 0);
			}
		}else if (requestCode == REQ_GET_IMAGE) { //选择图片
			if (resultCode == RESULT_OK && null != data) {
//				Uri selectedImage = data.getData();
//				String[] filePathColumn = { MediaStore.Images.Media.DATA };
//
//				Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
//				cursor.moveToFirst();
//
//				int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
//				String picturePath = cursor.getString(columnIndex);
//				cursor.close();
//				
//				bitmapBg = BitmapFactory.decodeFile(picturePath);
			}
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	////////////////////////////////////////////////
	@SuppressWarnings("deprecation")
	public void createEdit(String title, String content, int type, int max) {
		if(Prefer.fullScnEditor){
			Intent intent = new Intent(this, EditActivity.class);
			intent.putExtra("title", title);
			intent.putExtra("content", content);
			intent.putExtra("type", type);
			intent.putExtra("max", max);
			startActivityForResult(intent, REQ_SHOWEDIT);
		}else {
			Bundle b = new Bundle();
			b.putString("title", title);
			b.putString("content", content);
			b.putInt("type", type);
			b.putInt("max", max);
			showDialog(DLG_EDIT, b);
		}
	}

	/**
	 * 发送短信提示
	 * 
	 * @param text
	 * @param addr
	 */
	public void reqSendSms(final String text, final String addr) {
		AlertDialog dialog = new AlertDialog.Builder(this)
			.setTitle(R.string.hint)
			.setMessage(emulator.getCurMrpAppName()
					+ "请求发送短信：\n"
					+ "地址：" + addr + "\n"
					+ "内容：" + text + "\n")
			.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					SmsManager sms = SmsManager.getDefault();
					List<String> messages = sms.divideMessage(text);
			        for (String message : messages) {
			            sms.sendTextMessage(addr, null, message, 
			            		PendingIntent.getBroadcast(EmulatorActivity.this, 0, new Intent(ACTION_SMS_SENT), 0), 
			            		null);
			        }
				}
			})
			.setNegativeButton(R.string.refused, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					//直接通知底层失败
	                emulator.vm_event(MrDefines.MR_SMS_RESULT, MrDefines.MR_FAILED, 0);
				}
			})
			.setOnCancelListener(new DialogInterface.OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					emulator.vm_event(MrDefines.MR_SMS_RESULT,  MrDefines.MR_SUCCESS, 0);
				}
			})
			.create();
		dialog.setCancelable(false);
//		dialog.setCanceledOnTouchOutside(false);
		dialog.show();
	}
	
	/**
	 * 打电话提示
	 * 
	 * @param number
	 */
	public void reqCallPhone(final String number) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.hint)
			.setMessage(emulator.getCurMrpAppName()
					+ "请求拨打：\n" 
					+ number)
			.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
					EmulatorActivity.this.startActivity(intent);
				}
			})
			.setNegativeButton(R.string.refused, null)
			.create()
			.show();
	}
	
	private final OnKeyEventListener mKeyEventListener = new OnKeyEventListener() {
		
		@Override
		public boolean onKeyUp(int key) {
			if(key == 1025){ //打开菜单
				openOptionsMenu();
			} else {
				Emulator.getInstance().postMrpEvent(MrDefines.MR_KEY_RELEASE, key, 0);
			}
			
			return true;
		}
		
		@Override
		public boolean onKeyDown(int key) {
			if(Prefer.enableKeyVirb)
				vibrator.vibrate(20);
			
			Emulator.getInstance().postMrpEvent(MrDefines.MR_KEY_PRESS, key, 0);
			
			return false;
		}
	};
	
	private WindowManager wm;
    private WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams();
    
	private void floatView() {
        //获取WindowManager
        wm = (WindowManager)getSystemService("window");

         /**
         *以下都是WindowManager.LayoutParams的相关属性
         * 具体用途可参考SDK文档
         */
        wmParams.type = WindowManager.LayoutParams.TYPE_PHONE;   //设置window type
        wmParams.format = PixelFormat.RGBA_8888;   //设置图片格式，效果为背景透明

        //设置Window flag
//        wmParams.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL | LayoutParams.FLAG_NOT_FOCUSABLE;
        /*
         * 下面的flags属性的效果形同“锁定”。
         * 悬浮窗不可触摸，不接受任何事件,同时不影响后面的事件响应。
         wmParams.flags=LayoutParams.FLAG_NOT_TOUCH_MODAL 
                               | LayoutParams.FLAG_NOT_FOCUSABLE
                               | LayoutParams.FLAG_NOT_TOUCHABLE;
        */
        
        
        wmParams.gravity=Gravity.LEFT|Gravity.TOP;   //调整悬浮窗口至左上角
        //以屏幕左上角为原点，设置x、y初始值
        wmParams.x=0;
        wmParams.y=0;
        
        //设置悬浮窗口长宽数据
        wmParams.width=40;
        wmParams.height=40;
    
        //显示myFloatView图像
        wm.addView(emulatorView, wmParams);
    }
}
