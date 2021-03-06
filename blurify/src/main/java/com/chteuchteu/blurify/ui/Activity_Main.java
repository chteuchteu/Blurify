package com.chteuchteu.blurify.ui;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Switch;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.chteuchteu.blurify.R;
import com.chteuchteu.blurify.ast.BlurBackgroundBitmap;
import com.chteuchteu.blurify.ast.OnBlurChange;
import com.chteuchteu.blurify.ast.WallpaperSetter;
import com.chteuchteu.blurify.hlpr.BitmapUtil;
import com.chteuchteu.blurify.hlpr.CustomImageView;
import com.chteuchteu.blurify.hlpr.I18nHelper;
import com.chteuchteu.blurify.hlpr.Util;
import com.edmodo.cropper.CropImageView;

import java.util.Locale;

public class Activity_Main extends BlurifyActivity {
	private Bitmap tmp_original_bitmap;
	public Bitmap little_bitmap_original;
	public Bitmap little_bitmap;

	public boolean computing;

	private SeekBar seekBar;
	private Switch selectiveFocusSwitch;
	private SeekBar selectiveFocusSize;

	public int selFocus_x;
	public int selFocus_y;

	private int aspectRatioX = 0;
	private int aspectRatioY = 0;
	private int state = 0;

	private Activity_Main activity;
	
	private static final int ST_UNKNOWN = 0;
	private static final int ST_CROP = 1;
	private static final int ST_BLUR = 2;

	private WallpaperManager myWallpaperManager;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		super.onContentViewSet();

		activity = this;
		computing = false;

		Button set_wallpaper = (Button) findViewById(R.id.setWallpaper);
		myWallpaperManager = WallpaperManager.getInstance(getApplicationContext());
		aspectRatioX = myWallpaperManager.getDesiredMinimumWidth();
		aspectRatioY = myWallpaperManager.getDesiredMinimumHeight();
		
		state = ST_UNKNOWN;
		
		set_wallpaper.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View actualView) {
				if (little_bitmap != null)
					new WallpaperSetter(activity, myWallpaperManager, little_bitmap).execute();
			}
		});
		
		findViewById(R.id.saveimg).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Util.saveBitmap(context, little_bitmap);
			}
		});
		
		findViewById(R.id.getimg).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View actualView) {
				Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				startActivityForResult(pickPhoto , 1);
			}
		});

		seekBar = (SeekBar)findViewById(R.id.seekBar);
		seekBar.setMax(50);
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) { }
			@Override
			public void onStopTrackingTouch(SeekBar thisSeekBar) {
				if (little_bitmap_original != null) {
					computing = true;
					new OnBlurChange(activity, seekBar, selectiveFocusSize, selectiveFocusSwitch.isChecked()).execute();
				}
			}
		});

		selectiveFocusSize = (SeekBar) findViewById(R.id.selectiveFocusSize);
		selectiveFocusSize.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) { }

			@Override
			public void onStopTrackingTouch(SeekBar thisSeekBar) {
				if (little_bitmap_original != null) {
					computing = true;
					new OnBlurChange(activity, seekBar, selectiveFocusSize, selectiveFocusSwitch.isChecked()).execute();
				}
			}
		});
		selectiveFocusSize.setProgress(50);


		selectiveFocusSwitch = (Switch) findViewById(R.id.selectiveFocusSwitch);
		selectiveFocusSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				findViewById(R.id.selectiveFocusSizeContainer).setVisibility(isChecked ? View.VISIBLE : View.GONE);
				updateContainerPaddingBottom();

				if (little_bitmap_original != null) {
					computing = true;
					new OnBlurChange(activity, seekBar, selectiveFocusSize, isChecked).execute();
				}
			}
		});
		selFocus_x = -1;
		selFocus_y = -1;

		CustomImageView container = (CustomImageView) findViewById(R.id.container);
		container.setOnTouchListener(new CustomImageView.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (!selectiveFocusSwitch.isChecked() || computing) return true;

				if (little_bitmap_original != null) {
					int[] mappedCoord = BitmapUtil.Click.mapCoordinates((int) event.getX(), (int) event.getY(),
							(CustomImageView) findViewById(R.id.container));

					if (mappedCoord == null) // Clicked outside the bitmap
						return true;

					selFocus_x = mappedCoord[0];
					selFocus_y = mappedCoord[1];

					computing = true;
					new OnBlurChange(activity, seekBar, selectiveFocusSize, true).execute();
				}

				return true;
			}
		});

        displayI18nMessage();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			int id = getResources().getIdentifier("config_enableTranslucentDecor", "bool", "android");
			if (id != 0 && getResources().getBoolean(id)) // Translucent available
				findViewById(R.id.buttonsContainer).setPadding(0, 0, 0, Util.getSoftbuttonsbarHeight(this));
		}
		View cropImageView = findViewById(R.id.CropImageView);
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
			params.setMargins(0, 0, 0, Util.getSoftbuttonsbarHeight(this) + findViewById(R.id.buttonsContainer).getHeight());
		else
			params.setMargins(0, 0, 0, findViewById(R.id.buttonsContainer).getHeight());
		params.addRule(RelativeLayout.CENTER_IN_PARENT);
		cropImageView.setLayoutParams(params);
	}
	
	@Override
	public void onBackPressed() {
		if (isAboutShown) {
			closeAbout();
			return;
		}

		switch (state) {
			case ST_UNKNOWN:
				super.onBackPressed();
				break;
			case ST_CROP:
				findViewById(R.id.getimg).setVisibility(View.VISIBLE);
				findViewById(R.id.actions1).setVisibility(View.GONE);
				findViewById(R.id.container).setVisibility(View.GONE);
				findViewById(R.id.CropImageView).setVisibility(View.GONE);
				state = ST_UNKNOWN;
				break;
			case ST_BLUR:
				seekBar.setProgress(0);
				findViewById(R.id.container).setVisibility(View.GONE);
				findViewById(R.id.mask).setVisibility(View.VISIBLE);
				findViewById(R.id.CropImageView).setVisibility(View.VISIBLE);
				findViewById(R.id.blurryBackground).setVisibility(View.VISIBLE);
				findViewById(R.id.blurryBackground_darkMask).setVisibility(View.VISIBLE);
				findViewById(R.id.actions1).setVisibility(View.VISIBLE);
				findViewById(R.id.actions2).setVisibility(View.GONE);
				state = ST_CROP;
				launchCrop(false);
				break;
		}
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
		super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
		
		if (requestCode == 1 && resultCode == RESULT_OK) {
			state = ST_CROP;
			
			seekBar.setProgress(0);
			
			Uri selectedImage = imageReturnedIntent.getData();
			String[] filePathColumn = {MediaStore.Images.Media.DATA};
			
			Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            try {
                cursor.moveToFirst();
            } catch (Exception ex) {
                Toast.makeText(context, getString(R.string.err_import), Toast.LENGTH_SHORT).show();
                return;
            }
			
			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String filePath = cursor.getString(columnIndex);
			cursor.close();
			
			Bitmap b = null;
			
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			
			BitmapFactory.decodeFile(filePath, options);
			
			long totalImagePixels = options.outHeight * options.outWidth;
			
			// Get screen pixels
			int totalScreenPixels;
			if (android.os.Build.VERSION.SDK_INT >= 13) {
				Display display = getWindowManager().getDefaultDisplay();
				Point size = new Point();
				display.getSize(size);
				totalScreenPixels = size.x * size.y;
			} else {
				Display display = getWindowManager().getDefaultDisplay();
				totalScreenPixels = display.getHeight() * display.getWidth();
			}
			
			if (totalScreenPixels > 2048*2048)
				totalScreenPixels = 2048*2048;
			
			if (totalImagePixels > totalScreenPixels) {    
				double factor=(float)totalImagePixels/(float)(totalScreenPixels);
				int sampleSize=(int) Math.pow(2, Math.floor(Math.sqrt(factor)));
				options.inJustDecodeBounds=false;
				options.inSampleSize=sampleSize;
				b = BitmapFactory.decodeFile(filePath, options);
			}
			
			if (b == null) b = BitmapFactory.decodeFile(filePath);
			
			
			if (b != null && b.getConfig() != null) {
				try {
					tmp_original_bitmap = b.copy(b.getConfig(), true);
					BitmapUtil.recycle(b);
					
					findViewById(R.id.getimg).setVisibility(View.GONE);
					findViewById(R.id.actions1).setVisibility(View.VISIBLE);
					launchCrop(true);
					((CustomImageView)findViewById(R.id.container)).setImageBitmap(null);
				} catch (Exception ignored) {
					Toast.makeText(context, getString(R.string.err_import), Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(context, getString(R.string.err_import), Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	// executeBlurBackground : No need to execute blur background when pressing back
	private void launchCrop(boolean executeBlurBackground) {
		findViewById(R.id.mask).setVisibility(View.VISIBLE);
		final CropImageView c = (CropImageView)findViewById(R.id.CropImageView);

		c.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				// Set c margin (avoid it being hidden by Notifs bar, Toolbar, and actions bar
				int marginTop = Util.getStatusBarHeight(context) + toolbar.getHeight();
				int marginBottom = findViewById(R.id.actions1).getHeight() + Util.getSoftbuttonsbarHeight(activity);
				RelativeLayout.LayoutParams layoutParams = c.getLayoutParams() != null ? (RelativeLayout.LayoutParams) c.getLayoutParams()
						: new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
				layoutParams.setMargins(
						layoutParams.leftMargin,
						marginTop,
						layoutParams.rightMargin,
						marginBottom
				);

				Util.removeOnGlobalLayoutListener(c, this);
			}
		});

		//c.setFixedAspectRatio(true);
		c.setAspectRatio(aspectRatioX, aspectRatioY);
		c.setVisibility(View.VISIBLE);
		c.setImageBitmap(tmp_original_bitmap);
		c.invalidate();
		if (executeBlurBackground)
			new BlurBackgroundBitmap(this, tmp_original_bitmap).execute();
		
		findViewById(R.id.crop).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				little_bitmap_original = c.getCroppedImage();
				AlphaAnimation a2 = new AlphaAnimation(1.0f, 0.0f);
				a2.setDuration(500);
				little_bitmap = little_bitmap_original.copy(little_bitmap_original.getConfig(), true);

				CustomImageView container = (CustomImageView) findViewById(R.id.container);
				container.setVisibility(View.VISIBLE);
				container.setImageBitmap(little_bitmap_original);

				findViewById(R.id.mask).startAnimation(a2);
				findViewById(R.id.mask).setVisibility(View.GONE);
				findViewById(R.id.CropImageView).startAnimation(a2);
				findViewById(R.id.CropImageView).setVisibility(View.GONE);

				findViewById(R.id.actions1).setVisibility(View.GONE);
				findViewById(R.id.actions2).setVisibility(View.VISIBLE);
				updateContainerPaddingBottom();
				updateContainer();
				state = ST_BLUR;
			}
		});
		findViewById(R.id.rotate).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				((CropImageView) findViewById(R.id.CropImageView)).rotateImage(90);
			}
		});
	}

	public void updateContainer() { updateContainer(null); }
	public void updateContainer(CustomImageView.AfterNextDrawListener afterNextDrawListener) {
		try {
			CustomImageView container = (CustomImageView)findViewById(R.id.container);
			container.setAfterNextDrawListener(afterNextDrawListener);

			if (little_bitmap != null && !little_bitmap.isRecycled())
				container.setImageBitmap(little_bitmap);
		} catch (Exception ex) { ex.printStackTrace(); }
	}

	public void updateContainerPaddingBottom() {
		final View view = findViewById(R.id.actions2);
		view.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						int paddingBottom = findViewById(R.id.actions2).getHeight();
						View container = findViewById(R.id.containerContainer);
						container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
								container.getPaddingRight(), paddingBottom);

						Util.removeOnGlobalLayoutListener(view, this);
					}
				}
		);
	}

    public void displayI18nMessage() {
        // Only display the alertDialog if the device language is not handled
        String deviceLanguage = Locale.getDefault().getLanguage();

        if (!I18nHelper.isLanguageSupported(deviceLanguage)
                && !Util.Pref.getBool(this, Util.Pref.PrefKeys.I18NDialogShown, false)) {
            if (this.isFinishing())
                return;

            new AlertDialogWrapper.Builder(this)
                    .setMessage(R.string.i18n_message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/chteuchteu/Blurify#how-to-help-translate-blurify")));
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();

            Util.Pref.setBool(this, Util.Pref.PrefKeys.I18NDialogShown, true);
        }
    }
}
