package com.anapocket.auto;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Test APK only; platform Java so this separate process requires no target APK runtime. */
public class AdFixtureActivity extends Activity {
    private void screen(String text, String action, Runnable click) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 100, 32, 32);
        TextView title = new TextView(this);
        title.setText("CONTROLLED TEST FIXTURE / NO REAL POINTS"); title.setTextSize(20f); layout.addView(title);
        TextView body = new TextView(this); body.setText(text); body.setTextSize(24f); layout.addView(body);
        if (action != null) {
            Button button = new Button(this); button.setText(action);
            button.setOnClickListener(v -> { if (click != null) click.run(); }); layout.addView(button);
        }
        setContentView(layout);
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screen("今日の視聴済み回数 0/25", "視聴する", () -> {
            if (getIntent().getBooleanExtra("networkFailure", false)) {
                screen("通信エラー", "OK", () -> screen("fixture stopped after error", null, null)); return;
            }
            screen("Ad 1 of 1 / simulated playback", null, null);
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                screen("Ad 1 of 1 / finished", "閉じる", () ->
                    screen("報酬をゲット！", "OK", () -> screen("今日の視聴済み回数 1/25", null, null))), 1600L);
        });
    }
}
