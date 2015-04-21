package com.museosctolsl.main;

public class MuseOSCToLSLMain {
	private static MuseOscToLslTest m;
	
	public static void main(String[] args){
		//setup P300Test with: window width and height, letterArray length
		m = new MuseOscToLslTest();
		m.init();
		m.run();
	}
}
