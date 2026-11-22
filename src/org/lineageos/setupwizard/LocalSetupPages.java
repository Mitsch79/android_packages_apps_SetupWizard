/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserManager;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.android.settingslib.Utils;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.GlifLayout;

import java.util.ArrayList;

import org.lineageos.setupwizard.backup.RestoreIntroActivity;

/** Keeps the script intents as the back stack and draws local steps into one layout. */
final class LocalSetupPages {
    private final BaseSetupWizardActivity host;
    private final FrameLayout window;
    private final ArrayList<View> covered = new ArrayList<>();
    private final ArrayList<Intent> history = new ArrayList<>();
    private final OnBackPressedCallback back = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() {
            if (confirmingSkip) {
                setConfirmingSkip(false);
                return;
            }
            history.remove(history.size() - 1);
            redisplay();
        }
    };
    private GlifLayout shell;
    private WizardPane current;
    private String currentName;
    private boolean awaitingLock;
    private boolean showLockIntro;
    private boolean confirmingSkip;
    private View finishPage;
    private int preparedLayout;
    private View preparedView;
    private boolean sharedWithLanguage;
    private boolean covering;

    LocalSetupPages(BaseSetupWizardActivity activity) {
        host = activity;
        window = host.findViewById(android.R.id.content);
        host.getOnBackPressedDispatcher().addCallback(host, back);
    }

    void restore(Bundle state) {
        if (state != null) {
            ArrayList<Intent> saved = state.getParcelableArrayList("localPages", Intent.class);
            if (saved != null) history.addAll(saved);
            awaitingLock = state.getBoolean("awaitingLock");
            showLockIntro = state.getBoolean("showLockIntro");
            confirmingSkip = state.getBoolean("confirmSkip");
        }
        if (history.isEmpty() && host instanceof PaneActivity) {
            history.add(host.getIntent());
        }
        if (!history.isEmpty()) redisplay();
    }

    void save(Bundle state) {
        ArrayList<Intent> routes = new ArrayList<>(history.size());
        for (Intent step : history) {
            Intent route = step.cloneFilter();
            route.setFlags(step.getFlags());
            WizardManagerHelper.copyWizardManagerExtras(step, route);
            routes.add(route);
        }
        state.putParcelableArrayList("localPages", routes);
        state.putBoolean("awaitingLock", awaitingLock);
        state.putBoolean("showLockIntro", showLockIntro);
        state.putBoolean("confirmSkip", confirmingSkip);
    }

    Intent intent() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    private WizardPane page(Intent step) {
        ComponentName cls = step.resolveActivity(host.getPackageManager());
        if (cls == null || !host.getPackageName().equals(cls.getPackageName())) return null;
        String name = cls.getClassName();
        if (RestoreIntroActivity.class.getName().equals(name)) return new RestorePage(host);
        if (LocationSettingsActivity.class.getName().equals(name)) return new LocationSettingsActivity.Page(host);
        if (LineageSettingsActivity.class.getName().equals(name)) return new LineageSettingsActivity.Page(host);
        if (ThemeSettingsActivity.class.getName().equals(name)) return new ThemeSettingsActivity.Page(host);
        if (NavigationSettingsActivity.class.getName().equals(name)) return new NavigationSettingsActivity.Page(host);
        return null;
    }

    boolean open(Intent step) {
        if (isFinish(step) && shell != null) {
            history.add(step);
            displayFinish();
            return true;
        }
        if (isLock(step) && new Intent(ScreenLockActivity.ACTION_SETUP_LOCKSCREEN)
                .resolveActivity(host.getPackageManager()) != null) {
            history.add(step);
            showLockIntro = canOfferOptionalLock();
            if (showLockIntro) display(new LockPage());
            else openLock(step);
            return true;
        }
        WizardPane pane = page(step);
        if (pane == null) return false;
        history.add(step);
        display(pane);
        return true;
    }

    private boolean isLock(Intent step) {
        return new ComponentName(host, ScreenLockActivity.class)
                .equals(step.resolveActivity(host.getPackageManager()));
    }

    private boolean isFinish(Intent step) {
        return new ComponentName(host, FinishActivity.class)
                .equals(step.resolveActivity(host.getPackageManager()));
    }

    private boolean canOfferOptionalLock() {
        UserManager users = host.getSystemService(UserManager.class);
        DevicePolicyManager policy = host.getSystemService(DevicePolicyManager.class);
        KeyguardManager keyguard = host.getSystemService(KeyguardManager.class);
        if (users == null || policy == null || keyguard == null || !users.isSystemUser()
                || users.getUserProfiles().size() != 1 || keyguard.isDeviceSecure()
                || policy.isDeviceManaged() || policy.getPasswordQuality(null) != 0
                || Settings.Global.getInt(host.getContentResolver(),
                        Settings.Global.SECURE_FRP_MODE, 0) != 0) return false;
        var admins = policy.getActiveAdmins();
        return admins == null || admins.isEmpty();
    }

    private void finishOrConfirmSkip() {
        if (!canOfferOptionalLock()) {
            confirmingSkip = false;
            openLock(intent());
            return;
        }
        if (confirmingSkip) {
            confirmingSkip = false;
            host.nextAction(com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP);
        } else {
            setConfirmingSkip(true);
        }
    }

    private void setConfirmingSkip(boolean confirm) {
        confirmingSkip = confirm;
        current.bind();
        shell.getHeaderTextView().sendAccessibilityEvent(
                android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void displayFinish() {
        ensureShell();
        if (finishPage == null) {
            finishPage = inflatePage(R.layout.finish_activity, window);
            window.addView(finishPage);
            NavigationLayout nav = finishPage.findViewById(R.id.navigation_bar);
            nav.getNextButton().setText(R.string.start);
            nav.getNextButton().setOnClickListener(v -> FinishActivity.completeFrom(host, intent()));
        }
        currentName = FinishActivity.class.getName();
        if (shell != null) shell.setVisibility(View.INVISIBLE);
        finishPage.setVisibility(View.VISIBLE);
        back.setEnabled(true);
    }

    private void openLock(Intent step) {
        awaitingLock = true;
        try {
            host.launchLock(step);
        } catch (android.content.ActivityNotFoundException e) {
            lockResult(com.google.android.setupcompat.util.ResultCodes.RESULT_ACTIVITY_NOT_FOUND, null);
        }
    }

    void lockResult(int result, Intent data) {
        if (!awaitingLock || intent() == null || !isLock(intent())) return;
        awaitingLock = false;
        if (result == Activity.RESULT_CANCELED) {
            if (!showLockIntro) history.remove(history.size() - 1);
            redisplay();
        } else {
            host.advanceFrom(intent(), result, data);
        }
    }

    private void redisplay() {
        if (finishPage != null) finishPage.setVisibility(View.GONE);
        if (history.isEmpty()) {
            current = null;
            currentName = null;
            if (sharedWithLanguage) {
                shell.findViewById(R.id.wizard_page_body).setVisibility(View.GONE);
                NavigationLayout nav = shell.findViewById(R.id.navigation_bar);
                nav.getSkipButton().setVisibility(View.GONE);
                nav.setNavigationBarListener(host);
            } else if (shell != null) {
                shell.setVisibility(View.GONE);
            }
            for (View view : covered) view.setVisibility(View.VISIBLE);
            covered.clear();
            covering = false;
            if (sharedWithLanguage) ((LocaleActivity) host).restoreEarlyPage();
            back.setEnabled(false);
            return;
        }
        if (isFinish(intent())) {
            displayFinish();
        } else if (isLock(intent())) {
            if (showLockIntro) display(new LockPage());
            else if (!awaitingLock) openLock(intent());
        } else {
            display(page(intent()));
        }
    }

    void skip() {
        Intent skipped = intent();
        if (history.size() == 1 && host instanceof PaneActivity) {
            host.finishSkippedPane(skipped);
            return;
        }
        history.remove(history.size() - 1);
        redisplay();
        host.advanceFrom(skipped, Activity.RESULT_OK, null);
    }

    private void ensureShell() {
        if (shell == null) {
            sharedWithLanguage = host instanceof WelcomeActivity;
            if (sharedWithLanguage) {
                shell = host.getGlifLayout();
            } else {
                shell = (GlifLayout) host.getLayoutInflater().inflate(R.layout.wizard_page_shell, window, false);
                shell.setVisibility(View.GONE);
                window.addView(shell);
            }
            window.getViewTreeObserver().addOnDrawListener(() -> {
                if (currentName != null && intent() != null && Trace.isTagEnabled(Trace.TRACE_TAG_VIEW)) {
                    Trace.traceBegin(Trace.TRACE_TAG_VIEW, "WizardPane:" + currentName
                            + ":" + Choreographer.getInstance().getVsyncId());
                    Trace.traceEnd(Trace.TRACE_TAG_VIEW);
                }
            });
        }
    }

    private void display(WizardPane pane) {
        ensureShell();
        FrameLayout container = shell.findViewById(R.id.wizard_page_body);
        if (!covering) {
            ViewGroup parent = sharedWithLanguage ? (ViewGroup) container.getParent() : window;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child != shell && child != container && child.getId() != R.id.navigation_bar
                        && child.getVisibility() == View.VISIBLE) {
                    covered.add(child);
                    child.setVisibility(View.GONE);
                }
            }
            covering = true;
        }
        shell.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);
        current = pane;
        currentName = intent().resolveActivity(host.getPackageManager()).getClassName();
        container.removeAllViews();
        pane.body = inflatePage(pane.getLayoutResId(), container);
        pane.layout = shell;
        container.addView(pane.body);
        shell.setHeaderText(host.getText(pane.getTitleResId()));
        Drawable icon = host.getDrawable(pane.getIconResId()).mutate();
        icon.setTintList(Utils.getColorAccent(host));
        shell.setIcon(icon);
        shell.setDescriptionText(null);
        NavigationLayout nav = shell.findViewById(R.id.navigation_bar);
        nav.getSkipButton().setText(R.string.skip);
        nav.getSkipButton().setVisibility(pane instanceof RestorePage || pane instanceof LockPage
                ? View.VISIBLE : View.GONE);
        nav.getNextButton().setText(R.string.next);
        nav.setNavigationBarListener(new NavigationLayout.NavigationBarListener() {
            public void onNavigateNext() { pane.onNextPressed(); }
            public void onSkip() { pane.onSkipPressed(); }
        });
        back.setEnabled(history.size() > (host instanceof PaneActivity ? 1 : 0));
        pane.bind();
        if (current == pane) {
            TextView desc = shell.getDescriptionTextView();
            if (desc != null) desc.setVisibility(desc.length() == 0 ? View.GONE : View.VISIBLE);
            pane.refresh();
            if ("v500".equals(android.os.Build.DEVICE)) {
                if (pane instanceof RestorePage) {
                    preparePage(pane, R.layout.location_settings, container);
                } else if (pane instanceof ThemeSettingsActivity.Page) {
                    preparePage(pane, R.layout.setup_navigation, container);
                } else if (pane instanceof NavigationSettingsActivity.Page && finishPage == null) {
                    preparePage(pane, R.layout.finish_activity, window);
                }
            }
        }
    }

    private View inflatePage(int layout, FrameLayout parent) {
        if (preparedView == null || preparedLayout != layout) {
            return host.getLayoutInflater().inflate(layout, parent, false);
        }
        View view = preparedView;
        preparedView = null;
        return view;
    }

    private void preparePage(WizardPane source, int layout, FrameLayout parent) {
        if (preparedView != null && preparedLayout == layout) return;
        // Wait until the current page has submitted a frame. Bind settings
        // and add the next view to the accessibility tree only on navigation.
        window.getViewTreeObserver().registerFrameCommitCallback(() -> {
            // The restore page's body is a Space. Its visible header and
            // navigation live on the shell, while Space itself is invisible.
            if (host.isDestroyed() || host.isFinishing() || current != source
                    || !shell.isShown() || source.body.getParent() == null) return;
            preparedView = host.getLayoutInflater().inflate(layout, parent, false);
            preparedLayout = layout;
        });
    }

    void refresh() {
        if (current != null) current.refresh();
    }

    void configurationChanged() {
        preparedView = null;
        if (finishPage != null) {
            window.removeView(finishPage);
            finishPage = null;
        }
        if (!awaitingLock) redisplay();
    }

    private static final class RestorePage extends WizardPane {
        RestorePage(BaseSetupWizardActivity host) { super(host); }
        protected int getLayoutResId() { return R.layout.empty_restore_content; }
        protected int getTitleResId() { return R.string.intro_restore_title; }
        protected int getIconResId() { return R.drawable.ic_restore; }
        protected void bind() {
            layout.setDescriptionText(getString(R.string.intro_restore_subtitle, getString(R.string.os_name)));
        }
        protected void onNextPressed() { host.launchRestore(); }
    }

    private final class LockPage extends WizardPane {
        LockPage() { super(LocalSetupPages.this.host); }
        protected int getLayoutResId() { return R.layout.empty_restore_content; }
        protected int getTitleResId() { return R.string.protect_tablet_title; }
        protected int getIconResId() { return android.R.drawable.ic_lock_lock; }
        protected void bind() {
            currentName = confirmingSkip ? "LockSkipConfirmation" : ScreenLockActivity.class.getName();
            layout.setHeaderText(confirmingSkip ? R.string.skip_screen_lock_title : R.string.protect_tablet_title);
            layout.setDescriptionText(getString(confirmingSkip
                    ? R.string.skip_screen_lock_message : R.string.protect_tablet_description));
            NavigationLayout nav = layout.findViewById(R.id.navigation_bar);
            nav.getNextButton().setText(confirmingSkip ? R.string.skip : R.string.set_up_screen_lock);
            nav.getSkipButton().setText(confirmingSkip ? android.R.string.cancel : R.string.screen_lock_later);
        }
        protected void onNextPressed() {
            if (confirmingSkip) finishOrConfirmSkip();
            else if (!awaitingLock) openLock(intent());
        }
        protected void onSkipPressed() {
            if (confirmingSkip) setConfirmingSkip(false);
            else finishOrConfirmSkip();
        }
    }
}
