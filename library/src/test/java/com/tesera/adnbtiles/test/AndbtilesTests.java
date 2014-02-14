package com.tesera.andbtiles.test;

import com.tesera.andbtiles.Andbtiles;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class AndbtilesTests {

    @Test
    public void testInstantiation() {
        Andbtiles andbtiles = new Andbtiles(Robolectric.getShadowApplication().getApplicationContext());
        assertNotNull(andbtiles);
    }
}