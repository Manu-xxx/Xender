/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.formatting;

import static com.swirlds.common.formatting.TextEffect.CROSSED_OUT;
import static com.swirlds.common.formatting.TextEffect.RED;
import static com.swirlds.common.formatting.TextEffect.UNDERLINE;
import static com.swirlds.common.formatting.TextEffect.applyEffects;
import static com.swirlds.common.formatting.TextEffect.getPrintableTextLength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.formatting.TextEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextEffect Tests")
class TextEffectTests {

    @BeforeEach
    void beforeEach() {
        TextEffect.setTextEffectsEnabled(true);
    }

    @AfterEach
    void afterEach() {
        TextEffect.setTextEffectsEnabled(false);
    }

    @Test
    @DisplayName("Color Disabled Test")
    void colorDisabledTest() {
        TextEffect.setTextEffectsEnabled(false);

        assertEquals("", TextEffect.RED.apply(""));
        assertEquals("Hello World", TextEffect.GREEN.apply("Hello World"));
        assertEquals("There should be no effects", TextEffect.UNDERLINE.apply("There should be no effects"));
        assertEquals(
                "even when multiple are applied",
                TextEffect.applyEffects(
                        "even when multiple are applied", TextEffect.BOLD, TextEffect.RED, TextEffect.UNDERLINE));
        assertEquals("or when none are applied", TextEffect.applyEffects("or when none are applied"));
    }

    @Test
    @DisplayName("Single Effect Test")
    void singleEffectTest() {
        // This is one of those tests that needs human eyeballs to decide if the colors
        // actually match the descriptions. When running with the human-eyeball-plugin
        // enabled, set this to true.
        final boolean print = false;

        for (final TextEffect effect : TextEffect.values()) {
            if (effect == TextEffect.RESET) {
                continue;
            }

            final String text = effect.name();
            final String formattedText = effect.apply(text);

            if (print) {
                System.out.println(formattedText);
            }

            assertTrue(formattedText.contains(text));
            assertTrue(formattedText.startsWith("\u001B[" + effect.getCode() + "m"));
            assertTrue(formattedText.endsWith("\u001B[0m"));
            assertEquals(text.length(), getPrintableTextLength(formattedText));
        }
    }

    @Test
    @DisplayName("Multi Effect Test")
    void multiEffectTest() {
        // This is one of those tests that needs human eyeballs to decide if the colors
        // actually match the descriptions. When running with the human-eyeball-plugin
        // enabled, set this to true.
        final boolean print = true;

        final String text = "hello world!";
        final String formattedText = applyEffects(text, RED, CROSSED_OUT, UNDERLINE);

        if (print) {
            System.out.println(formattedText);
        }

        assertTrue(formattedText.contains(text));
        assertTrue(formattedText.startsWith(
                "\u001B[" + RED.getCode() + ";" + CROSSED_OUT.getCode() + ";" + UNDERLINE.getCode() + "m"));
        assertTrue(formattedText.endsWith("\u001B[0m"));
        assertEquals(text.length(), getPrintableTextLength(formattedText));
    }
}
