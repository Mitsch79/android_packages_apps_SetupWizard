/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.content.ContextWrapper;
import android.view.View;

import com.google.android.setupdesign.GlifLayout;

/** Controls belonging to one page inside the setup window. */
abstract class WizardPane extends ContextWrapper {
    final BaseSetupWizardActivity host;
    GlifLayout layout;
    View body;

    WizardPane(BaseSetupWizardActivity host) {
        super(host);
        this.host = host;
    }

    protected abstract int getLayoutResId();
    protected abstract int getTitleResId();
    protected abstract int getIconResId();
    protected abstract void bind();
    protected void refresh() { }

    protected final <T extends View> T findViewById(int id) {
        return body.findViewById(id);
    }

    protected final GlifLayout getGlifLayout() {
        return layout;
    }

    protected void onNextPressed() {
        host.nextAction(android.app.Activity.RESULT_OK);
    }

    protected void onSkipPressed() {
        host.nextAction(com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP);
    }
}
