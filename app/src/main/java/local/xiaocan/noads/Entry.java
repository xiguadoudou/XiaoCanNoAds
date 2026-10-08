package local.xiaocan.noads;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Rules inspected in XiaoCan 3.21.2 (2985). No SDK-wide or system-wide hooks. */
public final class Entry implements IXposedHookLoadPackage {
    private static final String PKG = "com.realtech.xiaocan";
    private static final String TAG = "[XiaoCanNoAds] ";
    private static final String BASE = PKG + ".BaseAdActivity";
    private static final String SERVICE = PKG + ".service.SigMobAdServiceImpl";
    private static final String SIGMOB = PKG + ".sigmob.SigMobUtils";
    private static final String AM = "com.realtech.am_sdk_plugin.AmInterstitialAdHandler";
    private static final String WIND = "com.windmill.windmill_ad_plugin.interstitial.InterstitialAd";
    private static final String HOME = "com.realtech.promotion.pages.home.HomeFragment";
    private static final String MALL = PKG + ".fragment.SecondTabPlacementFragment";
    private static final String HOME_VM = "com.realtech.promotion.pages.home.viewmodel.HomeViewModel";
    private static final String SECOND_FLOOR = "com.realtech.promotion.pages.home.tab.ext.HomeSecondFloorKt";
    private static final String HOME_EFFECT = HOME + "$createObserver$30";
    private static final String ACTIVITY_BANNER = "com.realtech.promotion.pages.home.ext.HomeActivityBannerExtKt";
    private static final String HOME_FAB = "com.realtech.promotion.pages.home.ext.HomeFabExtKt";
    private static final String COMPETITOR = "com.realtech.promotion.service.CompetitorChallengeServiceImpl";
    private static final String BQT = PKG + ".ad.BqtAdServiceImpl";
    private static final String HX = PKG + ".ad.HxAdServiceImpl";
    private static final String TOBID_NATIVE = PKG + ".service.TobidNativeAdServiceImpl";
    private static final Set<String> TARGETS = Collections.unmodifiableSet(
        new java.util.HashSet<String>(Arrays.asList(BASE, SERVICE, SIGMOB, AM, WIND, HOME, MALL, HOME_VM,
            SECOND_FLOOR, HOME_EFFECT, ACTIVITY_BANNER, HOME_FAB, COMPETITOR, BQT, HX, TOBID_NATIVE)));
    private final Set<Class<?>> installed = Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());
    private final ThreadLocal<Boolean> installing = new ThreadLocal<Boolean>();
    private final Set<Method> hooked = Collections.newSetFromMap(new java.util.HashMap<Method, Boolean>());

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName) || !PKG.equals(lp.processName)) return;
        log("loaded v0.4, process=" + lp.processName);
        // Install before the protector loads real application classes. Exact name allowlist only.
        XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || Boolean.TRUE.equals(installing.get())) return;
                Object c = p.getResult();
                if (c instanceof Class && TARGETS.contains(((Class<?>) c).getName())) install((Class<?>) c);
            }
        });
        // attach() returns after ShellApplication.attachBaseContext and its native loader.
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                installAvailable(((Context) p.args[0]).getClassLoader());
            }
        });
        installAvailable(lp.classLoader);
    }

    private void installAvailable(ClassLoader loader) {
        for (String name : TARGETS) {
            try { install(Class.forName(name, false, loader)); }
            catch (Throwable ignored) { /* Protected classes may become available later. */ }
        }
    }

    private synchronized void install(Class<?> c) {
        if (installed.contains(c) || Boolean.TRUE.equals(installing.get())) return;
        installing.set(Boolean.TRUE);
        try {
            String n = c.getName();
            if (BASE.equals(n)) {
                // Retain consent, deep-link routing, lifecycle and preload gate cleanup.
                hook(c, "loadAd", 0, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            XposedHelpers.callMethod(p.thisObject, "requestExit", "lsposed:no-ad");
                            p.setResult(null);
                            log("blocked splash; requested normal exit");
                        } catch (Throwable e) { log("splash hook failed; original retained: " + e); }
                    }
                });
            } else if (SERVICE.equals(n) || SIGMOB.equals(n)) {
                hook(c, SERVICE.equals(n) ? "loadInterstitialAd" : "load", 2, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        final Object callback = p.args[1];
                        p.setResult(null);
                        // Use a genuine failure path; never report reward completion.
                        onMain(new Runnable() { public void run() {
                            if (callback != null) safeCall(callback, "onError", "Interstitial disabled by XiaoCanNoAds");
                        }});
                        log("blocked native interstitial");
                    }
                });
                if (SIGMOB.equals(n)) {
                    hook(c, "preload", 2, skip("interstitial preload"));
                    hook(c, "showInterstitialHost", 0, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            final Object ad = p.thisObject;
                            p.setResult(null);
                            onMain(new Runnable() { public void run() {
                                safeCall(ad, "notifyError", "Interstitial disabled by XiaoCanNoAds");
                            }});
                        }
                    });
                }
            } else if (AM.equals(n)) {
                hook(c, "onMethodCall", 2, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(final MethodHookParam p) {
                        String method = (String) XposedHelpers.getObjectField(p.args[0], "method");
                        if (!"load".equals(method) && !"show".equals(method)) return;
                        Object result = p.args[1];
                        safeCall(result, "error", "AD_BLOCKED", "Interstitial disabled", null);
                        final Object owner = p.thisObject;
                        p.setResult(null);
                        onMain(new Runnable() { public void run() {
                            try { emit(XposedHelpers.getObjectField(owner, "channel"), "onAdFailed"); }
                            catch (Throwable e) { log("AM event: " + e); }
                        }});
                        log("blocked AM Flutter interstitial " + method);
                    }
                });
            } else if (WIND.equals(n)) {
                // Keep MethodChannel execution/result handling and instance setup intact.
                for (final String method : new String[]{"load", "showAd"}) {
                    hook(c, method, 1, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            final Object owner = p.thisObject;
                            p.setResult(null);
                            onMain(new Runnable() { public void run() {
                                try { emit(XposedHelpers.getObjectField(owner, "adChannel"),
                                    "load".equals(method) ? "onAdFailedToLoad" : "onAdShowError"); }
                                catch (Throwable e) { log("ToBid event: " + e); }
                            }});
                            log("blocked ToBid Flutter interstitial " + method);
                        }
                    });
                }
                hook(c, "isReady", 1, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult(Boolean.FALSE); }
                });
            } else if (HOME.equals(n)) {
                // Skip before the promotional popup is added to DialogUtils' task queue.
                hook(c, "showSharerHomePopup", 1, skip("home promotional popup"));
            } else if (HOME_EFFECT.equals(n)) {
                // Intercept only unsolicited promotion effects, preserving manually opened features.
                hook(c, "emit", 2, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args[0] == null) return;
                        String effect = p.args[0].getClass().getName();
                        String prefix = "com.realtech.promotion.pages.home.HomeEffect$";
                        if (!effect.equals(prefix + "ShowActiveVipDialog") &&
                            !effect.equals(prefix + "ShowFullRefundWheelDialog") &&
                            !effect.equals(prefix + "ShowFreeLotteryDialog") &&
                            !effect.equals(prefix + "ShowMAChallengeDialog") &&
                            !effect.equals(prefix + "ShowFullRefundSecRedPackExpiringDialog")) return;
                        try {
                            Object unit = XposedHelpers.getStaticObjectField(
                                XposedHelpers.findClass("kotlin.Unit", p.thisObject.getClass().getClassLoader()), "INSTANCE");
                            if (effect.equals(prefix + "ShowFullRefundWheelDialog") || effect.equals(prefix + "ShowFreeLotteryDialog")) {
                                Object fragment = XposedHelpers.getObjectField(p.thisObject, "this$0");
                                XposedHelpers.callMethod(fragment, "completePostLoginLotteryDecision", Boolean.FALSE);
                            }
                            p.setResult(unit);
                            log("blocked automatic effect " + effect);
                        } catch (Throwable e) { log("promotion effect failed; original retained: " + e); }
                    }
                });
            } else if (ACTIVITY_BANNER.equals(n)) {
                hook(c, "renderHomeActivityBanner", 2, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        // The app's empty-list branch stops loops, cancels delayed restores and hides the view.
                        p.args[1] = Collections.emptyList();
                        log("cleared floating promotional activity banner");
                    }
                });
            } else if (HOME_FAB.equals(n)) {
                hook(c, "initNewUserFabView$lambda$6", 2, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable()) return;
                        try {
                            Object binding = XposedHelpers.callMethod(p.args[0], "getBinding");
                            Object fab = XposedHelpers.getObjectField(binding, "layoutFab");
                            ((View) XposedHelpers.getObjectField(fab, "rainFab")).setVisibility(View.GONE);
                        } catch (Throwable e) { log("rain widget: " + e); }
                    }
                });
            } else if (COMPETITOR.equals(n)) {
                hook(c, "shouldCheckHomeActivityPop", 0, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult(Boolean.FALSE); }
                });
            } else if (BQT.equals(n) || HX.equals(n) || TOBID_NATIVE.equals(n)) {
                String load = TOBID_NATIVE.equals(n) ? "loadAndShowIntoContainer" : "loadAndShow";
                int count = BQT.equals(n) ? 4 : HX.equals(n) ? 3 : 6;
                hook(c, load, count, nativeAdFailure(count - 1, TOBID_NATIVE.equals(n)));
                if (HX.equals(n)) {
                    hook(c, "preload", 3, nativeAdFailure(2, false));
                    hook(c, "show", 2, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult(Boolean.FALSE); }
                    });
                }
            } else if (HOME_VM.equals(n)) {
                // initRefresh() uses MaterialHeader and the existing OnRefreshListener
                // when this eligibility gate is false; openLynxSecondFloor also checks it.
                hook(c, "canUseHomeSecondFloor", 0, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(Boolean.FALSE);
                    }
                });
            } else if (SECOND_FLOOR.equals(n)) {
                hook(c, "checkSceondDFloorGuide", 1, skip("second-floor advertising guide"));
            } else if (MALL.equals(n)) {
                // Only these two advertising prompts; no generic Dialog/Popup interception.
                hook(c, "enqueueFirstGuideDialogIfNeeded", 0, skip("mall first-guide popup"));
                hook(c, "enqueueBonusDialogIfNeeded", 1, skip("mall bonus promotional popup"));
            }
            installed.add(c);
        } catch (Throwable e) { log("install failed " + c.getName() + ": " + e); }
        finally { installing.remove(); }
    }

    private void hook(Class<?> c, String name, int argc, XC_MethodHook callback) {
        boolean found = false;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == argc) {
                found = true;
                if (hooked.contains(m)) continue;
                XposedBridge.hookMethod(m, callback);
                hooked.add(m);
                log("hooked " + c.getName() + "." + name);
            }
        }
        if (!found) log("missing hook " + c.getName() + "." + name);
    }
    private static XC_MethodHook skip(final String what) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult(null); log("blocked " + what); }
        };
    }
    private static XC_MethodHook nativeAdFailure(final int listenerIndex, final boolean tobid) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                final Object listener = p.args[listenerIndex];
                p.setResult(null);
                onMain(new Runnable() { public void run() {
                    if (listener == null) return;
                    if (tobid) safeCall(listener, "onResult", Boolean.FALSE);
                    else safeCall(listener, "onAdLoadedFailure");
                }});
                log("blocked native advertising container load");
            }
        };
    }
    private static void onMain(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
    private static void emit(Object channel, String event) {
        if (channel == null) return;
        Map<String, Object> error = new HashMap<String, Object>();
        error.put("code", Integer.valueOf(-1)); error.put("message", "Interstitial disabled by XiaoCanNoAds");
        safeCall(channel, "invokeMethod", event, error);
    }
    private static void safeCall(Object object, String method, Object... args) {
        try { XposedHelpers.callMethod(object, method, args); }
        catch (Throwable e) { log("callback " + method + ": " + e); }
    }
    private static void log(String message) { XposedBridge.log(TAG + message); }
}
