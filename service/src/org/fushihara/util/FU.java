package org.fushihara.util;

public class FU {
    /** Thread.sleep(long)の非チェック版。例外時はprintStackTrace()してそのまま抜ける */
    public static void SleepNC(long millis) {
	try {
	    Thread.sleep(millis);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }
}
