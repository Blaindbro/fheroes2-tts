package org.fheroes2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

// --- ACCESSIBILITY IMPORTS ---
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.content.Context;
// -----------------------------

import org.apache.commons.io.IOUtils;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity
{
    private static Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate( final Bundle savedInstanceState )
    {
        final File filesDir = getFilesDir();
        final File externalFilesDir = getExternalFilesDir( null );

        if ( isAssetsDigestChanged( "assets.digest", new File( filesDir, "assets.digest" ) ) ) {
            try {
                extractAssets( "files", externalFilesDir );
                extractAssets( "maps", externalFilesDir );
                extractAssets( "assets.digest", filesDir );
            }
            catch ( final Exception ex ) {
                Log.e( "fheroes2", "Failed to extract assets.", ex );
            }
        }

        super.onCreate( savedInstanceState );

        // Сообщение о подключении патча при запуске
        sendToScreenReader("Accessibility patch connected. F-Heroes 2 is ready.");

        if ( !HoMM2AssetManagement.isHoMM2AssetsPresent( externalFilesDir ) ) {
            startActivity( new Intent( this, ToolsetActivity.class ) );
            finish();
        }
    }

    // --- ОБРАБОТКА КАСАНИЙ ДЛЯ ДОСТУПНОСТИ ---
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE || 
            event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mSurface != null) {
                mSurface.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
            if (mSurface != null) {
                mSurface.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }
    // ------------------------------------------

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        System.exit( 0 );
    }

    // --- JNI МОСТ ДЛЯ C++ ---
    public static void sendToScreenReader( final String text )
    {
        if ( text == null || text.isEmpty() ) return;

        uiHandler.post( new Runnable() {
            @Override
            public void run() {
                if ( mSurface != null ) {
                    mSurface.announceForAccessibility( text );
                    Log.i("fheroes2_acc", "Speaking: " + text);
                }
            }
        });
    }

    // Оставшиеся методы (isAssetsDigestChanged, extractAssets, getAssetsPaths) оставляем без изменений...
    // [Вставьте сюда ваши оригинальные методы из предыдущего сообщения]
}
