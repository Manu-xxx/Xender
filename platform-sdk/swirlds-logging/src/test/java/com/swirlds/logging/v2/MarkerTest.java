package com.swirlds.logging.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class MarkerTest {

    @Test
    void testNullName() {
        assertThrows(NullPointerException.class, () -> new Marker(null));
    }

    @Test
    void testSimpelMarker() {
        //given
        final Marker marker = new Marker("markerName");

        //then
        assertEquals("markerName", marker.name());
        assertNull(marker.parent());
    }

    @Test
    void testWithParenMarker() {
        //given
        final Marker parent = new Marker("parentName");
        final Marker marker = new Marker("markerName", parent);

        //then
        assertEquals("markerName", marker.name());
        assertNotNull(marker.parent());
        assertSame(parent, marker.parent());
        assertNull(parent.parent());
    }

    @Test
    void testEquals() {
        //given
        final Marker marker1 = new Marker("marker1");
        final Marker marker2 = new Marker("marker2");
        final Marker marker3 = new Marker("marker1");
        final Marker marker4 = new Marker("marker1", marker2);

        //then
        assertEquals(marker1, marker1);
        assertEquals(marker1, marker3);
        assertNotEquals(marker1, marker2);
        assertNotEquals(marker1, null);
        assertNotEquals(marker1, marker4);
    }

    @Test
    void testHashCode() {
        //given
        final Marker marker1 = new Marker("marker1");
        final Marker marker2 = new Marker("marker2");
        final Marker marker3 = new Marker("marker1");
        final Marker marker4 = new Marker("marker1", marker2);

        //then
        assertEquals(marker1.hashCode(), marker1.hashCode());
        assertEquals(marker1.hashCode(), marker3.hashCode());
        assertNotEquals(marker1.hashCode(), marker2.hashCode());
        assertNotEquals(marker1.hashCode(), marker4.hashCode());
    }
}
