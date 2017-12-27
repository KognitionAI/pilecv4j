package com.jiminger.util;

import org.junit.Test;

import net.dempsy.util.library.NativeLibraryLoader;

public class TestLibraryLoader {
    @Test
    public void testLibraryLoading() throws Exception {
        NativeLibraryLoader.init();
    }
}
