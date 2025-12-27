/***************************************************************************
 * fheroes2: https://github.com/ihhub/fheroes2                           *
 * Copyright (C) 2021 - 2025                                             *
 * *
 * This program is free software; you can redistribute it and/or modify  *
 * it under the terms of the GNU General Public License as published by  *
 * the Free Software Foundation; either version 2 of the License, or     *
 * (at your option) any later version.                                   *
 * *
 * This program is distributed in the hope that it will be useful,       *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 * GNU General Public License for more details.                          *
 * *
 * You should have received a copy of the GNU General Public License     *
 * along with this program; if not, write to the                         *
 * Free Software Foundation, Inc.,                                       *
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 ***************************************************************************/

#include "ui_dialog.h"

#include <algorithm>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <ostream>
#include <string>
#include <utility>

// --- ACCESSIBILITY INCLUDE START ---
#include "../../engine/tools.h"
// --- ACCESSIBILITY INCLUDE END ---

#include "agg_image.h"
#include "army_troop.h"
#include "cursor.h"
#include "dialog.h"
#include "experience.h"
#include "game_delays.h"
#include "game_hotkeys.h"
#include "heroes_indicator.h"
#include "icn.h"
#include "localevent.h"
#include "logging.h"
#include "luck.h"
#include "morale.h"
#include "resource.h"
#include "screen.h"
#include "spell_info.h"
#include "tools.h"
#include "translations.h"
#include "ui_button.h"
#include "ui_constants.h"
#include "ui_keyboard.h"
#include "ui_monster.h"
#include "ui_text.h"

class HeroBase;

namespace
{
    const int32_t textOffsetY = 10;
    const int32_t elementOffsetX = 10;
    const int32_t textOffsetFromElement = 2;
    const int32_t defaultElementPopupButtons = Dialog::ZERO;

    void outputInTextSupportMode( const fheroes2::TextBase & header, const fheroes2::TextBase & body, const int buttonTypes )
    {
        START_TEXT_SUPPORT_MODE

        COUT( header.text() )
        COUT( '\n' )
        COUT( body.text() )

        if ( buttonTypes & Dialog::YES ) {
            COUT( "Press " << Game::getHotKeyNameByEventId( Game::HotKeyEvent::DEFAULT_OKAY ) << " to choose YES." )
        }
        if ( buttonTypes & Dialog::NO ) {
            COUT( "Press " << Game::getHotKeyNameByEventId( Game::HotKeyEvent::DEFAULT_CANCEL ) << " to choose NO." )
        }
        if ( buttonTypes & Dialog::OK ) {
            COUT( "Press " << Game::getHotKeyNameByEventId( Game::HotKeyEvent::DEFAULT_OKAY ) << " to choose OK." )
        }
        if ( buttonTypes & Dialog::CANCEL ) {
            COUT( "Press " << Game::getHotKeyNameByEventId( Game::HotKeyEvent::DEFAULT_CANCEL ) << " to choose CANCEL." )
        }
    }
}

namespace fheroes2
{
    int showMessage( const TextBase & header, const TextBase & body, const int buttons, const std::vector<const DialogElement *> & elements /* = {} */ )
    {
        outputInTextSupportMode( header, body, buttons );

        // --- ACCESSIBILITY CODE START ---
        // Озвучиваем заголовок и текст сообщения
        if ( !header.empty() ) {
            SpeakAccessibility( header.text() );
        }
        if ( !body.empty() ) {
            SpeakAccessibility( body.text() );
        }
        // --- ACCESSIBILITY CODE END ---

        const bool isProperDialog = ( buttons != 0 );
        const int cusorTheme = isProperDialog ? ::Cursor::POINTER : ::Cursor::Get().Themes();

        // setup cursor
        const CursorRestorer cursorRestorer( isProperDialog, cusorTheme );

        const int32_t headerHeight = header.empty() ? 0 : header.height( fheroes2::boxAreaWidthPx ) + textOffsetY;

        int overallTextHeight = headerHeight;

        const int32_t bodyTextHeight = body.height( fheroes2::boxAreaWidthPx );
        if ( bodyTextHeight > 0 ) {
            overallTextHeight += bodyTextHeight + textOffsetY;
        }

        std::vector<int32_t> rowElementIndex;
        std::vector<int32_t> rowHeight;
        std::vector<size_t> rowId;
        std::vector<int32_t> rowMaxElementWidth;
        std::vector<int32_t> rowElementCount;

        int32_t elementHeight = 0;
        size_t elementId = 0;
        for ( const DialogElement * element : elements ) {
            assert( element != nullptr );

            const int32_t currentElementWidth = element->area().width;
            if ( rowHeight.empty() ) {
                rowElementIndex.emplace_back( 0 );
                rowHeight.emplace_back( element->area().height );
                rowId.emplace_back( elementId );
                rowMaxElementWidth.emplace_back( currentElementWidth );
                rowElementCount.emplace_back( 1 );

                ++elementId;
            }
            else if ( ( std::max( rowMaxElementWidth.back(), currentElementWidth ) + elementOffsetX ) * ( rowElementCount.back() + 1 ) <= fheroes2::boxAreaWidthPx ) {
                rowElementIndex.emplace_back( rowElementIndex.back() + 1 );
                rowHeight.back() = std::max( rowHeight.back(), element->area().height );

                // We cannot use back() to insert it into the same container as it will be resized upon insertion.
                const size_t lastRoiId = rowId.back();
                rowId.emplace_back( lastRoiId );

                rowMaxElementWidth.back() = std::max( rowMaxElementWidth.back(), currentElementWidth );
                ++rowElementCount.back();
            }
            else {
                elementHeight += textOffsetY;
                elementHeight += rowHeight.back();

                rowElementIndex.emplace_back( 0 );
                rowHeight.emplace_back( element->area().height );
                rowId.emplace_back( elementId );
                rowMaxElementWidth.emplace_back( currentElementWidth );
                rowElementCount.emplace_back( 1 );

                ++elementId;
            }
        }

        if ( !rowHeight.empty() ) {
            // UI elements are offset from the dialog body.
            if ( bodyTextHeight > 0 ) {
                elementHeight += textOffsetY;
            }
            elementHeight += textOffsetY;
            elementHeight += rowHeight.back();
        }

        const Dialog::FrameBox box( overallTextHeight + elementHeight, isProperDialog );
        const Rect & pos = box.GetArea();

        Display & display = Display::instance();
        header.draw( pos.x, pos.y + textOffsetY, fheroes2::boxAreaWidthPx, display );
        body.draw( pos.x, pos.y + textOffsetY + headerHeight, fheroes2::boxAreaWidthPx, display );

        elementHeight = overallTextHeight + textOffsetY;
        if ( bodyTextHeight > 0 ) {
            elementHeight += textOffsetY;
        }

        elementId = 0;
        size_t prevRowId = 0;
        int32_t currentRowHeight = 0;

        std::vector<Point> elementOffsets;

        for ( const DialogElement * element : elements ) {
            const size_t currentRowId = rowId[elementId];
            if ( prevRowId != currentRowId ) {
                prevRowId = currentRowId;

                elementHeight += textOffsetY;
                elementHeight += currentRowHeight;
            }

            currentRowHeight = rowHeight[currentRowId];
            const int32_t currentRowElementIndex = rowElementIndex[elementId];
            const int32_t currentRowElementCount = rowElementCount[currentRowId];
            const int32_t currentRowMaxElementWidth = rowMaxElementWidth[currentRowId];

            const int32_t emptyWidth = fheroes2::boxAreaWidthPx - currentRowElementCount * currentRowMaxElementWidth;
            const int32_t offsetBetweenElements = emptyWidth / ( currentRowElementCount + 1 );

            const int32_t widthOffset = offsetBetweenElements + currentRowElementIndex * ( currentRowMaxElementWidth + offsetBetweenElements );
            elementOffsets.emplace_back( pos.x + widthOffset + ( currentRowMaxElementWidth - element->area().width ) / 2,
                                         pos.y + elementHeight + currentRowHeight - element->area().height );

            element->draw( display, elementOffsets.back() );

            ++elementId;
        }

        ButtonGroup group( pos, buttons );
        group.draw();

        display.render();

        int result = Dialog::ZERO;
        LocalEvent & le = LocalEvent::Get();

        bool delayInEventHandling = true;

        while ( result == Dialog::ZERO && le.HandleEvents( delayInEventHandling ) ) {
            if ( isProperDialog ) {
                elementId = 0;
                for ( const DialogElement * element : elements ) {
                    element->processEvents( elementOffsets[elementId] );
                    ++elementId;
                }
            }
            else if ( !le.isMouseRightButtonPressed() ) {
                break;
            }

            result = group.processEvents();

            delayInEventHandling = true;
            elementId = 0;
            for ( const DialogElement * element : elements ) {
                if ( element->update( display, elementOffsets[elementId] ) ) {
                    delayInEventHandling = false;
                }
                ++elementId;
            }

            if ( !delayInEventHandling ) {
                display.render( pos );
            }
        }

        return result;
    }

    // --- Функции-обертки, которые были в конце файла и нужны для сборки ---

    int32_t getDialogHeight( const TextBase & header, const TextBase & body, const int buttons, const std::vector<const DialogElement *> & elements )
    {
        const int32_t headerHeight = header.empty() ? 0 : header.height( fheroes2::boxAreaWidthPx ) + textOffsetY;
        int overallTextHeight = headerHeight;

        const int32_t bodyTextHeight = body.height( fheroes2::boxAreaWidthPx );
        if ( bodyTextHeight > 0 ) {
            overallTextHeight += bodyTextHeight + textOffsetY;
        }

        return overallTextHeight;
    }

    void MessageBox( const std::string & msg, const int buttons )
    {
        const Text text( msg, FontType::normalWhite() );
        showMessage( Text(), text, buttons );
    }

    void MessageBox( const std::string & msg, const std::function<void( void )> & callback )
    {
        MessageBox( msg );
        if ( callback ) {
            callback();
        }
    }

    bool Confirm( const std::string & msg )
    {
        return MessageBox( msg, Dialog::YES | Dialog::NO ) == Dialog::YES;
    }

    int Select( const std::string & msg, const std::string & first, const std::string & second )
    {
        // Simple implementation for text support
        return Confirm( msg + " (" + first + " / " + second + ")" ) ? 1 : 2;
    }
}
