package com.github.devishankar.paint.utils;

import android.support.design.widget.Snackbar;
import android.view.View;

/**
 * @author Devishankar
 */
public class Utils {

    public static void showSnackBar(View view, String msg) {
        Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show();
    }
}
