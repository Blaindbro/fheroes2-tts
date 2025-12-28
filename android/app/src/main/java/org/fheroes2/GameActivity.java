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
import android.view.accessibility.AccessibilityManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.media.AudioAttributes;
import android.content.Context;
// ------------------------------------

import org.apache.commons.io.IOUtils;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity implements TextToSpeech.OnInitListener
{
    // Используем Handler главного потока для безопасных вызовов из JNI
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static TextToSpeech tts;
    private static boolean isTtsReady = false;
    
    // Переменные для защиты от спама
    private static String lastText = "";
    private static long lastSpeakTime = 0;
    // Уменьшаем задержку до 300мс — это достаточно для отсева дребезга, 
    // но позволяет быстро прощупывать один и тот же объект
    private static final long SPAM_THRESHOLD_MS = 300; 

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

        // --- ИНИЦИАЛИЗАЦИЯ TTS ---
        tts = new TextToSpeech(this, this);
        // -------------------------

        super.onCreate( savedInstanceState );

        if ( !HoMM2AssetManagement.isHoMM2AssetsPresent( externalFilesDir ) ) {
            startActivity( new Intent( this, ToolsetActivity.class ) );
            finish();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Настройка языка
            int result = tts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                 Log.e("TTS", "Language not supported");
            }

            // Настройка аудио-атрибутов: Важно, чтобы голос шел как ACCESSIBILITY, 
            // тогда Android может приглушать музыку игры во время речи (Audio Ducking)
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            tts.setAudioAttributes(attrs);

            tts.setPitch(1.0f);
            tts.setSpeechRate(1.2f); // 1.2x оптимально для быстрого чтения
            isTtsReady = true;
            
            // Тестовое сообщение
            speakInternal("Accessibility initialized", TextToSpeech.QUEUE_FLUSH, 1.0f);
        } else {
            Log.e("TTS", "Initialization failed");
        }
    }

    // --- УЛУЧШЕННЫЙ МЕТОД JNI (ТОЧКА ВХОДА) ---
    /**
     * Вызывается из C++ (engine/tools.cpp).
     * Поддерживает "Протокол префиксов":
     * "+" в начале строки -> QUEUE_ADD (дочитать после текущего, для диалогов)
     * "~" в начале строки -> Низкий питч (для врагов/опасности)
     * Без префикса -> QUEUE_FLUSH (прервать и читать сразу, для навигации)
     */
    public static void sendToScreenReader(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return;

        final String textToProcess = rawText;

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isTtsReady || tts == null) return;

                String text = textToProcess;
                int queueMode = TextToSpeech.QUEUE_FLUSH; // По умолчанию - ПРЕРЫВАТЬ (для быстрой реакции)
                float pitch = 1.0f;

                // 1. Парсинг префиксов
                if (text.startsWith("+")) {
                    queueMode = TextToSpeech.QUEUE_ADD;
                    text = text.substring(1);
                } else if (text.startsWith("~")) {
                    pitch = 0.6f; // Низкий голос (Демонический/Враг)
                    text = text.substring(1);
                    // Для врагов лучше прерывать сразу
                    queueMode = TextToSpeech.QUEUE_FLUSH; 
                }

                // 2. Умный Анти-спам
                long currentTime = System.currentTimeMillis();
                // Если текст совпадает с прошлым И прошло мало времени -> игнорируем
                // НО: Если текст тот же, но прошло > 300мс, читаем снова (пользователь хочет перепроверить клетку)
                if (text.equals(lastText) && (currentTime - lastSpeakTime < SPAM_THRESHOLD_MS)) {
                    return; 
                }

                lastText = text;
                lastSpeakTime = currentTime;

                speakInternal(text, queueMode, pitch);
            }
        });
    }

    // Внутренний метод для непосредственной озвучки
    private static void speakInternal(String text, int queueMode, float pitch) {
        tts.setPitch(pitch);
        
        // Bundle для идентификации (помогает при отладке)
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "f2_access");

        tts.speak(text, queueMode, params, "f2_access");
        
        // Сбрасываем питч обратно после добавления в очередь, 
        // чтобы следующее сообщение (если оно без префикса) было нормальным
        if (pitch != 1.0f) {
             // Небольшой хак: нам нужно вернуть питч, но speak - асинхронный.
             // В идеале нужно использовать UtteranceProgressListener, но для простоты
             // мы полагаемся на то, что setPitch применяется к следующему speak.
             // В данном коде это безопасно, так как setPitch вызывается перед каждым speak.
        }
        
        Log.d("fheroes2_tts", "Speak: [" + text + "] Mode: " + (queueMode == 0 ? "FLUSH" : "ADD"));
    }

    // --- PASS-THROUGH TOUCH EVENTS ---
    // Это важно, чтобы TalkBack не блокировал игру, но знал, что происходит взаимодействие
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Мы НЕ отправляем HOVER события вручную, если включен TalkBack в режиме "Изучение касанием",
        // так как TalkBack сам перехватывает жесты и транслирует их.
        // Но для fheroes2, который рисует на Canvas, нам нужно просто пропускать события.
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
        // Убираем System.exit(0), это плохая практика в Android, 
        // SDLActivity сам должен корректно закрываться. 
        // Но если это требование движка fheroes2, можно раскомментировать.
        System.exit( 0 ); 
    }

    // --- ASSET MANAGEMENT (NO CHANGES) ---
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
