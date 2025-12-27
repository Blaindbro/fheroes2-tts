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

// --- ACCESSIBILITY IMPORTS START ---
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
// --- ACCESSIBILITY IMPORTS END ---

import org.apache.commons.io.IOUtils;

import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity
{
    // Статический хендлер, чтобы не создавать новые объекты при каждом сообщении статусбара
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate( final Bundle savedInstanceState )
    {
        final File filesDir = getFilesDir();
        final File externalFilesDir = getExternalFilesDir( null );

        if ( isAssetsDigestChanged( "assets.digest", new File( filesDir, "assets.digest" ) ) ) {
            try {
                extractAssets( "files", externalFilesDir );
                extractAssets( "maps", externalFilesDir );
                // Digest should be updated only after successful extraction of all assets
                extractAssets( "assets.digest", filesDir );
            }
            catch ( final Exception ex ) {
                Log.e( "fheroes2", "Failed to extract assets.", ex );
            }
        }

        super.onCreate( savedInstanceState );

        // If the minimum set of game assets has not been found, run the toolset activity instead
        if ( !HoMM2AssetManagement.isHoMM2AssetsPresent( externalFilesDir ) ) {
            startActivity( new Intent( this, ToolsetActivity.class ) );

            // Replace this activity with the newly launched activity
            finish();
        } else {
            // Озвучка при успешном запуске игры
            sendToScreenReader("Accessibility patch is connected. Welcome to Heroes 2.");
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        System.exit( 0 );
    }

    // --- ACCESSIBILITY TOUCH INTERCEPTION START ---
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Прокидываем события касания в систему доступности
        if (mSurface != null && (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE)) {
            mSurface.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Поддержка ховер-событий (для стилусов или TalkBack навигации)
        if (mSurface != null && event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
            mSurface.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
        return super.dispatchGenericMotionEvent(event);
    }
    // --- ACCESSIBILITY TOUCH INTERCEPTION END ---

    @SuppressWarnings( "SameParameterValue" )
    private boolean isAssetsDigestChanged( final String assetsDigestPath, final File localDigestFile )
    {
        try ( final InputStream assetsDigestStream = getAssets().open( assetsDigestPath ) ) {
            try ( final InputStream localDigestStream = Files.newInputStream( localDigestFile.toPath() ) ) {
                if ( Arrays.equals( IOUtils.toByteArray( assetsDigestStream ), IOUtils.toByteArray( localDigestStream ) ) ) {
                    return false;
                }

                Log.i( "fheroes2", "Digest of assets has been changed." );
            }
            catch ( final Exception ex ) {
                Log.i( "fheroes2", "Failed to access the local digest. Considering the digest of assets as changed.", ex );
            }
        }
        catch ( final Exception ex ) {
            Log.e( "fheroes2", "Failed to access the digest of assets. Considering the digest of assets as changed.", ex );
        }

        return true;
    }

    private void extractAssets( final String srcPath, final File dstDir ) throws IOException
    {
        for ( final String path : getAssetsPaths( srcPath ) ) {
            try ( final InputStream in = getAssets().open( path ) ) {
                final File outFile = new File( dstDir, path );

                final File outFileDir = outFile.getParentFile();
                if ( outFileDir != null ) {
                    Files.createDirectories( outFileDir.toPath() );
                }

                try ( final OutputStream out = Files.newOutputStream( outFile.toPath() ) ) {
                    IOUtils.copy( in, out );
                }
            }
        }
    }

    private List<String> getAssetsPaths( final String path ) throws IOException
    {
        final List<String> result = new ArrayList<>();

        final String[] assets = getAssets().list( path );

        if ( assets == null ) {
            return result;
        }

        if ( assets.length == 0 ) {
            result.add( path );

            return result;
        }

        for ( final String asset : assets ) {
            result.addAll( getAssetsPaths( path + File.separator + asset ) );
        }

        return result;
    }

    // --- ACCESSIBILITY CODE START ---
    
    public static void sendToScreenReader( final String text )
    {
        if ( text == null || text.isEmpty() ) {
            return;
        }

        // Используем статический хендлер для отправки в UI-поток
        uiHandler.post( new Runnable() {
            @Override
            public void run()
            {
                if ( mSurface != null ) {
                    mSurface.announceForAccessibility( text );
                    // Лог для проверки в терминале через adb logcat
                    Log.d("fheroes2_acc", "TTS Message: " + text);
                }
            }
        } );
    }
    // --- ACCESSIBILITY CODE END ---
}
