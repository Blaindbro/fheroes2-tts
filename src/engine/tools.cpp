/***************************************************************************
 * fheroes2: https://github.com/ihhub/fheroes2                           *
 * Copyright (C) 2019 - 2025                                             *
 ***************************************************************************/

#include "tools.h"

#include <algorithm>
#include <cassert>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <system_error>

#include <zconf.h>
#include <zlib.h>

// --- ACCESSIBILITY FIX START ---
#ifdef __ANDROID__
#include <jni.h>

// Объявляем функцию SDL здесь, глобально.
// extern "C" нужно, чтобы C++ понял, что это функция из C-библиотеки.
extern "C" void* SDL_AndroidGetJNIEnv();

#endif
// --- ACCESSIBILITY FIX END ---

std::string StringTrim( std::string str )
{
    if ( str.empty() ) {
        return str;
    }

    std::string::iterator iter = str.begin();
    while ( iter != str.end() && std::isspace( static_cast<unsigned char>( *iter ) ) ) {
        ++iter;
    }

    if ( iter == str.end() ) {
        return {};
    }

    if ( iter != str.begin() )
        str.erase( str.begin(), iter );

    iter = str.end() - 1;
    while ( iter != str.begin() && std::isspace( static_cast<unsigned char>( *iter ) ) ) {
        --iter;
    }

    if ( iter != str.end() - 1 ) {
        str.erase( iter + 1, str.end() );
    }

    return str;
}

std::string StringLower( std::string str )
{
    std::transform( str.begin(), str.end(), str.begin(), []( const unsigned char c ) { return static_cast<char>( std::tolower( c ) ); } );
    return str;
}

std::string StringUpper( std::string str )
{
    std::transform( str.begin(), str.end(), str.begin(), []( const unsigned char c ) { return static_cast<char>( std::toupper( c ) ); } );
    return str;
}

void StringReplace( std::string & dst, const char * pred, const std::string_view src )
{
    size_t pos;

    while ( std::string::npos != ( pos = dst.find( pred ) ) ) {
        dst.replace( pos, std::strlen( pred ), src );
    }
}

std::vector<std::string> StringSplit( const std::string_view str, const char sep )
{
    std::vector<std::string> result;

    size_t startPos = 0;

    for ( size_t sepPos = str.find( sep ); sepPos != std::string::npos; sepPos = str.find( sep, startPos ) ) {
        assert( startPos < str.size() && sepPos < str.size() );

        result.emplace_back( str.begin() + startPos, str.begin() + sepPos );

        startPos = sepPos + 1;
    }

    assert( startPos <= str.size() );

    result.emplace_back( str.begin() + startPos, str.end() );

    return result;
}

int Sign( const int i )
{
    if ( i < 0 ) {
        return -1;
    }
    if ( i > 0 ) {
        return 1;
    }
    return 0;
}

namespace fheroes2
{
    uint32_t calculateCRC32( const uint8_t * data, const size_t length )
    {
        if ( length > std::numeric_limits<uInt>::max() ) {
            throw std::
                system_error( std::make_error_code( std::errc::value_too_large ),
                              "Too large `length` provided to `calculateCRC32`. Must be no larger than `std::numeric_limits<uInt>::max()` (usually `(1 << 32) - 1`)." );
        }

        return static_cast<uint32_t>( crc32( 0, data, static_cast<uInt>( length ) ) );
    }

    void replaceStringEnding( std::string & output, const char * originalEnding, const char * correctedEnding )
    {
        assert( originalEnding != nullptr && correctedEnding != nullptr );

        const size_t originalEndingSize = strlen( originalEnding );
        const size_t correctedEndingSize = strlen( correctedEnding );
        if ( output.size() < originalEndingSize ) {
            return;
        }

        if ( memcmp( output.data() + output.size() - originalEndingSize, originalEnding, originalEndingSize ) != 0 ) {
            return;
        }

        output.replace( output.size() - originalEndingSize, originalEndingSize, correctedEnding, correctedEndingSize );
    }

    std::string abbreviateNumber( const int num )
    {
        if ( std::abs( num ) >= 1000000 ) {
            return std::to_string( num / 1000000 ) + 'M';
        }

        if ( std::abs( num ) >= 1000 ) {
            return std::to_string( num / 1000 ) + 'K';
        }

        return std::to_string( num );
    }

    void appendModifierToString( std::string & str, const int mod )
    {
        if ( mod < 0 ) {
            str.append( " " );
        }
        else if ( mod > 0 ) {
            str.append( " +" );
        }

        str.append( std::to_string( mod ) );
    }
}

// --- ACCESSIBILITY CODE START ---
void SpeakAccessibility( const std::string & text )
{
#ifdef __ANDROID__
    if ( text.empty() ) {
        return;
    }

    // ИСПРАВЛЕНИЕ: Используем static_cast вместо (JNIEnv*), так как проект запрещает старые касты.
    JNIEnv * env = static_cast<JNIEnv *>( SDL_AndroidGetJNIEnv() );

    if ( !env ) {
        return;
    }

    jclass clazz = env->FindClass( "org/fheroes2/GameActivity" );

    if ( clazz ) {
        jmethodID method = env->GetStaticMethodID( clazz, "sendToScreenReader", "(Ljava/lang/String;)V" );

        if ( method ) {
            jstring jMsg = env->NewStringUTF( text.c_str() );
            env->CallStaticVoidMethod( clazz, method, jMsg );
            env->DeleteLocalRef( jMsg );
        }
        env->DeleteLocalRef( clazz );
    }
#else
    // Заглушка для ПК
    (void)text;
#endif
}
// --- ACCESSIBILITY CODE END ---
