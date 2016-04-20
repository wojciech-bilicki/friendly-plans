/*
 * Copyright (c) 2016. Wydział Elektroniki, Telekomunikacji i Informatyki, Politechnika Gdańska
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 3 of the License, or   (at your option) any later version.
 *
 * Copy of GNU General Public License is available at http://www.gnu.org/licenses/gpl-3.0.html
 */
package com.przyjaznyplanDisplayer.Utils;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.przyjaznyplan.R;
import com.przyjaznyplan.models.Activity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;


public class GridCustomAdapter extends ArrayAdapter<Activity> {
    private List<Activity> objects;
    private Hashtable<String,Bitmap> bitmaps;
    private LayoutInflater mInflater;
    private int mViewResourceId;
    private int backgroundColor;
    private int textColor;
    private Bitmap mPlaceHolderBitmap;
    private Context ctx;
    public int selectedAc;
    private LruCache<String, Bitmap> mMemoryCache;

    public GridCustomAdapter(Context ctx, int textViewResourceId, int id, List<Activity> objects, int selectedAc) {
        super(ctx, textViewResourceId, objects);
        mInflater = (LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.objects = objects;
        this.ctx = ctx;
        mViewResourceId = textViewResourceId;
        TypedArray array = ctx.getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
        });
        backgroundColor = array.getColor(0, Color.WHITE);
        textColor = array.getColor(1, Color.BLACK);
        array.recycle();
        mPlaceHolderBitmap = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.t1);
        this.selectedAc = selectedAc;
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }


    @Override
    public int getCount() {
        return objects.size();
    }

    @Override
    public Activity getItem(int i) {
        return objects.get(i);
    }

    @Override
    public long getItemId(int i) {
        System.out.println("LOGID:"+i+"UUID:"+objects.get(i).getId());
        return UUID.fromString(objects.get(i).getId()).getMostSignificantBits();
        //return objects.get(i).getId();
    }

    private static class ViewHolder {
        public final TextView label;
        public final ImageView sound;
        public final ImageView activityImage;
        public final ImageView activityTimer;
        public ViewHolder(
                TextView label,
                ImageView sound,
                ImageView activityImage,
                ImageView activityTimer,
                Activity activity) {
            this.label=label;
            this.sound=sound;
            this.activityImage=activityImage;
            this.activityTimer=activityTimer;
        }
    }

    class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private String path;

        public BitmapWorkerTask(ImageView imageView, String path) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);
            this.path = path;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Integer... params) {
            final Bitmap bitmap = decodeSampledBitmapFromResource(path, 0, 100, 100);
            addBitmapToMemoryCache(String.valueOf(path), bitmap);
            return bitmap;
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask bitmapWorkerTask =
                        getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask && imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public static boolean cancelPotentialWork(String path, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final String bitmapData = bitmapWorkerTask.path;
            // If bitmapData is not yet set or it differs from the new data
            if (bitmapData == null || bitmapData.equals("") || bitmapData!=path) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    public void loadBitmap(String path, ImageView imageView) {
        final String imageKey = path;

        final Bitmap bitmap = getBitmapFromMemCache(imageKey);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            if (cancelPotentialWork(path, imageView)) {
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView,path);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(ctx.getResources(), mPlaceHolderBitmap, task);
                imageView.setImageDrawable(asyncDrawable);
                task.execute();
            }
        }
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(String path, int resId,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }


    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    @Override
    public View getView(int position, View v, ViewGroup viewGroup) {

        TextView label;
        ImageView sound;
        ImageView activityImage;
        ImageView activityTimer;

        Activity activity = objects.get(position);

        if(v==null){
            v = mInflater.inflate(mViewResourceId, null);
            label = (TextView) v.findViewById(R.id.label);
            sound=(ImageView)(v.findViewById(R.id.con3));
            activityImage=(ImageView)(v.findViewById(R.id.con));
            activityTimer=(ImageView)(v.findViewById(R.id.con2));
            v.setTag(new ViewHolder(label,sound,activityImage,activityTimer, activity));
        }
        else{
            ViewHolder vh=(ViewHolder)v.getTag();
            label = vh.label;
            sound= vh.sound;
            activityImage=vh.activityImage;
            activityTimer=vh.activityTimer;
        }

        //v = mInflater.inflate(mViewResourceId, null);

        if (activity != null) {
            if (label != null){
                label.setText(activity.getTitle());
                label.setTextColor(Color.BLACK);
                if(activity.getStatus().equals(Activity.ActivityStatus.FINISHED.toString())){
                    label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    ((View)(label.getParent())).setBackgroundColor(Color.LTGRAY);
                }
                else{
                    label.setPaintFlags(label.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    if(position == selectedAc){
                        ((View)(label.getParent())).setBackgroundColor(Color.YELLOW);
                    } else {
                        ((View)(label.getParent())).setBackgroundColor(Color.WHITE);
                    }

                }

            }
            if (sound != null){
                if(activity.getAudioPath()==null || activity.getAudioPath().equals("")){
                    sound.setVisibility(View.INVISIBLE);
                }else{
                    sound.setVisibility(View.VISIBLE);
                    sound.setTag(activity.getAudioPath());
                }
            }
            if(activityImage != null){
                activityImage.setVisibility(View.INVISIBLE);
                if(!(activity.getIconPath()==null || activity.getIconPath().equals(""))){
                    try {
                        //Bitmap bmp = BitmapFactory.decodeFile(activity.getIconPath(),options);
                        //Bitmap scaledBmp = Bitmap.createScaledBitmap(bmp, 100, 100, false);
                        //activityImage.setImageBitmap(
                        //      decodeSampledBitmapFromResource(activity.getIconPath(), 0, 100, 100));
                        loadBitmap(activity.getIconPath(),activityImage);
                        activityImage.setVisibility(View.VISIBLE);
                    }catch(Exception e){
                    }
                }
            }
            if (activityTimer != null){
                if(activity.getTime()==0){
                    activityTimer.setVisibility(View.INVISIBLE);
                }else{
                    activityTimer.setVisibility(View.VISIBLE);
                }
            }
        }
        return v;
    }
}
