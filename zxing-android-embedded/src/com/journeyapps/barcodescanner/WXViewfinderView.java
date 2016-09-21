/*
 * Copyright (C) 2008 ZXing authors
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

package com.journeyapps.barcodescanner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.client.android.R;


/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class WXViewfinderView extends View {
    protected static final String TAG = WXViewfinderView.class.getSimpleName();

    protected Paint maskPaint;
    protected Paint borderPaint;
    protected Paint cornerPaint;
    protected int maskColor;
    protected int laserColor;
    protected int scannerAlpha;
    protected CameraPreview cameraPreview;
    protected int cornerColor;   //角落颜色
    protected int borderColor;  //边框线颜色
    protected float borderWidth;  //边框线粗细
    protected float cornerWidth;  //角落的线的宽度
    protected float cornerLength;  //角落的线的长度
    private final long animationDelay = 5L;
    private float speed = 5;
    private int laserPadding = 10;

    protected Paint textPaint;

    int offset = 0;

    String statusText;

    float statusTextPadding = 32f;

    int statusTextColor = Color.parseColor("#b6b0b0");

    Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    int statusTextSize;

    // Cache the framingRect and previewFramingRect, so that we can still draw it after the preview
    // stopped.
    protected Rect framingRect;
    protected Rect previewFramingRect;


    // This constructor is used when the class is built from an XML resource.
    public WXViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();

        // Get setted attributes on view
        TypedArray attributes = getContext().obtainStyledAttributes(attrs, R.styleable.zxing_WXViewfinderView);

        this.maskColor = attributes.getColor(R.styleable.zxing_WXViewfinderView_zxing_mask_color,
                Color.parseColor("#8c000000"));
        this.laserColor = attributes.getColor(R.styleable.zxing_WXViewfinderView_zxing_laser_color,
                Color.parseColor("#d90000"));
        this.cornerColor = attributes.getColor(R.styleable.zxing_WXViewfinderView_zxing_corner_color,
                Color.parseColor("#d90000"));
        this.borderColor = attributes.getColor(R.styleable.zxing_WXViewfinderView_zxing_border_color,
                Color.parseColor("#dfdfdf"));
        this.borderWidth = attributes.getDimension(R.styleable.zxing_WXViewfinderView_zxing_border_width, 1);
        this.cornerWidth = attributes.getDimension(R.styleable.zxing_WXViewfinderView_zxing_corner_width, 3);
        this.cornerLength = attributes.getDimension(R.styleable.zxing_WXViewfinderView_zxing_corner_length, 12);
        this.statusText = attributes.getString(R.styleable.zxing_WXViewfinderView_zxing_status_text);
        if(statusText == null){
          statusText  = "将二维码入框内，即可自动扫描";
        }
        this.statusTextColor = attributes.getColor(R.styleable.zxing_WXViewfinderView_zxing_status_color, Color.parseColor("#b6b0b0"));
        this.statusTextPadding = attributes.getDimension(R.styleable.zxing_WXViewfinderView_zxing_status_padding, 32);
        this.statusTextSize = attributes.getDimensionPixelSize(R.styleable.zxing_WXViewfinderView_zxing_status_size, 24);
        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setColor(maskColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setColor(borderColor);
        cornerPaint.setStrokeWidth(cornerWidth);
        cornerPaint.setColor(cornerColor);
        cornerPaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextSize(statusTextSize);
        textPaint.setColor(statusTextColor);
        attributes.recycle();
    }

    public void setCameraPreview(CameraPreview view) {
        this.cameraPreview = view;
        view.addStateListener(new CameraPreview.StateListener() {
            @Override
            public void previewSized() {
                refreshSizes();
                invalidate();
            }

            @Override
            public void previewStarted() {

            }

            @Override
            public void previewStopped() {

            }

            @Override
            public void cameraError(Exception error) {

            }
        });
    }

    protected void refreshSizes() {
        if(cameraPreview == null) {
            return;
        }
        Rect framingRect = cameraPreview.getFramingRect();
        Rect previewFramingRect = cameraPreview.getPreviewFramingRect();
        if(framingRect != null && previewFramingRect != null) {
            this.framingRect = framingRect;
            this.previewFramingRect = previewFramingRect;
        }
    }


    public void setStatusText(String statusText){
        this.statusText = statusText;
        invalidate();
    }


    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        refreshSizes();
        if (framingRect == null || previewFramingRect == null) {
            return;
        }

        Rect frame = framingRect;
        Rect previewFrame = previewFramingRect;

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        maskPaint.setColor(maskColor);
        canvas.drawRect(0, 0, width, frame.top, maskPaint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, maskPaint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, maskPaint);
        canvas.drawRect(0, frame.bottom + 1, width, height, maskPaint);
        //drawable the border
        canvas.drawRect(frame.left+1, frame.top+1, frame.right, frame.bottom, borderPaint);
        int halfWidth = (int) (cornerWidth / 2);
        //draw four corner
        Path corner1 = new Path();
        corner1.moveTo(frame.left , frame.top  + cornerLength);
        corner1.lineTo(frame.left , frame.top );
        corner1.lineTo(frame.left  + cornerLength, frame.top );
        Matrix translate1 = new Matrix();
        translate1.setTranslate(halfWidth, halfWidth);
        corner1.transform(translate1);
        canvas.drawPath(corner1, cornerPaint);

        Path corner2 = new Path();
        corner2.moveTo(frame.right +1 - cornerLength, frame.top);
        corner2.lineTo(frame.right + 1, frame.top);
        corner2.lineTo(frame.right + 1, frame.top + cornerLength);
        Matrix translate2 = new Matrix();
        translate2.setTranslate(-halfWidth, halfWidth);
        corner2.transform(translate2);
        canvas.drawPath(corner2, cornerPaint);

        Path corner3 = new Path();
        corner3.moveTo(frame.left , frame.bottom +1 - cornerLength);
        corner3.lineTo(frame.left , frame.bottom + 1);
        corner3.lineTo(frame.left  + cornerLength, frame.bottom + 1);
        Matrix translate3 = new Matrix();
        translate3.setTranslate(halfWidth, -halfWidth);
        corner3.transform(translate3);
        canvas.drawPath(corner3, cornerPaint);

        Path corner4 = new Path();
        corner4.moveTo(frame.right +1 - cornerLength, frame.bottom +1);
        corner4.lineTo(frame.right + 1, frame.bottom + 1);
        corner4.lineTo(frame.right + 1, frame.bottom +1 - cornerLength);
        Matrix translate4 = new Matrix();
        translate4.setTranslate(-halfWidth, -halfWidth);
        corner4.transform(translate4);
        canvas.drawPath(corner4, cornerPaint);

        offset += speed;
        if(offset >= frame.bottom - frame.top){
            offset = 0;
        }
        Rect rect = new Rect();
        rect.left = frame.left + 1 + laserPadding;
        rect.top = frame.top + 1 + offset;
        rect.right = frame.right - laserPadding;
        rect.bottom = frame.top + 1 + offset + 3;


        Bitmap laserBitmap = ((BitmapDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.scan_laser, null)).getBitmap();
        canvas.drawBitmap(laserBitmap, null, rect, linePaint);

        textPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText(statusText, (frame.right + frame.left) /2, frame.bottom + statusTextPadding + statusTextSize, textPaint);

        postInvalidateDelayed(animationDelay, frame.left, frame.top, frame.right, frame.bottom);

    }


}
