package com.hit.wi.t9.effect;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import com.hit.wi.t9.R;
import com.hit.wi.t9.values.Global;

/**
 * Created by Administrator on 2015/5/22.
 */
public class KeyBoardTouchEffect implements KeyboardTouchEffectInterface {
    /**
     * 键盘震动
     */
    VibrateManager vibrateManager;
    /**
     * 键盘声音
     */
    SoundManager soundManager;

    public KeyBoardTouchEffect(Context context) {
        vibrateManager = new VibrateManager(context);
        soundManager = new SoundManager(context);
    }

    public void loadSetting(SharedPreferences sharedPreferences) {
        vibrateManager.setStrength(sharedPreferences.getInt("VIBRATOR", 0));
        soundManager.setVolume(sharedPreferences.getInt("SOUND", 0));
        soundManager.setSoundType(Integer.parseInt(sharedPreferences.getString("KEY_SOUND_EFFECT_SELECTOR", "1")));
        soundManager.setSlideVolume(sharedPreferences.getInt("SLIDE_PIN_VOLUME", 0));
    }

    public final void onPressEffect() {
        vibrateManager.Vibrate();
        soundManager.playSound();
    }

    public final void onSlideEffect() {
        soundManager.playSwipeSound();
    }

    public void onTouchEffect(View v, int action, int touchdown_color, int normal_color) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                onPressEffect();
                v.setBackgroundColor(touchdown_color);
                break;
            case MotionEvent.ACTION_MOVE:
                onSlideEffect();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                v.setBackgroundColor(normal_color);
                break;
        }
        v.getBackground().setAlpha((int) (Global.mCurrentAlpha * 255));
    }

    public void animEffect(View v,int action,Context context){
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                v.startAnimation(AnimationUtils.loadAnimation(context, R.anim.touch_down));
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                v.startAnimation(AnimationUtils.loadAnimation(context, R.anim.touch_down2));
                break;
        }
    }

    public void onTouchEffectWithAnim(View v, int action, int touchdown_color, int normal_color, Context context) {
        animEffect(v,action,context);
        onTouchEffect(v, action, touchdown_color, normal_color);
    }

    public void onTouchEffectWithAnimForQK(View v, int action, int touchdown_color, int normal_color, Context context){
        animEffect(v,action,context);
        onTouchEffect(v.findViewById(R.id.main_text),action,touchdown_color,normal_color);
        onTouchEffect(v.findViewById(R.id.predict_text),action,touchdown_color,normal_color);
    }

}
