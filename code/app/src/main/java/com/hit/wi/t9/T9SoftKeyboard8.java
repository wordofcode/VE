package com.hit.wi.t9;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.*;

import com.hit.wi.jni.WIInputMethodNK;
import com.hit.wi.jni.WIInputMethod;
import com.hit.wi.t9.Interfaces.T9SoftKeyboard8Interface;
import com.hit.wi.t9.datastruct.InputAction;
import com.hit.wi.t9.effect.KeyBoardTouchEffect;
import com.hit.wi.t9.functions.GenerateMessage;
import com.hit.wi.jni.NKInitDictFile;
import com.hit.wi.jni.InitInputParam;
import com.hit.wi.t9.functions.SharedPreferenceManager;
import com.hit.wi.t9.functions.SymbolsManager;
import com.hit.wi.t9.values.Global;
import com.hit.wi.t9.values.SkinInfoManager;
import com.hit.wi.t9.view.LightViewManager;
import com.hit.wi.t9.view.QuickButton;
import com.hit.wi.t9.view.SetKeyboardSizeView;
import com.hit.wi.t9.view.SetKeyboardSizeView.OnChangeListener;
import com.hit.wi.t9.view.SetKeyboardSizeView.SettingType;
import com.hit.wi.t9.viewGroups.*;
import com.umeng.analytics.MobclickAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public final class T9SoftKeyboard8 extends InputMethodService implements T9SoftKeyboard8Interface {

    /**
     * 功能：加载输入法内核链接库
     * 调用时机：该输入法类加载时调用
     */
    static {
        System.loadLibrary("WIIM_NK");
        System.loadLibrary("WIIM");
    }

    /**
     * 屏幕方向
     */
    private String orientation;

    /**
     * 横屏状态字符
     */
    private final String ORI_HOR = "_H";

    /**
     * 竖屏状态字符
     */
    private final String ORI_VER = "_V";

    /**
     * 设置键盘大小界面是否显示
     */
    boolean mSetKeyboardSizeViewOn = false;

    /**
     * 是全拼还是Emoji
     */
    public String mQPOrEmoji = Global.QUANPIN;

    /**
     * 中文键盘类型
     */
    int zhKeyboard;

    /**
     * 屏幕宽度
     */
    int mScreenWidth;

    /**
     * 屏幕高度
     */
    int mScreenHeight;

    /**
     * 状态栏高度
     */
    int mStatusBarHeight;

    public String[] mFuncKeyboardText;

    /**
     * 浮动窗口状态标识符
     */
    private static final int DISABLE_LAYOUTPARAMS_FLAG = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    private static final int LOCK_LAYOUTPARAMS_FLAG = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
    private static final int ABLE_LAYOUTPARAMS_FLAG = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    //默认是FLAG_NOT_FOCUSABLE，否则会抢夺输入框的焦点导致键盘收回

    //透明度有关
//    public static final int SET_ALPHA_VIEW_DESTROY = -803;

    private final int MSG_HIDE = 0;
    public final int MSG_REPEAT = 1;
    public final int MSG_SEND_TO_KERNEL = 2;
    public final int QP_MSG_SEND_TO_KERNEL = 3;
    private final int MSG_CHOOSE_WORD = 4;
    private final int MSG_HEART = 5;
    public final int MSG_DOUBLE_CLICK_REFRESH = 7;
    private final int MSG_KERNEL_CLEAN = 8;
    public final int MSG_LAZY_LOAD_CANDIDATE = 6;

    private static final int REPEAT_INTERVAL = 50; // 重复按键的时间
    public static final int REPEAT_START_DELAY = 400;// 重复按键

    //屏幕信息
    private int DEFAULT_FULL_WIDTH;
    private int DEFAULT_FULL_WIDTH_X;
    private String FULL_WIDTH_S = "FULL_WIDTH";
    private String FULL_WIDTH_X_S = "FULL_WIDTH_X";
    private int DEFAULT_KEYBOARD_X;
    private int DEFAULT_KEYBOARD_Y;
    private int DEFAULT_KEYBOARD_WIDTH;
    private int DEFAULT_KEYBOARD_HEIGHT;

    private final String KEYBOARD_X_S = "KEYBOARD_X";
    private final String KEYBOARD_Y_S = "KEYBOARD_Y";
    private final String KEYBOARD_WIDTH_S = "KEYBOARD_WIDTH";
    private final String KEYBOARD_HEIGHT_S = "KEYBOARD_HEIGHT";

    private boolean mWindowShown = false;
    /**
     * 左手模式
     */
    private boolean mLeftHand = false;
    private boolean show = true;
    /**
     * 光标位置处理
     */
    private int mNewStart;
    private int mNewEnd;
    private int mCandicateStart;
    private int mCandicateEnd;

    public int keyboardWidth = 0;
    private int keyboardHeight = 0;
    private int standardVerticalGapDistance = 10;
    private int standardHorizantalGapDistance = 0;
    private int maxFreeKernelTime = 60;

    private InitInputParam initInputParam;
    public Typeface mTypeface;

    public LinearLayout keyboardLayout;
    public WindowManager.LayoutParams keyboardParams = new WindowManager.LayoutParams();
    public LinearLayout secondLayerLayout;
    private LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    public ViewGroup mInputViewGG;
    public LinearLayout.LayoutParams mGGParams = new LinearLayout.LayoutParams(0, 0);

    public WindowManager.LayoutParams mSetKeyboardSizeParams = new WindowManager.LayoutParams();
    public SetKeyboardSizeView mSetKeyboardSizeView;
    public EditText preEdit;
    private LinearLayout.LayoutParams preEditLayoutParams;
    private QuickButton largeCandidateButton;

    private Listeners listeners = new Listeners();
    public KeyBoardSwitcherC keyBoardSwitcher = new KeyBoardSwitcherC();
    public SkinUpdateC skinUpdateC = new SkinUpdateC();
    public ScreenInfoC screenInfoC = new ScreenInfoC();
    public TransparencyHandle transparencyHandle = new TransparencyHandle();
    public ViewManagerC viewManagerC = new ViewManagerC();
    public FunctionsC functionsC = new FunctionsC();
    public ViewSizeUpdateC viewSizeUpdate = new ViewSizeUpdateC();

    public QKInputViewGroups qkInputViewGroups;
    public SpecialSymbolChooseViewGroup specialSymbolChooseViewGroup;
    public FunctionViewGroup functionViewGroup;
    public QuickSymbolViewGroup quickSymbolViewGroup;
    public PreFixViewGroup preFixViewGroup;
    public BottomBarViewGroup bottomBarViewGroup;
    public QKCandidatesViewGroup qkCandidatesViewGroup;
    public T9InputViewGroup t9InputViewGroup;
    public LightViewManager lightViewManager;

    public SymbolsManager symbolsManager;
    public KeyBoardTouchEffect keyBoardTouchEffect;
    public SkinInfoManager skinInfoManager;
    private Resources res;

    /**
     * 处理消息事件
     */
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_HIDE:
                    if (Global.keyboardRestTimeCount > 7) {
                        transparencyHandle.UpAlpha();
                        Global.keyboardRestTimeCount = 0;
                    } else
                        Global.keyboardRestTimeCount++;
                    mHandler.removeMessages(MSG_HIDE);
                    mHandler.sendEmptyMessageDelayed(MSG_HIDE, Global.metaRefreshTime);
                    break;
                case MSG_REPEAT:
                    DeleteLast();
                    sendEmptyMessageDelayed(MSG_REPEAT, REPEAT_INTERVAL);
                    break;
                case MSG_SEND_TO_KERNEL:
                    WIInputMethodNK.GetAllWords((String) msg.obj);
                     refreshDisplay();
                    t9InputViewGroup.updateFirstKeyText();
                    break;
                case QP_MSG_SEND_TO_KERNEL:
                    innerEdit((String) msg.obj, false);
                    break;
                case MSG_CHOOSE_WORD:
                    ChooseWord(msg.arg1);
                    break;
                case MSG_LAZY_LOAD_CANDIDATE:
                    qkCandidatesViewGroup.setCandidates((List<String>) msg.obj);
                    break;
                case MSG_HEART:
                    transparencyHandle.HeartAlpha();
                    break;
                case MSG_DOUBLE_CLICK_REFRESH:
                    if (bottomBarViewGroup != null) {
                        bottomBarViewGroup.expressionFlag = 0;
                    }
                    mHandler.removeMessages(MSG_DOUBLE_CLICK_REFRESH);
                    mHandler.sendEmptyMessageDelayed(MSG_DOUBLE_CLICK_REFRESH, 3 * Global.metaRefreshTime);
                    break;
                case MSG_KERNEL_CLEAN:
//                    try {
//                        WIInputMethod.FreeIme();
//                        WIInputMethodNK.FreeIme();
//                    } catch (Exception e){
//                    }
                    mHandler.removeMessages(MSG_KERNEL_CLEAN);
//                    mHandler.sendEmptyMessageDelayed(MSG_KERNEL_CLEAN, maxFreeKernelTime * Global.metaRefreshTime);
                    break;
            }

            super.handleMessage(msg);
        }
    };

    /**
     * 功能：，首先加载输入法词典，并初始化内核参数，并对界面元素进行创建
     * 调用时机：输入法服务创建时调用
     */
    @Override
    public void onCreate() {
        /*
         * 加载内核
		 */
        Log.i("Test","onCreate");
        NKInitDictFile.NKInitWiDict(this);
        initInputParam = new InitInputParam();

		/*
         * 初始化SharedPreferences数据
		 */
        SharedPreferenceManager.initSharedPreferencesData(this);//初始化系统默认的点划设置

        iniComponent();

        GenerateMessage gm = new GenerateMessage(this, 1);
        gm.generate();
        screenInfoC.refreshScreenInfo();
        KeyBoardCreate keyBoardCreate = new KeyBoardCreate();
        keyBoardCreate.createKeyboard();
        transparencyHandle.startAutoDownAlpha();
        super.onCreate();
    }

    private void iniComponent() {
        res = getResources();
        keyBoardTouchEffect = new KeyBoardTouchEffect(this);
        specialSymbolChooseViewGroup = new SpecialSymbolChooseViewGroup();
        functionViewGroup = new FunctionViewGroup();
        quickSymbolViewGroup = new QuickSymbolViewGroup();
        preFixViewGroup = new PreFixViewGroup();
        bottomBarViewGroup = new BottomBarViewGroup();
        qkCandidatesViewGroup = new QKCandidatesViewGroup();
        qkInputViewGroups = new QKInputViewGroups();
        t9InputViewGroup = new T9InputViewGroup();
        preEdit = new EditText(this);
        lightViewManager = new LightViewManager();

        secondLayerLayout = new LinearLayout(this);
        keyboardLayout = new LinearLayout(this);
        keyboardLayout.setOrientation(LinearLayout.VERTICAL);
        skinInfoManager = SkinInfoManager.getSkinInfoManagerInstance();
        symbolsManager = new SymbolsManager(this);
    }

    /**
     * 功能：钩子函数，在配置改变时调用会被系统调用，可以在此针对不同的屏幕方向，加载相应的配置文件
     * 调用时机：屏幕旋转方向时调用
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        screenInfoC.refreshScreenInfo();
        screenInfoC.LoadKeyboardSizeInfoFromSharedPreference();

        viewSizeUpdate.updateViewSizeAndPosition();

        if (mWindowShown) {
            updateWindowManager();
        }
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        this.mNewStart = newSelStart;
        this.mNewEnd = newSelEnd;
        this.mCandicateStart = candidatesStart;
        this.mCandicateEnd = candidatesEnd;
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
    }

    /**
     * 功能：服务销毁时调用的钩子函数，可以在此释放内存
     * 调用时机：服务结束时调用
     */
    @Override
    public void onDestroy() {
        Log.i("Test","onDestroy");
        viewManagerC.removeInputView();
        lightViewManager.removeView();
        WIInputMethodNK.CLeanKernel();
        WIInputMethod.ResetWiIme(InitInputParam.RESET);
        WIInputMethodNK.FreeIme();
        WIInputMethod.FreeIme();
        super.onDestroy();
    }

    /**
     * 功能：编辑功能，负责向内核传递字符，句内编辑也是在这里做
     * 调用时机：{@link #mHandler}处理{@link #MSG_SEND_TO_KERNEL}时调用
     *
     * @param s 向内核传入的字符,delete 是否删除操作
     */
    public boolean innerEdit(String s, boolean delete) {
        if (Global.currentKeyboard != Global.KEYBOARD_QP && Global.currentKeyboard != Global.KEYBOARD_EN) return false;
        mQPOrEmoji = Global.QUANPIN;
        final int selectionStart = Math.min(mNewStart, mNewEnd);
        final int selectionEnd = Math.max(mNewStart, mNewEnd);
        final int candicateStart = Math.min(mCandicateStart, mCandicateEnd);
        final int candicateEnd = Math.max(mCandicateStart, mCandicateEnd);
        if (delete) {
            if (selectionStart <= candicateStart || selectionStart > candicateEnd) {
                this.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                return true;
            }
        }

        if (candicateEnd > selectionStart) {
            final InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                //开始处理真正的句内编辑
                final String candicateString = WIInputMethod.GetWordsPinyin(0);
                if (candicateString.length() == candicateEnd - candicateStart) {
                    //切割字符串
                    final int isDel = delete && selectionStart == selectionEnd ? 1 : 0;
                    String sBegin = selectionStart > candicateStart ? candicateString.substring(0, selectionStart - candicateStart - isDel) : "";
                    sBegin = sBegin.replace("'", "") + s;
                    String sEnd = selectionEnd <= candicateStart ? candicateString : (selectionEnd < candicateEnd ? candicateString.substring(selectionEnd - candicateStart).replace("'", "") : "");
                    int i, j;
                    if (sBegin.length() > 0 && Character.getType(sBegin.charAt(0)) != Character.OTHER_LETTER) {
                        final String sylla = sBegin + sEnd;
                        WIInputMethod.CLeanKernel();
                        WIInputMethod.GetAllWords(sylla);
                        refreshDisplay();
                        qkInputViewGroups.refreshQKKeyboardPredict();
                        String r = WIInputMethod.GetWordsPinyin(0);
                        for (i = 0, j = 0; i < sBegin.length() && j < r.length(); i++) {
                            if (r.charAt(j) == '\'')
                                j++;
                            j++;
                        }
                        ic.setComposingText(r, 1);
                        ic.setSelection(candicateStart + j, candicateStart + j);
                        return true;
                    } else if ((sBegin.length() == 0) && (sEnd.length() > 0 ? Character.getType(sEnd.charAt(0)) != Character.OTHER_LETTER : true)) {
                        WIInputMethod.CLeanKernel();
                        WIInputMethod.GetAllWords(sEnd);
                        qkInputViewGroups.refreshQKKeyboardPredict();
                        refreshDisplay();
                        ic.setSelection(0, 0);
                        //未上屏字符中有中文
                    } else {
                        for (i = 0; i < sBegin.length() && Character.getType(sBegin.charAt(i)) == Character.OTHER_LETTER; i++)
                            ;
                        // 光标前是汉字
                        if (i == sBegin.length() || (i == sBegin.length() - 1 && !delete)) {
                            for (j = 0; j < sEnd.length()
                                    && Character.getType(sEnd.charAt(j)) == Character.OTHER_LETTER; j++)
                                ;
                            if (j != 0) {
                                if (!delete) {
                                    WIInputMethod.GetAllWords(s);
                                    qkInputViewGroups.refreshQKKeyboardPredict();
                                    refreshDisplay();
                                    return true;
                                }
                            }
                            if (delete) {
                                ic.commitText(sBegin, 1);
                                ic.commitText(sEnd.substring(0, j), 1);
                                final String sylla = sEnd.substring(j);
                                WIInputMethod.CLeanKernel();
                                WIInputMethod.GetAllWords(sylla);
                                qkInputViewGroups.refreshQKKeyboardPredict();
                                refreshDisplay();
                                String r = WIInputMethod.GetWordsPinyin(0);
                                ic.setComposingText(r, 1);
                                ic.setSelection(candicateStart + i + j, candicateStart + i + j);
                                return true;
                            }
                            if (!delete && j == 0) {
                                if (sBegin.length() > 0) {
                                    ic.commitText(sBegin.substring(0, sBegin.length() - 1), 1);
                                    WIInputMethod.CLeanKernel();
                                    WIInputMethod.GetAllWords(s + sEnd);
                                    qkInputViewGroups.refreshQKKeyboardPredict();
                                    refreshDisplay();
                                    ic.setSelection(mCandicateStart + 1, mCandicateStart + 1);
                                }
                            }
                        } else {
                            // 光标前不是汉字
                            ic.commitText(sBegin.substring(0, i), 1);
                            final String sylla = sBegin.substring(i) + sEnd;
                            WIInputMethod.CLeanKernel();
                            WIInputMethod.GetAllWords(sylla);
                            qkInputViewGroups.refreshQKKeyboardPredict();
                            refreshDisplay();
                            final String r = WIInputMethod.GetWordsPinyin(0);
                            int k;
                            for (k = i, j = 0; k < sBegin.length()
                                    && j < r.length(); k++) {
                                if (r.charAt(j) == '\'')
                                    j++;
                                j++;
                            }
                            ic.setComposingText(r, 1);
                            ic.setSelection(candicateStart + i + j, candicateStart + i + j);
                        }
                    }
                }
            }
        } else {
            if (delete) {
                WIInputMethod.DeleteAction();
            } else {
                WIInputMethod.GetAllWords((String) s);
            }
            qkInputViewGroups.refreshQKKeyboardPredict();
            refreshDisplay();
        }
        return true;
    }

    /**
     * 功能：更新键盘的尺寸视图
     * 调用时机：初始化尺寸视图或调整键盘尺寸时
     */
    public void UpdateSetKeyboardSizeViewPos() {
        Rect keyboardRect = new Rect(keyboardParams.x, keyboardParams.y,
                keyboardParams.x + keyboardParams.width,
                keyboardParams.y + keyboardParams.height);
        mSetKeyboardSizeView.SetScreenInfo(mScreenWidth, mScreenHeight, mStatusBarHeight);
        mSetKeyboardSizeView.SetPos(keyboardRect);
    }

    /**
     * 每次有候选词跟新的时候统一刷新界面，因为影响到的因素比较多，统一使用状态机解决
     */
    public void refreshDisplay(boolean special) {

        Log.i("Test","refreshDisplay");

        boolean isNK = Global.currentKeyboard == Global.KEYBOARD_T9 || Global.currentKeyboard == Global.KEYBOARD_SYM;
        int candidatenum = isNK ? WIInputMethodNK.GetWordsNumber() : WIInputMethod.GetWordsNumber();
        boolean hidecandidate = candidatenum != 0 ? false : (special ? false : true);

        if (mInputViewGG.isShown()) mInputViewGG.setVisibility(View.VISIBLE);
        functionViewGroup.refreshState(hidecandidate);
        functionsC.refreshStateForSecondLayout(hidecandidate);
        functionsC.refreshStateForLargeCandidate(hidecandidate);
        functionsC.refreshStateForPreEdit(hidecandidate);
        if (!hidecandidate) {
            viewSizeUpdate.UpdatePreEditSize();
            viewSizeUpdate.UpdateQPCandidateSize();
            viewSizeUpdate.UpdateLargeCandidateSize();
        }
        viewSizeUpdate.UpdateQuickSymbolSize();
        qkCandidatesViewGroup.refreshState(hidecandidate, isNK ? Global.EMOJI : Global.QUANPIN);
        specialSymbolChooseViewGroup.refreshState(hidecandidate);
        preFixViewGroup.refresh();
        t9InputViewGroup.refreshState();
        qkInputViewGroups.refreshState();
        quickSymbolViewGroup.refreshState(hidecandidate);
        functionsC.computeCursorPosition();

    }

    public void refreshDisplay() {
        refreshDisplay(false);
    }

    public void DeleteLast() {
        if ((WIInputMethodNK.GetWordsNumber() == 0 && WIInputMethod.GetWordsNumber() == 0) || Global.currentKeyboard == Global.KEYBOARD_SYM) {
            this.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        } else {
            if (Global.currentKeyboard == Global.KEYBOARD_T9) {
                String pinyin = WIInputMethodNK.GetWordsPinyin(0);
                Global.addToRedo(pinyin.substring(pinyin.length()-1));
                WIInputMethodNK.DeleteAction();
                t9InputViewGroup.updateFirstKeyText();
                refreshDisplay();
            } else if (Global.currentKeyboard == Global.KEYBOARD_QP) {
                if (mQPOrEmoji == Global.QUANPIN) {
                    String pinyin = WIInputMethod.GetWordsPinyin(0);
                    Global.addToRedo(pinyin.substring(pinyin.length()-1));
                    innerEdit("", true);
                } else if (mQPOrEmoji == Global.EMOJI) {
                    WIInputMethodNK.CLeanKernel();
                    refreshDisplay();
                    refreshDisplay(true);
                }
            } else if (Global.currentKeyboard == Global.KEYBOARD_EN) {
                WIInputMethodNK.CLeanKernel();
                WIInputMethod.CLeanKernel();
                refreshDisplay(true);
            }
        }
    }

    public void ChooseWord(int index) {
        String text = null;
        if (Global.currentKeyboard == Global.KEYBOARD_T9) {
            text = WIInputMethodNK.GetWordSelectedWord(index);
            refreshDisplay();
            t9InputViewGroup.updateFirstKeyText();
        } else if (Global.currentKeyboard == Global.KEYBOARD_QP) {
            text = WIInputMethod.GetWordSelectedWord(index);
            refreshDisplay();
        }
        if (text != null) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                Global.addToRedo(text);
                ic.commitText(text, 1);
            }
        }
        qkInputViewGroups.refreshQKKeyboardPredict();//刷新点滑预测
    }

    /**
     * 上屏,从这里传输的文字会直接填写到文本框里
     *
     * @param text
     */
    public void CommitText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (text != null && ic != null) {
            Global.addToRedo(text);
            ic.commitText(text, 1);
        }
        qkInputViewGroups.refreshQKKeyboardPredict();//刷新点滑预测
//		refreshDisplay();
    }

    /**
     * 如其名，向九键内核传递拼音串
     * @param msg
    * */
    public void sendMsgToKernel(String msg) {
//        Global.redoTextForDeleteAll_preedit = "";
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SEND_TO_KERNEL, msg));
    }

    /**
     * 向全键内核传递拼音串
     *
     * @param msg
     * */
    public void sendMsgToQKKernel(String msg) {
//        Global.redoTextForDeleteAll_preedit = "";
        mHandler.sendMessage(mHandler.obtainMessage(QP_MSG_SEND_TO_KERNEL, msg));
    }

    public OnChangeListener mOnSizeChangeListener = new OnChangeListener() {

        public void onSizeChange(Rect keyboardRect) {
            keyboardWidth = keyboardRect.width();
            keyboardHeight = keyboardRect.height();

            keyboardParams.x = keyboardRect.left;
            keyboardParams.y = keyboardRect.top;
            keyboardParams.width = keyboardWidth;
            keyboardParams.height = keyboardHeight;

            standardHorizantalGapDistance = keyboardWidth * 2 / 100;
            standardVerticalGapDistance = keyboardHeight * 2 / 100;

            viewSizeUpdate.updateViewSizeAndPosition();
            updateWindowManager();
        }

        public void onFinishSetting() {
            screenInfoC.WriteKeyboardSizeInfoToSharedPreference();
            viewManagerC.removeSetKeyboardSizeView();
        }

        public void onPosChange(Rect keyboardRect) {
            keyboardParams.x = keyboardRect.left % mScreenWidth;
            keyboardParams.y = keyboardRect.top % mScreenHeight;
            viewSizeUpdate.UpdateLightSize();//因为是独立的，所以这里要独立重定位
            screenInfoC.WriteKeyboardSizeInfoToSharedPreference();
            updateWindowManager();
        }

        public void onResetSetting() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(T9SoftKeyboard8.this);
            Editor edit = sp.edit();
            edit.remove(KEYBOARD_HEIGHT_S + orientation);
            edit.remove(KEYBOARD_WIDTH_S + orientation);
            edit.remove(KEYBOARD_X_S + orientation);
            edit.remove(KEYBOARD_Y_S + orientation);
            edit.remove(FULL_WIDTH_S + orientation);
            edit.remove(FULL_WIDTH_X_S + orientation);
            edit.commit();
            screenInfoC.LoadKeyboardSizeInfoFromSharedPreference();

            viewSizeUpdate.updateViewSizeAndPosition();

            UpdateSetKeyboardSizeViewPos();
            mSetKeyboardSizeView.invalidate();
        }
    };

    @SuppressWarnings("deprecation")
    public void updateWindowManager() {
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        wm.updateViewLayout(keyboardLayout, keyboardParams);
    }

    public void switchKeyboardTo(int keyboard, boolean showAnim) {
        keyBoardSwitcher.SwitchKeyboard(keyboard, showAnim);
    }

    /**
     * 键盘切换类，实际运用基本只调用期中的SwitchKeyBoard
     */
    public class KeyBoardSwitcherC {

        /**
         * 功能：使键盘占满屏幕的Width
         * 调用时机：从九键切到英文键盘
         */
        private void fullWidth() {
            quickSymbolViewGroup.setSize(keyboardWidth - 2 * standardHorizantalGapDistance, ViewGroup.LayoutParams.MATCH_PARENT);
            preFixViewGroup.setSize(keyboardWidth * -2 * standardHorizantalGapDistance, ViewGroup.LayoutParams.MATCH_PARENT);
            updateWindowManager();
        }

        /**
         * 功能：使键盘宽度不占满整个Width
         * 调用时机：英文键盘切向九键
         */
        private void fullWidthBack() {
            quickSymbolViewGroup.setSize(keyboardWidth * res.getInteger(R.integer.PREEDIT_WIDTH) / 100, ViewGroup.LayoutParams.MATCH_PARENT);
            preFixViewGroup.setSize(keyboardWidth * res.getInteger(R.integer.PREEDIT_WIDTH) / 100, ViewGroup.LayoutParams.MATCH_PARENT);
            updateWindowManager();
        }

        private void HideKeyboard(int keyboard, boolean showAnim) {
            switch (Global.currentKeyboard) {
                case Global.KEYBOARD_QP:
                case Global.KEYBOARD_EN:
                    if (keyboard != Global.KEYBOARD_QP && keyboard != Global.KEYBOARD_EN) {
                        qkInputViewGroups.startHideAnimation(showAnim);
                    }
                    break;
                default:
                    if (keyboard == Global.KEYBOARD_NUM || keyboard == Global.KEYBOARD_SYM) {
                        t9InputViewGroup.T9ToNum(showAnim);
                    } else {
                        t9InputViewGroup.hideT9(showAnim);
                        fullWidth();
                    }
                    break;
            }
            if (preFixViewGroup.isShown()) preFixViewGroup.setVisibility(View.GONE);
        }

        private void ShowKeyboard(int keyboard, boolean showAnim) {
            switch (keyboard) {
                case Global.KEYBOARD_QP:
                case Global.KEYBOARD_EN:
                    if (Global.currentKeyboard != Global.KEYBOARD_QP && Global.currentKeyboard != Global.KEYBOARD_EN) {
                        qkInputViewGroups.startShowAnimation(showAnim);
                    }
                    break;
                default:
                    t9InputViewGroup.showT9(showAnim);
                    fullWidthBack();
                    break;
            }
            quickSymbolViewGroup.updateCurrentSymbolsAndSetTheContent(keyboard);
        }

        /**
         * 功能：切换键盘
         * 调用时机：首次弹出键盘或者按下键盘上相应的切换键时调用
         *
         * @param keyboard 要切换到的键盘
         * @param showAnim 是否播放动画
         */
        public void SwitchKeyboard(int keyboard, boolean showAnim) {
            qkInputViewGroups.reloadPredictText(keyboard);
            WIInputMethodNK.CLeanKernel();

            HideKeyboard(keyboard, showAnim);
            ShowKeyboard(keyboard, showAnim);

            Global.currentKeyboard = keyboard;
            refreshDisplay();
            bottomBarViewGroup.switchToKeyboard(keyboard);
            viewSizeUpdate.updateViewSizeAndPosition();
        }
    }

    /**
     * 一些工具函数集中存放的地方
     */
    public class FunctionsC {
        //For Listeners
        public void DeleteAll() {
            if ((WIInputMethodNK.GetWordsNumber() == 0 && WIInputMethod.GetWordsNumber() == 0) || Global.currentKeyboard == Global.KEYBOARD_SYM) {
                InputConnection ic = T9SoftKeyboard8.this.getCurrentInputConnection();
                if (ic != null) {
                    Global.redoTextForDeleteAll = ic.getTextBeforeCursor(200,0);
                    ic.deleteSurroundingText(Integer.MAX_VALUE, 0);
                }
            } else {
                Global.redoTextForDeleteAll_preedit = WIInputMethod.GetWordsPinyin(0);
                Global.redoTextForDeleteAll = "";
            }
            if (Global.currentKeyboard == Global.KEYBOARD_T9 || Global.currentKeyboard == Global.KEYBOARD_SYM || Global.currentKeyboard == Global.KEYBOARD_NUM) {
                WIInputMethodNK.CLeanKernel();
                t9InputViewGroup.updateFirstKeyText();
                refreshDisplay();
            } else if (Global.currentKeyboard == Global.KEYBOARD_QP) {
                WIInputMethod.CLeanKernel();
                refreshDisplay(mQPOrEmoji != Global.QUANPIN);
            } else if (Global.currentKeyboard == Global.KEYBOARD_EN) {
                WIInputMethodNK.CLeanKernel();
                WIInputMethod.CLeanKernel();
                refreshDisplay();
            }
            qkInputViewGroups.refreshQKKeyboardPredict();//刷新点滑预测
        }

        //For others
        public void computeCursorPosition() {
            //计算光标位置
            int cursor = WIInputMethod.GetWordsPinyin(0) == null ? 0 : WIInputMethod.GetWordsPinyin(0).length();
            //上屏
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.beginBatchEdit();
                ic.setComposingText(WIInputMethod.GetWordsPinyin(0), cursor);
                ic.endBatchEdit();
            }
        }

        /**
         * 功能：判断当前输入框是否要输入网址，为了显示相应的字符
         * 调用时机：切换到符号键盘时调用
         *
         * @param ei 输入框信息
         * @return 是否要输入网址
         */
        private boolean isToShowUrl(EditorInfo ei) {
            if (ei != null) {
                if ((ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT
                        && (EditorInfo.TYPE_MASK_VARIATION & ei.inputType) == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                    return true;
                }
                if ((ei.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) == EditorInfo.IME_ACTION_GO) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 功能：判断输入框是否要输入邮箱
         * 调用时机：切换为符号键盘
         *
         * @param ei
         * @return
         */
        private boolean isToShowEmail(EditorInfo ei) {
            if (ei != null) {
                if ((ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT
                        && (EditorInfo.TYPE_MASK_VARIATION & ei.inputType) == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 功能：调用候选框需要显示的符号集
         * 调用时机：切换为符号键盘
         */
        public final void showDefaultSymbolSet() {
            final EditorInfo ei = T9SoftKeyboard8.this.getCurrentInputEditorInfo();
            if (isToShowUrl(ei)) {
                sendMsgToKernel("'u");
            } else if (isToShowEmail(ei)) {
                sendMsgToKernel("'e");
            } else {
                sendMsgToKernel("'");
            }
        }

        public void refreshStateForSecondLayout(boolean show) {
            if (Global.inLarge) {
                secondLayerLayout.setVisibility(View.GONE);
            } else {
                secondLayerLayout.setVisibility(View.VISIBLE);
            }
        }

        public void refreshStateForLargeCandidate(boolean show) {
            if (Global.inLarge || show) {
                largeCandidateButton.setVisibility(View.GONE);
            } else {
                largeCandidateButton.setVisibility(View.VISIBLE);
            }
            largeCandidateButton.getBackground().setAlpha((int) (Global.mCurrentAlpha * 255));
        }

        public void refreshStateForPreEdit(boolean candidatehide) {
            if (!candidatehide) {
                if (Global.currentKeyboard == Global.KEYBOARD_SYM) {
                    preEdit.setVisibility(View.GONE);
                } else if (WIInputMethodNK.GetWordsPinyin(0) != null && WIInputMethodNK.GetWordsPinyin(0).length() != 0) {
                    String text = WIInputMethodNK.GetWordsPinyin(0).length() == 0 ? " " : WIInputMethodNK.GetWordsPinyin(0);
                    preEdit.setText(text);
                    int cursorPos = WIInputMethodNK.GetWordsPinyin(0) == null ?
                            0 : WIInputMethodNK.GetWordsPinyin(0).length();
                    preEdit.setSelection(cursorPos);
                    viewSizeUpdate.UpdatePreEditSize();
                    refreshPreEdit();
                    preEdit.setVisibility(View.VISIBLE);
                } else if (WIInputMethod.GetWordsPinyin(0) != null && WIInputMethod.GetWordsPinyin(0).length() != 0) {
                    String text = WIInputMethod.GetWordsPinyin(0).length() == 0 ? " " : WIInputMethod.GetWordsPinyin(0);
                    preEdit.setText(text);
                    int cursorPos = WIInputMethod.GetWordsPinyin(0) == null ?
                            0 : WIInputMethod.GetWordsPinyin(0).length();
                    preEdit.setSelection(cursorPos);
                    viewSizeUpdate.UpdatePreEditSize();
                    refreshPreEdit();
                    preEdit.setVisibility(View.VISIBLE);
                } else {
                    preEdit.setVisibility(View.GONE);
                }
            } else {
                preEdit.setVisibility(View.GONE);
            }
        }

        public void updateSkin(TextView v, int textcolor, int backgroundcolor) {
            v.setTextColor(textcolor);
            v.setBackgroundColor(backgroundcolor);
            v.getBackground().setAlpha((int) (Global.mCurrentAlpha * 255));
        }

        public void refreshPreEdit() {
            preEdit.setBackgroundResource(R.drawable.blank);
            functionsC.updateSkin(preEdit, skinInfoManager.skinData.textcolors_preEdit,
                    skinInfoManager.skinData.backcolor_preEdit
            );
            preEdit.setBackgroundColor(skinInfoManager.skinData.backcolor_preEdit);
            preEdit.getBackground().setAlpha((int) (Global.mCurrentAlpha * 255));
        }

        /**
         * 检验字符串str中是否全为英文字母
         *
         * @param str
         * @return
         */
        public boolean isAllLetter(String str) {
            char tmp;
            for (int i = 0; i < str.length(); i++) {
                tmp = str.charAt(i);
                if (!((tmp >= 'a' && tmp <= 'z') || (tmp >= 'A' && tmp <= 'Z'))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 判断输入域类型，返回确定键盘类型
         */
        private final int getKeyboardType(EditorInfo pEditorInfo) {
            int keyboardType;
            // 判断输入框类型,选择对应的键盘
            switch (pEditorInfo.inputType & EditorInfo.TYPE_MASK_CLASS) {
                case EditorInfo.TYPE_CLASS_NUMBER:
                case EditorInfo.TYPE_CLASS_DATETIME:
                case EditorInfo.TYPE_CLASS_PHONE:
                    keyboardType = Global.KEYBOARD_NUM;
                    break;
                case EditorInfo.TYPE_CLASS_TEXT:
                    switch (EditorInfo.TYPE_MASK_VARIATION & pEditorInfo.inputType) {
                        case EditorInfo.TYPE_TEXT_VARIATION_URI:
                        case EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                        case EditorInfo.TYPE_TEXT_VARIATION_PASSWORD:
                        case EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                            keyboardType = Global.KEYBOARD_EN;
                            break;
                        default:
                            keyboardType = zhKeyboard;
                            break;
                    }
                    break;
                default:// 默认为英文键盘——
                    keyboardType = zhKeyboard;
                    break;
            }
            return keyboardType;
        }
    }

    /**
     * 存放listener
     */
    private class Listeners {
        OnTouchListener largeCandidateOnTouchListener = new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                transparencyHandle.handleAlpha(motionEvent.getAction());
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    qkCandidatesViewGroup.displayCandidates();
                    qkCandidatesViewGroup.largeTheCandidate();
                    Global.inLarge = true;
                    bottomBarViewGroup.intoReturnState();
                }
                keyBoardTouchEffect.onTouchEffectWithAnim(view, motionEvent.getAction(),
                        skinInfoManager.skinData.backcolor_touchdown,
                        skinInfoManager.skinData.backcolor_quickSymbol,
                        T9SoftKeyboard8.this);
                return false;
            }
        };
    }

    /**
     * 各种view的创建函数，不初始化位置
     */
    private class KeyBoardCreate {

        private void CreateCandidateView() {
            qkCandidatesViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            qkCandidatesViewGroup.create(T9SoftKeyboard8.this);
            qkCandidatesViewGroup.addThisToView(keyboardLayout);
        }
        private void CreatePreEditView() {
            preEditLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            preEdit.setPadding(0, 0, 0, 0);
            preEdit.setGravity(Gravity.LEFT);
            preEdit.setVisibility(View.GONE);
            preEdit.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            preEdit.setBackgroundResource(R.drawable.blank);
            preEdit.setBackgroundColor(skinInfoManager.skinData.backcolor_preEdit);
            preEdit.getBackground().setAlpha((int) (Global.mCurrentAlpha * 255));
            if (Global.shadowSwitch) preEdit.setShadowLayer(Global.shadowRadius, 0, 0, skinInfoManager.skinData.shadow);
            keyboardLayout.addView(preEdit, preEditLayoutParams);
        }

        /**
         * 功能：点滑产生光效果
         */
        private void CreateLightView() {
            lightViewManager.setSoftkeyboard(T9SoftKeyboard8.this);
            lightViewManager.create(T9SoftKeyboard8.this);
            lightViewManager.setTypeface(mTypeface);
        }

        private void CreateFuncKey() {
            functionViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            functionViewGroup.create(T9SoftKeyboard8.this);
            functionViewGroup.setTypeface(mTypeface);
            functionViewGroup.addThisToView(keyboardLayout);
        }

        private void CreatePrefixView() {
            preFixViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            preFixViewGroup.create(T9SoftKeyboard8.this);
            preFixViewGroup.addThisToView(secondLayerLayout);
        }

        private void CreateQuickSymbol() {
            quickSymbolViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            quickSymbolViewGroup.setTypeface(mTypeface);
            quickSymbolViewGroup.create(T9SoftKeyboard8.this);
            quickSymbolViewGroup.updateCurrentSymbolsAndSetTheContent(Global.currentKeyboard);
            quickSymbolViewGroup.addThisToView(secondLayerLayout);
        }

        private void CreateSpecialSymbolChoose() {
            specialSymbolChooseViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            specialSymbolChooseViewGroup.create(T9SoftKeyboard8.this);
            specialSymbolChooseViewGroup.setTypeface(mTypeface);
            specialSymbolChooseViewGroup.addThisToView(secondLayerLayout);
        }

        private void CreateSetKeyboardSizeView() {
            mSetKeyboardSizeView = new SetKeyboardSizeView(T9SoftKeyboard8.this, mOnSizeChangeListener);
            mSetKeyboardSizeView.SetTypeface(mTypeface);
            mSetKeyboardSizeView.SetMovingIcon((String) functionViewGroup.buttonList.get(0).getText());
        }

        /**
         * 加载资源
         */
        private void LoadResources() {
            Resources res = getResources();
            mTypeface = Typeface.createFromAsset(getAssets(), res.getString(R.string.font_file_path));// 加载自定义字体
            mFuncKeyboardText = res.getStringArray(R.array.FUNC_KEYBOARD_TEXT);

            Global.shadowRadius = res.getInteger(R.integer.SHADOW_RADIUS);

            // 初始化，具体加载在onWindowShown里面实现
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(T9SoftKeyboard8.this);
            Global.slideDeleteSwitch = sharedPreferences.getBoolean("SLIDE_DELETE_CHECK", false);
            Global.shadowSwitch = sharedPreferences.getBoolean("SHADOW_TEXT_CHECK", true);
            /*
            * 设置当前键盘皮肤
            */
            int themeType = sharedPreferences.getInt("THEME_TYPE", 0);
            boolean isDiy = sharedPreferences.getBoolean("IS_DIY", false);
            if (isDiy) {
                skinInfoManager.loadConfigurationFromDIY(T9SoftKeyboard8.this);
            } else {
                skinInfoManager.loadConfigurationFromXML(themeType, res);
            }
            Global.currentSkinType = themeType;
        }

        private void CreateBottomBarViewGroup() {
            bottomBarViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            bottomBarViewGroup.create(T9SoftKeyboard8.this);
            bottomBarViewGroup.setTypeFace(mTypeface);
            bottomBarViewGroup.addThisToView(keyboardLayout);
        }

        private void CreateLargeButton() {
            largeCandidateButton = new QuickButton(T9SoftKeyboard8.this);
            largeCandidateButton.setTextColor(skinInfoManager.skinData.textcolors_quickSymbol);
            largeCandidateButton.setBackgroundColor(skinInfoManager.skinData.backcolor_quickSymbol);
            largeCandidateButton.getBackground().setAlpha((int) (Global.mCurrentAlpha * 255));
            largeCandidateButton.setTypeface(mTypeface);
            largeCandidateButton.setText(res.getString(R.string.largecandidate));
            largeCandidateButton.setOnTouchListener(listeners.largeCandidateOnTouchListener);
            if (Global.shadowSwitch)
                largeCandidateButton.setShadowLayer(Global.shadowRadius, 0, 0, skinInfoManager.skinData.shadow);
            largeCandidateButton.setVisibility(View.GONE);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0);
            largeCandidateButton.itsLayoutParams = params;

            secondLayerLayout.addView(largeCandidateButton, params);
        }

        private void CreateEnglishKeyboard() {
            qkInputViewGroups.setSoftKeyboard(T9SoftKeyboard8.this);
            qkInputViewGroups.create(T9SoftKeyboard8.this);
            qkInputViewGroups.setTypeface(mTypeface);
            qkInputViewGroups.addThisToView(mInputViewGG);
        }

        private void CreateT9Keyboard() {
            t9InputViewGroup.setSoftKeyboard(T9SoftKeyboard8.this);
            t9InputViewGroup.create(T9SoftKeyboard8.this);
            t9InputViewGroup.setTypeface(mTypeface);
            t9InputViewGroup.addThisToView(mInputViewGG);
        }

        private void createKeyboard() {
            if (keyboardLayout.getChildCount() < 1) {
                LoadResources();
                CreateFuncKey();
                CreatePreEditView();
                CreateCandidateView();
                keyboardLayout.addView(secondLayerLayout, secondParams);
                CreatePrefixView();
                CreateQuickSymbol();
                CreateSpecialSymbolChoose();
                CreateLargeButton();
                mInputViewGG = new LinearLayout(T9SoftKeyboard8.this);
                mGGParams = new LinearLayout.LayoutParams(0, 0);
                CreateT9Keyboard();
                CreateEnglishKeyboard();
                keyboardLayout.addView(mInputViewGG, mGGParams);
                CreateBottomBarViewGroup();
                CreateLightView();
                //上面的是重构完的
                CreateSetKeyboardSizeView();
            }
        }
    }

    /**
     * 整合所有view大小和位置更新方法到一个内部类中
     */
    public class ViewSizeUpdateC {
        private int[] layerHeightRate;

        private void UpdatePrefixSize() {
            preFixViewGroup.setPosition(0, 0);
            if (Global.currentKeyboard == Global.KEYBOARD_NUM || Global.currentKeyboard == Global.KEYBOARD_SYM || Global.currentKeyboard == Global.KEYBOARD_T9) {
                preFixViewGroup.setSize(keyboardWidth * 44 / 100, keyboardHeight * layerHeightRate[1] / 100);
            } else {
                preFixViewGroup.setSize(keyboardWidth - standardHorizantalGapDistance, keyboardHeight * layerHeightRate[1] / 100);
            }
            preFixViewGroup.setButtonPadding(standardHorizantalGapDistance);
            preFixViewGroup.updateViewLayout();
        }

        public void UpdatePreEditSize() {
            preEdit.setPadding(0, 0, 0, 0);
            preEditLayoutParams.width = LayoutParams.MATCH_PARENT;
            preEditLayoutParams.height = (int) (0.4 * keyboardHeight * layerHeightRate[0] / 100);
            preEdit.setTextSize((float) (preEditLayoutParams.height * 0.33));
            preEditLayoutParams.leftMargin = standardHorizantalGapDistance;
            preEditLayoutParams.rightMargin = standardHorizantalGapDistance;
            keyboardLayout.updateViewLayout(preEdit, preEditLayoutParams);
        }

        private void UpdateEnglishKeyboardSize() {
            qkInputViewGroups.setPosition(0, 0);
            qkInputViewGroups.setSize(
                    keyboardWidth,
                    keyboardHeight * layerHeightRate[2] / 100,
                    standardHorizantalGapDistance
            );
            qkInputViewGroups.updateViewLayout();
        }

        private void UpdateQuickSymbolSize() {
            int height = keyboardHeight * layerHeightRate[1] / 100;
            int width = keyboardWidth - 2 * standardHorizantalGapDistance;
            int buttonWidth = width / 6;
            quickSymbolViewGroup.setPosition(0, 0);
            quickSymbolViewGroup.setButtonPadding(standardHorizantalGapDistance);
            quickSymbolViewGroup.setButtonWidth(buttonWidth);
            float textSize = 2 * Math.min(buttonWidth, height) / 5;
            quickSymbolViewGroup.setTextSize(textSize);
            if (t9InputViewGroup.deleteButton.isShown() && largeCandidateButton.isShown()) {
                quickSymbolViewGroup.setSize(keyboardWidth * 44 / 100, height);
            } else if (t9InputViewGroup.deleteButton.isShown() || largeCandidateButton.isShown()) {
                quickSymbolViewGroup.setSize(keyboardWidth * res.getInteger(R.integer.PREEDIT_WIDTH) / 100, height);
            } else {
                quickSymbolViewGroup.setSize(width, height);
            }
            quickSymbolViewGroup.updateViewLayout();
        }

        private void UpdateSpecialSymbolChooseSize() {
            int height = keyboardHeight * layerHeightRate[1] / 100;
            specialSymbolChooseViewGroup.updateSize(keyboardWidth * res.getInteger(R.integer.PREEDIT_WIDTH) / 100, height);
            specialSymbolChooseViewGroup.setPosition(0, 0);
            specialSymbolChooseViewGroup.setTextSize(2 * height / 5);
        }

        private void UpdateT9Size() {
            UpdateT9Layout();
        }

        private void UpdateLightSize() {
            lightViewManager.setSize(keyboardWidth, keyboardHeight * layerHeightRate[2] / 100,
                    keyboardParams.x,
                    keyboardParams.y + keyboardHeight * (layerHeightRate[0] + layerHeightRate[1]) / 100 +2*standardVerticalGapDistance //计算y位置
            );
        }

        private void UpdateFunctionsSize() {
            int height = keyboardHeight * layerHeightRate[0] / 100;
            int buttonwidth = keyboardWidth / 5 - standardHorizantalGapDistance * 6 / 5;
            functionViewGroup.updatesize(keyboardWidth, height);
            functionViewGroup.setButtonPadding(standardHorizantalGapDistance);
            functionViewGroup.setButtonWidth(buttonwidth);
            functionViewGroup.setTextSize(50 * Math.min(buttonwidth, height) / 100);
        }

        public void UpdateQPCandidateSize() {
            int height = 0, topMargin = 0;
            if (!preEdit.isShown()) {
                height = keyboardHeight * layerHeightRate[0] / 100;
            } else {
                height = (int) (0.6 * keyboardHeight * layerHeightRate[0] / 100);
            }
            qkCandidatesViewGroup.setPosition(standardHorizantalGapDistance, topMargin);
            qkCandidatesViewGroup.setSize(keyboardWidth - 2 * standardHorizantalGapDistance, height);
            qkCandidatesViewGroup.updateViewLayout();
        }

        private void UpdateBottomBarSize() {

            bottomBarViewGroup.setPosition(0, standardVerticalGapDistance);
            bottomBarViewGroup.setSize(keyboardWidth - standardHorizantalGapDistance, keyboardHeight * layerHeightRate[3] / 100);
            bottomBarViewGroup.setButtonPadding(standardHorizantalGapDistance);
            if (Global.currentKeyboard == Global.KEYBOARD_NUM) {
                bottomBarViewGroup.setButtonWidthByRate(res.getIntArray(R.array.BOTTOMBAR_NUM_KEY_WIDTH));
            } else {
                bottomBarViewGroup.setButtonWidthByRate(res.getIntArray(R.array.BOTTOMBAR_KEY_WIDTH));
            }
            bottomBarViewGroup.setTextSize();
            bottomBarViewGroup.updateViewLayout();
        }

        private void UpdateT9Layout() {
            mGGParams.topMargin = standardVerticalGapDistance;
            mGGParams.height = keyboardHeight * layerHeightRate[2] / 100;
            mGGParams.width = keyboardWidth;
            t9InputViewGroup.setSize(keyboardWidth, mGGParams.height,
                    standardHorizantalGapDistance);
            t9InputViewGroup.deleteButton.getPaint().setTextSize(
                    3 * Math.min(t9InputViewGroup.deleteButton.itsLayoutParams.width, layerHeightRate[1] * keyboardHeight / 100) / 5);
            keyboardLayout.updateViewLayout(mInputViewGG, mGGParams);
        }

        private void UpdateLargeCandidateSize() {
            largeCandidateButton.itsLayoutParams.height = LayoutParams.MATCH_PARENT;
            largeCandidateButton.itsLayoutParams.width = keyboardWidth - 3 * standardHorizantalGapDistance - res.getInteger(R.integer.PREEDIT_WIDTH) * keyboardWidth / 100;
            ((LinearLayout.LayoutParams) largeCandidateButton.itsLayoutParams).leftMargin = standardHorizantalGapDistance;

            float textsize = Math.min(3 * Math.min(secondParams.height, (100 - res.getInteger(R.integer.PREEDIT_WIDTH)) * keyboardWidth / 100) / 5, 30);
            largeCandidateButton.getPaint().setTextSize(textsize);
        }

        public void updateViewSizeAndPosition() {
            layerHeightRate = res.getIntArray(R.array.LAYER_HEIGHT);
            UpdateT9Size();
            secondParams.height = keyboardHeight * layerHeightRate[1] / 100;
            secondParams.topMargin = standardVerticalGapDistance;
            secondParams.leftMargin = standardHorizantalGapDistance;
            UpdateQPCandidateSize();
            UpdatePreEditSize();
            UpdateEnglishKeyboardSize();
            UpdatePrefixSize();
            UpdateFunctionsSize();
            UpdateLargeCandidateSize();
            UpdateQuickSymbolSize();
            UpdateSpecialSymbolChooseSize();
            UpdateBottomBarSize();
            UpdateLightSize();
        }
    }

    /**
     * 整合了皮肤更新的所有方法到一个内部类中
     */
    public class SkinUpdateC {
        /**
         * 功能：更新键盘皮肤和透明度
         * 调用时机：每次唤出键盘
         */
        public void updateSkin() {
            /*
             * 设置当前键盘皮肤
             */
            int themeType = PreferenceManager.getDefaultSharedPreferences(T9SoftKeyboard8.this).getInt("THEME_TYPE", 0);
            boolean isDiy = PreferenceManager.getDefaultSharedPreferences(T9SoftKeyboard8.this).getBoolean("IS_DIY", false);
            if (themeType != Global.currentSkinType) {
                skinInfoManager.loadConfigurationFromXML(themeType, res);
            }
            if (isDiy) {
                skinInfoManager.loadConfigurationFromDIY(T9SoftKeyboard8.this);
            }

            qkCandidatesViewGroup.updateSkin();
            t9InputViewGroup.updateSkin();
            preEdit.setBackgroundResource(R.drawable.button_back_x);
            functionsC.updateSkin(preEdit, skinInfoManager.skinData.textcolors_preEdit, skinInfoManager.skinData.backcolor_preEdit);
            preFixViewGroup.updateSkin();
            specialSymbolChooseViewGroup.updateSkin();
            quickSymbolViewGroup.updateSkin();
            qkInputViewGroups.updateSkin();
            functionViewGroup.updateSkin();
            bottomBarViewGroup.updateSkin();
            functionsC.updateSkin(largeCandidateButton, skinInfoManager.skinData.textcolors_quickSymbol, skinInfoManager.skinData.backcolor_quickSymbol);
            if (keyboardLayout.getBackground() != null)
                keyboardLayout.getBackground().setAlpha((int) (Global.keyboardViewBackgroundAlpha * 255));
            else {
                keyboardLayout.setBackgroundResource(R.drawable.blank);
                keyboardLayout.setBackgroundColor(skinInfoManager.skinData.backcolor_touchdown);
                keyboardLayout.getBackground().setAlpha((int) (Global.keyboardViewBackgroundAlpha * 255));
            }
        }

        public void updateShadowLayer() {
            qkCandidatesViewGroup.setShadowLayer(Global.shadowRadius, skinInfoManager.skinData.shadow);
            preFixViewGroup.setShadowLayer(Global.shadowRadius, skinInfoManager.skinData.shadow);
            quickSymbolViewGroup.setShadowLayer(Global.shadowRadius, skinInfoManager.skinData.shadow);
            functionViewGroup.setShadowLayer(Global.shadowRadius, skinInfoManager.skinData.shadow);
            bottomBarViewGroup.setShadowLayer(Global.shadowRadius, skinInfoManager.skinData.shadow);
            specialSymbolChooseViewGroup.setShadowLayer(Global.shadowRadius, skinInfoManager.skinData.shadow);
            largeCandidateButton.setShadowLayer(Global.shadowRadius, 0, 0, skinInfoManager.skinData.shadow);
            preEdit.setShadowLayer(Global.shadowRadius, 0, 0, skinInfoManager.skinData.shadow);

        }
    }

    /**
     * 屏幕信息处理类
     */
    public class ScreenInfoC {

        /**
         * 功能：刷新界面信息
         * 调用时机：在初始化和旋转手机方向时需要调用以及时刷新信息
         */
        private void refreshScreenInfo() {
            WindowManager wm = (WindowManager) getApplicationContext()
                    .getSystemService(WINDOW_SERVICE);
            Display dis = wm.getDefaultDisplay();
            mScreenWidth = dis.getWidth();
            mScreenHeight = dis.getHeight();
            orientation = mScreenWidth > mScreenHeight ? ORI_HOR : ORI_VER;
            mStatusBarHeight = getStatusBarHeight();

            DEFAULT_KEYBOARD_Y = mScreenHeight / 3;
            DEFAULT_KEYBOARD_WIDTH = mScreenWidth * 2 / 3;
            DEFAULT_KEYBOARD_HEIGHT = mScreenHeight / 2;

            DEFAULT_FULL_WIDTH = mScreenWidth;
            DEFAULT_FULL_WIDTH_X = 0;

            if (mLeftHand) {
                DEFAULT_KEYBOARD_X = DEFAULT_KEYBOARD_WIDTH;
            } else {
                DEFAULT_KEYBOARD_X = mScreenWidth / 3;
            }
        }

        public int getStatusBarHeight() {
            int result = 0;
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = getResources().getDimensionPixelSize(resourceId);
            }
            return result;
        }

        /**
         * 功能：从SharedPreference中加载键盘的尺寸信息
         * 调用时机：初始化或旋转手机方向
         */
        private void LoadKeyboardSizeInfoFromSharedPreference() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(T9SoftKeyboard8.this);

            /**
             * 若为英文键盘，则为FULL_WIDTH
             */
//            if (zhKeyboard == Global.KEYBOARD_QP) {
            keyboardParams.x = sp.getInt(FULL_WIDTH_X_S + orientation, DEFAULT_FULL_WIDTH_X);
            keyboardWidth = sp.getInt(FULL_WIDTH_S + orientation, DEFAULT_FULL_WIDTH);
//            } else {
//                keyboardParams.x = sp.getInt(KEYBOARD_X_S + orientation, DEFAULT_KEYBOARD_X);
//                keyboardWidth = sp.getInt(KEYBOARD_WIDTH_S + orientation, DEFAULT_KEYBOARD_WIDTH);
//            }
            keyboardParams.y = sp.getInt(KEYBOARD_Y_S + orientation, DEFAULT_KEYBOARD_Y);
            keyboardHeight = sp.getInt(KEYBOARD_HEIGHT_S + orientation, DEFAULT_KEYBOARD_HEIGHT);

            keyboardParams.width = keyboardWidth;
            keyboardParams.height = keyboardHeight;

            standardVerticalGapDistance = keyboardHeight * 2 / 100;
            standardHorizantalGapDistance = keyboardWidth * 2 / 100;

//            mCandiParams.x = sp.getInt(CANDIDATE_X_S + orientation, DEFAULT_CANDIDATE_X);
//            mCandiParams.y = sp.getInt(CANDIDATE_Y_S + orientation, DEFAULT_CANDIDATE_Y);
//            mCandiParams.width = sp.getInt(CANDIDATE_WIDTH + orientation, DEFAULT_CANDIDATE_WIDTH);
//            mCandiParams.height = sp.getInt(CANDIDATE_HEIGHT + orientation, DEFAULT_CANDIDATE_HEIGHT);
        }

        /**
         * 功能：将键盘尺寸信息写入SharedPreference
         * 调用时机：调整键盘尺寸
         */
        public void WriteKeyboardSizeInfoToSharedPreference() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(T9SoftKeyboard8.this);
            Editor editor = sp.edit();

            editor.putInt(KEYBOARD_Y_S + orientation, keyboardParams.y);
            editor.putInt(KEYBOARD_HEIGHT_S + orientation, keyboardHeight);

//            if (mSetKeyboardSizeView.isJustKeyboard()) {
            editor.putInt(FULL_WIDTH_X_S + orientation, keyboardParams.x);
            editor.putInt(FULL_WIDTH_S + orientation, keyboardWidth);
//            } else {
//                editor.putInt(KEYBOARD_X_S + orientation, keyboardParams.x);
//                editor.putInt(KEYBOARD_WIDTH_S + orientation, keyboardWidth);
//            }
//            editor.putInt(CANDIDATE_X_S + orientation, mCandiParams.x);
//            editor.putInt(CANDIDATE_Y_S + orientation, mCandiParams.y);
//            editor.putInt(CANDIDATE_WIDTH + orientation, mCandiParams.width);
//            editor.putInt(CANDIDATE_HEIGHT + orientation, mCandiParams.height);
            editor.commit();
        }
    }

    /**
     * 键盘透明度处理，包括一些动画
     */
    public class TransparencyHandle {

        List<ValueAnimator> valueAnimators = new ArrayList<ValueAnimator>();
        List<View> allKeyList = new ArrayList<View>();

        boolean heartAlphaStarted = false;

        /**
         * 功能：开始心跳动画
         * 调用时机：五秒内没有touch
         */
        private void HeartAlpha() {

            if (heartAlphaStarted) return;//避免重复开启动画的防御措施，慎删@purebluesong
            if (!mWindowShown) return;
            if (WIInputMethodNK.GetWordsNumber() > 0 || !show || WIInputMethod.GetWordsNumber() > 0)
                return;
            if (quickSymbolViewGroup.isShown()) {
                quickSymbolViewGroup.clearAnimation();
            }
            heartAlphaStarted = true;
        }

        private void startAutoDownAlpha() {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE, 1000);
        }

        /**
         * @param alpha
         * @author:purebluesong 参数说明：一个0到1的单精度浮点型
         */
        public void setKeyBoardAlpha(float alpha) {
            int alphaInt = (int) (alpha * 255);
            setKeyboardAlphaRaw(alphaInt, 1);
        }

        /**
         * @param alphaInt
         * @param alphaFloat 参数是0~255的整形值 和0~1的单精度浮点,不应该对外使用
         */
        @SuppressLint("NewApi")
        private void setKeyboardAlphaRaw(int alphaInt, float alphaFloat) {
            t9InputViewGroup.setBackgroundAlpha(alphaInt);
            functionViewGroup.setBackgroundAlpha(alphaInt);
            functionViewGroup.setButtonAlpha(alphaFloat);
            bottomBarViewGroup.setBackgroundAlpha(alphaInt);
            bottomBarViewGroup.setButtonAlpha(alphaFloat);
            qkCandidatesViewGroup.setBackgroundAlpha(alphaInt);
            specialSymbolChooseViewGroup.setBackgroundAlpha(alphaInt);
            specialSymbolChooseViewGroup.setButtonAlpha(alphaFloat);
            preFixViewGroup.setBackgroundAlpha(alphaInt);
            preFixViewGroup.setButtonAlpha(alphaFloat);
            qkInputViewGroups.setBackgroundAlpha(alphaInt);
            quickSymbolViewGroup.setBackgroundAlpha(alphaInt);
            quickSymbolViewGroup.setButtonAlpha(alphaFloat);
            preEdit.getBackground().setAlpha(alphaInt);
            largeCandidateButton.getBackground().setAlpha(alphaInt);
        }

        private void UpAlpha() {
            if (!mWindowShown) return;
            if (WIInputMethodNK.GetWordsNumber() > 0 || !show || WIInputMethod.GetWordsNumber() > 0)
                return;
            Animation anim = AnimationUtils.loadAnimation(T9SoftKeyboard8.this, R.anim.hide);
            if (Global.currentKeyboard == Global.KEYBOARD_T9 ||
                    Global.currentKeyboard == Global.KEYBOARD_NUM ||
                    Global.currentKeyboard == Global.KEYBOARD_SYM) {
                t9InputViewGroup.startAnimation(anim);
            } else {
                qkInputViewGroups.startAnimation(anim);
            }
            if (preEdit.isShown()) preEdit.startAnimation(anim);
            if (bottomBarViewGroup.isShown()) bottomBarViewGroup.startAnimation(anim);
            if (functionViewGroup.isShown()) functionViewGroup.startAnimation(anim);
            if (specialSymbolChooseViewGroup.isShown()) specialSymbolChooseViewGroup.startAnimation(anim);
            if (quickSymbolViewGroup.isShown())
                quickSymbolViewGroup.startAnimation(anim);
            if (qkCandidatesViewGroup.isShown())
                qkCandidatesViewGroup.startAnimation(anim);
            if (largeCandidateButton.isShown())
                largeCandidateButton.startAnimation(anim);
            show = false;
        }

        /**
         * 功能：减小透明度
         * 调用时机：键盘上的任意touch事件
         */
        public void DownAlpha() {
            if (show || !mWindowShown) return;
            show = true;
            Animation anim = AnimationUtils.loadAnimation(T9SoftKeyboard8.this, R.anim.show);
            if (Global.currentKeyboard == Global.KEYBOARD_T9 || Global.currentKeyboard == Global.KEYBOARD_NUM || Global.currentKeyboard == Global.KEYBOARD_SYM) {
                if (t9InputViewGroup.isShown()) {
                    t9InputViewGroup.startAnimation(anim);
                }
            } else {
                qkInputViewGroups.startAnimation(anim);
            }
            if (preEdit.isShown()) preEdit.startAnimation(anim);
            if (bottomBarViewGroup.isShown()) bottomBarViewGroup.startAnimation(anim);
            if (functionViewGroup.isShown()) functionViewGroup.startAnimation(anim);
            if (specialSymbolChooseViewGroup.isShown()) specialSymbolChooseViewGroup.startAnimation(anim);
            if (quickSymbolViewGroup.isShown()) quickSymbolViewGroup.startAnimation(anim);
            if (preFixViewGroup.isShown()) preFixViewGroup.startAnimation(anim);
            if (qkCandidatesViewGroup.isShown()) qkCandidatesViewGroup.startAnimation(anim);
            if (largeCandidateButton.isShown()) largeCandidateButton.startAnimation(anim);
        }

        public void handleAlpha(int eventAction) {
            Global.keyboardRestTimeCount = 0;
            if (eventAction == MotionEvent.ACTION_DOWN) {
                DownAlpha();
            }
        }
    }

    /**
     * keyboardLayout、设置键盘大小的view的创建与删除
     */
    public class ViewManagerC {
        /**
         */
        public void addInputView() {
            if (!keyboardLayout.isShown() ) {
                WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
                screenInfoC.LoadKeyboardSizeInfoFromSharedPreference();

                keyboardParams.type = LayoutParams.TYPE_PHONE;
                keyboardParams.format = 1;
                keyboardParams.flags = DISABLE_LAYOUTPARAMS_FLAG;
                keyboardParams.gravity = Gravity.TOP | Gravity.LEFT;

                viewSizeUpdate.updateViewSizeAndPosition();
                wm.addView(keyboardLayout, keyboardParams);
            }
        }

        /***/
        public void removeInputView() {
            if (null != keyboardLayout && keyboardLayout.isShown()) {
                WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
                wm.removeView(keyboardLayout);
            }
        }

        /**
         * 功能：添加调整键盘尺寸的视图
         * 调用时机：touch RESIZE功能键
         *
         * @param type
         */
        public void addSetKeyboardSizeView(SettingType type) {
            mSetKeyboardSizeViewOn = true;//todo: what is this?
            WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);

            mSetKeyboardSizeParams.type = LayoutParams.TYPE_PHONE;
            mSetKeyboardSizeParams.format = 1;
            mSetKeyboardSizeParams.gravity = Gravity.TOP | Gravity.LEFT;
            mSetKeyboardSizeParams.x = 0;
            mSetKeyboardSizeParams.y = 0;
            mSetKeyboardSizeParams.width = mScreenWidth;
            mSetKeyboardSizeParams.height = mScreenHeight;

            mSetKeyboardSizeParams.flags = DISABLE_LAYOUTPARAMS_FLAG;
            UpdateSetKeyboardSizeViewPos();
            mSetKeyboardSizeView.SetSettingType(type);
            wm.addView(mSetKeyboardSizeView, mSetKeyboardSizeParams);
        }

        public void removeSetKeyboardSizeView() {
            if (mSetKeyboardSizeViewOn) {
                WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
                wm.removeView(mSetKeyboardSizeView);
            }
            mSetKeyboardSizeViewOn = false;
        }

    }

    /**
     * 键盘的的出场动画
     */
    private void startAnimation() {
        if (Global.currentKeyboard == Global.KEYBOARD_T9 || Global.currentKeyboard == Global.KEYBOARD_NUM || Global.currentKeyboard == Global.KEYBOARD_SYM) {
            t9InputViewGroup.startShowAnimation();
            qkInputViewGroups.setVisibility(View.GONE);
        } else {
            qkInputViewGroups.startShowAnimation();
            t9InputViewGroup.setVisibility(View.GONE);
        }

        preFixViewGroup.setVisibility(View.GONE);
        quickSymbolViewGroup.setVisibility(View.VISIBLE);
        functionViewGroup.startshowAnimation();
        functionViewGroup.setVisibility(View.VISIBLE);
        bottomBarViewGroup.startShowAnimation();
        bottomBarViewGroup.setVisibility(View.VISIBLE);
        if (keyboardLayout.getBackground() != null) {
            keyboardLayout.getBackground().setAlpha((int) (Global.keyboardViewBackgroundAlpha * 255));
        }
        keyboardParams.flags = ABLE_LAYOUTPARAMS_FLAG;

        viewSizeUpdate.UpdateQuickSymbolSize();
        if (keyboardLayout.isShown()) {
            WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
            wm.updateViewLayout(keyboardLayout, keyboardParams);
        }
        show = true;
    }

    /**
     * 键盘的退出动画
     * */
    private void startOutAnimation() {
        if (!Global.isQK(Global.currentKeyboard)) {
            qkInputViewGroups.setVisibility(View.GONE);
            t9InputViewGroup.startHideAnimation();
        } else {
            t9InputViewGroup.setVisibility(View.GONE);
            qkInputViewGroups.startHideAnimation();
        }
        if (keyboardLayout.getBackground() != null)
            keyboardLayout.getBackground().setAlpha(0);

        if (largeCandidateButton.isShown()) largeCandidateButton.setVisibility(View.GONE);
        if (preEdit.isShown()) preEdit.setVisibility(View.GONE);
        if (quickSymbolViewGroup.isShown()) {
            quickSymbolViewGroup.hide();
            quickSymbolViewGroup.startAnimation(R.anim.key_1_out);
        }
        if (specialSymbolChooseViewGroup.isShown()) {
            specialSymbolChooseViewGroup.startAnimation(R.anim.key_1_out);
            specialSymbolChooseViewGroup.setVisibility(View.GONE);
        }
        preFixViewGroup.hide();
        functionViewGroup.starthideAnimation();
        bottomBarViewGroup.startHideAnimation();
        qkCandidatesViewGroup.setVisibility(View.GONE);

        lightViewManager.invisibleLightView();
        keyboardParams.flags = DISABLE_LAYOUTPARAMS_FLAG;
        if (keyboardLayout.isShown()) {
            WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
            wm.updateViewLayout(keyboardLayout, keyboardParams);
        }
        show = false;
    }

    @Override
    public View onCreateInputView() {
        Log.i("Test","onCreateInputView");
        return super.onCreateInputView();
    }

    /**
     * 启动输入法，在切换输入法时起作用，貌似安装后有一段时间不出来就是耗在这里
     * */
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        Log.i("Test","onStartInputView");
        initInputParam.initKernal(this.getApplicationContext());

        Global.inLarge = false;
        //设置enter_text
        BottomBarViewGroup.setEnterText(bottomBarViewGroup.enterButton, info, Global.currentKeyboard);
        /*
         * 获取中文键盘类型
		 */
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        zhKeyboard = sharedPreferences.getString("KEYBOARD_SELECTOR", "2").equals("1") ? Global.KEYBOARD_T9 : Global.KEYBOARD_QP;
        if (mWindowShown) {
            int keyboard = functionsC.getKeyboardType(info);
            if (Global.currentKeyboard != keyboard && info.inputType != 0) {
                keyBoardSwitcher.SwitchKeyboard(keyboard, true);
            }
            transparencyHandle.DownAlpha();
        }
        Global.keyboardRestTimeCount = 0;
        mHandler.removeMessages(MSG_DOUBLE_CLICK_REFRESH);
        mHandler.removeMessages(MSG_KERNEL_CLEAN);
        mHandler.sendEmptyMessageDelayed(MSG_DOUBLE_CLICK_REFRESH, 0);
        mHandler.sendEmptyMessageDelayed(MSG_KERNEL_CLEAN, maxFreeKernelTime * Global.metaRefreshTime);
        super.onStartInputView(info, restarting);
    }

    /**
     * 弹出输入框
     * */
    @Override
    public void onWindowShown() {
        Log.i("Test","onWindowShown");
        MobclickAgent.onResume(this);
        mWindowShown = true;
        mHandler.removeMessages(MSG_HIDE);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Global.mCurrentAlpha = sp.getFloat("CURRENT_ALPHA", 1f);
        Global.shadowRadius = Integer.parseInt(sp.getString("SHADOW_TEXT_RADIUS", "5"));
        Global.slideDeleteSwitch = sp.getBoolean("SLIDE_DELETE_CHECK", true);
        skinUpdateC.updateShadowLayer();
        viewManagerC.addInputView();
        lightViewManager.removeView();//真是种lowB方式，为了保证光线片在键盘之上也是醉了……，问题是wm就没有别的方式来保证啊
        lightViewManager.addToWindow();
        mInputViewGG.setVisibility(View.VISIBLE);

        try {
            quickSymbolViewGroup.updateSymbolsFromFile();
        } catch (IOException e) {
            Global.showToast(this, "Sorry,There is an error when program load symbols from file");
        }

        EditorInfo info = this.getCurrentInputEditorInfo();
        keyBoardSwitcher.SwitchKeyboard(functionsC.getKeyboardType(info), false);
        //显示键盘出场动画
        startAnimation();
        //设置空格键文字
        bottomBarViewGroup.spaceButton.setText(Global.halfToFull(sp.getString("ZH_SPACE_TEXT", "空格")));
        //更新当前键盘皮肤
        skinUpdateC.updateSkin();
        //音效设置加载
        keyBoardTouchEffect.loadSetting(sp);
        super.onWindowShown();
    }

    /**
     * 回收输入框
     * */
    @Override
    public void onWindowHidden() {
        Log.i("Test","onWindowHidden");
        MobclickAgent.onPause(this);
        mWindowShown = false;
        WIInputMethodNK.CLeanKernel();
        WIInputMethod.CLeanKernel();
        refreshDisplay();
        t9InputViewGroup.updateFirstKeyText();
        startOutAnimation();
        if (mSetKeyboardSizeViewOn) {
            mOnSizeChangeListener.onFinishSetting();
        }
        super.onWindowHidden();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i("Test","onKeyUp");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mSetKeyboardSizeViewOn) {
                mOnSizeChangeListener.onFinishSetting();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i("Test","onKeyDown");
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            char key = (char) ('a' + keyCode - KeyEvent.KEYCODE_A);
            WIInputMethodNK.GetAllWords(key + "");
            refreshDisplay();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && WIInputMethodNK.GetWordsNumber() > 0) {
            WIInputMethodNK.DeleteAction();
            refreshDisplay();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
