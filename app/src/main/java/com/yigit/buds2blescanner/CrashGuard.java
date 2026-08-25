package com.yigit.buds2blescanner;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Captures fatal startup crashes so a device-side launch failure can be diagnosed. */
public final class CrashGuard {
    private CrashGuard() {}
    public static void install(Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                File dir = new File(context.getFilesDir(), "diagnostics");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, "last_crash.txt");
                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                StringBuilder out = new StringBuilder();
                out.append("Galaxy Buds2 Manager crash\n");
                out.append("time=").append(stamp).append('\n');
                out.append("thread=").append(thread.getName()).append('\n');
                out.append("android=").append(android.os.Build.VERSION.RELEASE).append('\n');
                out.append("sdk=").append(android.os.Build.VERSION.SDK_INT).append('\n');
                out.append("device=").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n');
                out.append("\n");
                java.io.StringWriter sw = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(sw));
                out.append(sw);
                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(out.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
