package org.fushihara.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class FU {
	/** Thread.sleep(long)の非チェック版。例外時はprintStackTrace()してそのまま抜ける */
	public static void SleepNC(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/** プロパティファイルをutf-8で読み込む http://blog.k11i.biz/2014/09/java-utf-8.html より hoge.properties という風に指定 */
	public static Properties loadUtf8Properties(String resourceName) throws IOException {
		try (InputStream is = FU.class.getResourceAsStream(resourceName);
				InputStreamReader isr = new InputStreamReader(is, "UTF-8");
				BufferedReader reader = new BufferedReader(isr)) {
			Properties result = new Properties();
			result.load(reader);
			return result;
		}
	}
}
