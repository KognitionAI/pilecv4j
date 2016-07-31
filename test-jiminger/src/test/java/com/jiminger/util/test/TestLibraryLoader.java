package com.jiminger.util.test;

import org.junit.Test;

import com.jiminger.util.LibraryLoader;

public class TestLibraryLoader
{
	

	@Test
	public void testLibraryLoading() throws Exception {
		LibraryLoader.init();
	}
}
