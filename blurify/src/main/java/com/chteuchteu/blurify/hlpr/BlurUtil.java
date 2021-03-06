package com.chteuchteu.blurify.hlpr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

import com.chteuchteu.blurify.Foofy;
import com.chteuchteu.blurify.R;
import com.chteuchteu.blurify.ui.Activity_Main;

public class BlurUtil {
	/**
	 * Blurs a bitmap using RenderScript (source : http://trickyandroid.com/advanced-blurring-techniques/,
	 *  https://plus.google.com/+MarioViviani/posts/fhuzYkji9zz)
	 * @param originalBitmap Original bitmap to be blurred (not altered)
	 * @param radius Blur amount
	 * @return Blurred bitmap
	 */
	public static Bitmap renderScriptBlur(Bitmap originalBitmap, float radius) {
		Bitmap outBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(),
				Bitmap.Config.ARGB_8888);

		RenderScript rs = Foofy.getInstance().getRenderScriptContext();
		ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
		Allocation allIn = Allocation.createFromBitmap(rs, originalBitmap);
		Allocation allOut = Allocation.createFromBitmap(rs, outBitmap);
		blurScript.setRadius(radius > 25 ? 25 : radius);
		blurScript.setInput(allIn);
		blurScript.forEach(allOut);
		allOut.copyTo(outBitmap);

		//rs.destroy();

		if (radius > 25)
			return renderScriptBlur(outBitmap, radius-25);
		else
			return outBitmap;
	}

	public static Bitmap maskBlur(Activity_Main activity, Bitmap source, float blurAmount, float maskSize) {
		int maskPosX = activity.selFocus_x;
		int maskPosY = activity.selFocus_y;

		// Get the simple mask drawable as a bitmap
		Bitmap mask = BitmapUtil.getDrawableAsBitmap(activity, R.drawable.mask);

		// Resize mask
		if (maskSize == 0)
			maskSize = 0.01f;
		if (maskSize != 1) {
			Foofy.log("Resizing mask (" + maskSize + ") => " + ((int) (mask.getWidth() * maskSize)) + "x" + (int) (mask.getWidth() * maskSize));
			mask = Bitmap.createScaledBitmap(mask,
					(int) (mask.getWidth() * maskSize),
					(int) (mask.getHeight() * maskSize), false);
		}

		if (maskPosX == -1 || maskPosY == -1) {
			maskPosX = source.getWidth() / 2;
			maskPosY = source.getHeight() / 2;
		}
		maskPosX -= mask.getWidth() / 2;
		maskPosY -= mask.getHeight() / 2;

		// Get the sharp part of the picture
		Bitmap sharp = BitmapUtil.applyMask(source, mask, maskPosX, maskPosY);

		Bitmap blurryBackground = renderScriptBlur(source, blurAmount);

		// Put all those together
		Canvas canvas = new Canvas(blurryBackground);
		Paint paint = new Paint();
		canvas.drawBitmap(sharp, maskPosX, maskPosY, paint);

		return blurryBackground;
	}
}
