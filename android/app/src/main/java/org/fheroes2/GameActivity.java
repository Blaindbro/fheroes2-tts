package org.fheroes2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

// --- ACCESSIBILITY & TTS IMPORTS ---
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.speech.tts.TextToSpeech;
// ------------------------------------

import org.apache.commons.io.IOUtils;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity implements TextToSpeech.OnInitListener
{
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static TextToSpeech tts;
    private static boolean isTtsReady = false;
    
    // Для предотвращения зацикливания и спама одинаковыми звуками
    private static String lastText = "";
    private static long lastSpeakTime = 0;

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

        // Инициализация TTS
        tts = new TextToSpeech(this, this);

        super.onCreate( savedInstanceState );

        if ( !HoMM2AssetManagement.isHoMM2AssetsPresent( externalFilesDir ) ) {
            startActivity( new Intent( this, ToolsetActivity.class ) );
            finish();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            tts.setPitch(1.0f);
            tts.setSpeechRate(1.1f); // Чуть быстрее для удобства
            isTtsReady = true;
            sendToScreenReader("Accessibility patch is active.");
        }
    }

    // --- УЛУЧШЕННЫЙ ПЕРЕХВАТ ЖЕСТОВ ---
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mSurface != null) {
            // Генерируем HOVER события, чтобы TalkBack понимал, что мы "исследуем" экран
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                mSurface.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    // --- ОСНОВНОЙ МЕТОД ОЗВУЧКИ ---
    public static void sendToScreenReader(final String text) {
        if (text == null || text.trim().isEmpty()) return;

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                
                // Фильтр: не повторять то же самое чаще чем раз в 1.5 сек (защита от спама статусбара)
                if (text.equals(lastText) && (currentTime - lastSpeakTime < 1500)) {
                    return; 
                }

                if (isTtsReady && tts != null) {
                    lastText = text;
                    lastSpeakTime = currentTime;
                    
                    // QUEUE_ADD — сообщения встают в очередь и не прерываются
                    tts.speak(text, TextToSpeech.QUEUE_ADD, null, "f2_tts_out");
                    Log.d("fheroes2_acc", "Spoken: " + text);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
        System.exit( 0 );
    }

    // --- СТАНДАРТНЫЕ МЕТОДЫ FHEROES2 (БЕЗ ИЗМЕНЕНИЙ) ---
    @SuppressWarnings( "SameParameterValue" )
    private boolean isAssetsDigestChanged( final String assetsDigestPath, final File localDigestFile )
    {
        try ( final InputStream assetsDigestStream = getAssets().open( assetsDigestPath ) ) {
            try ( final InputStream localDigestStream = Files.newInputStream( localDigestFile.toPath() ) ) {
                if ( Arrays.equals( IOUtils.toByteArray( assetsDigestStream ), IOUtils.toByteArray( localDigestStream ) ) ) {
                    return false;
                }
                Log.i( "fheroes2", "Digest of assets has been changed." );
            } catch ( final Exception ex ) {
                Log.i( "fheroes2", "Failed to access the local digest.", ex );
            }
        } catch ( final Exception ex ) {
            Log.e( "fheroes2", "Failed to access the digest of assets.", ex );
        }
        return true;
    }

    private void extractAssets( final String srcPath, final File dstDir ) throws IOException
    {
        for ( final String path : getAssetsPaths( srcPath ) ) {
            try ( final InputStream in = getAssets().open( path ) ) {
                final File outFile = new File( dstDir, path );
                final File outFileDir = outFile.getParentFile();
                if ( outFileDir != null ) Files.createDirectories( outFileDir.toPath() );
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
        if ( assets == null ) return result;
        if ( assets.length == 0 ) {
            result.add( path );
            return result;
        }
        for ( final String asset : assets ) {
            result.addAll( getAssetsPaths( path + File.separator + asset ) );
        }
        return result;
    }
}
