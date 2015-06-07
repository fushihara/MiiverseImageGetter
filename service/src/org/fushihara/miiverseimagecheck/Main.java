package org.fushihara.miiverseimagecheck;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.ParseException;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicHeader;
import org.fushihara.util.FU;

public class Main {
    public static void main(String[] args) {
	Main main = new Main();
	try {
	    main.loadConfig();
	    if (args.length == 2) {
		main.downloadOneTitleAllHotImages(args[0], args[1]);
	    } else {
		while (true) {
		    main.getAllHotTilte();
		    main.getNewtitle();
		    main.waitForNextTime();
		}
	    }
	} catch (IOException | ParseException | URISyntaxException e) {
	    e.printStackTrace();
	}
    }

    private final BasicCookieStore mCookieStore = new BasicCookieStore();
    private final List<LoadTarget> mLoadTarget = new ArrayList<Main.LoadTarget>();
    private File mSaveAllBaseDirectory = null;
    private String mPassword = null;
    private String mId = null;
    private CloseableHttpClient mHttpClient;

    private static class LoadTarget {
	public final String mBaseUrl;
	public final String mTitle;

	public LoadTarget(String baseUrl, String title) {
	    mBaseUrl = baseUrl;
	    mTitle = title;
	}
    }

    public Main() {
	System.out.println(String.format("%1$tF %1$tT Main()", new Date()));
    }

    /** config.propertiesから設定を読み込む */
    public void loadConfig() throws IOException {
	ResourceBundle bundle = ResourceBundle.getBundle("config");
	// 保存ディレクトリを取得する
	{
	    String baseDir = bundle.getString("miiverse.saveDirectory");
	    mSaveAllBaseDirectory = new File(baseDir);
	    System.out.println(String.format("bundle.miiverse.saveDirectory=%s",
		    mSaveAllBaseDirectory.getCanonicalPath()));
	}
	if (bundle.containsKey("miiverse.account.id")) {
	    mId = bundle.getString("miiverse.account.id");
	    System.out.println(String.format("bundle.miiverse.account.id=%s", mId));
	}
	if (bundle.containsKey("miiverse.account.password")) {
	    mPassword = bundle.getString("miiverse.account.password");
	    System.out.println(String.format("bundle.miiverse.account.password=%s", mPassword));
	}
	// 取得対象を読み込む
	mLoadTarget.clear();
	{
	    Enumeration<String> em = bundle.getKeys();
	    while (em.hasMoreElements()) {
		String key = em.nextElement();
		if (!key.startsWith("miiverse.target.")) {
		    continue;
		}
		String rawValue = bundle.getString(key);
		String[] sp = rawValue.split(",", 2);
		mLoadTarget.add(new LoadTarget(sp[0], sp[1]));
		System.out.println(String.format("bundle.miiverse.target=%s / %s", sp[0], sp[1]));
	    }
	}
	// httpクライアントの定義
	{
	    // 最初に使う変数を全部宣言＆デフォルト値で初期化
	    String userAgent = "";
	    List<Header> requestHeaders = new ArrayList<>();
	    // bundleからキーの有無を確認しつつ取得
	    if (bundle.containsKey("http.header.useragent")) {
		userAgent = bundle.getString("http.header.useragent");
		System.out.println(String.format("bundle.http.header.useragent=%s", userAgent));
	    }
	    Enumeration<String> em = bundle.getKeys();
	    while (em.hasMoreElements()) {
		String key = em.nextElement();
		if (!key.startsWith("http.header.any.")) {
		    continue;
		}
		String rawValue = bundle.getString(key);
		String[] sp = rawValue.split(":", 2);
		requestHeaders.add(new BasicHeader(sp[0], sp[1]));
		System.out.println(String.format("bundle.http.header.any=%s:%s", sp[0], sp[1]));
	    }

	    HttpClientBuilder http = HttpClients.custom();
	    http.setUserAgent(userAgent);
	    http.setDefaultHeaders(requestHeaders);
	    http.setSSLHostnameVerifier(new NoopHostnameVerifier());
	    http.setDefaultCookieStore(mCookieStore);
	    http.setRedirectStrategy(new LaxRedirectStrategy());
	    mHttpClient = http.build();
	}
    }

    /** 次の時刻の0分x秒まで待機する */
    public void waitForNextTime() {
	ZonedDateTime now = ZonedDateTime.now();
	ZonedDateTime stop = now.plusHours(1).withMinute(0).withSecond((int) (Math.random() * 60));
	Duration dur = Duration.between(stop, now).abs();
	System.out.println("sleep for " + stop.toString());
	FU.SleepNC(dur.getSeconds() * 1000);
    }

    /**
     * configで読み込んだゲームタイトルの新着画像をDLする。 ログインを行うのでパスワード等の設定必須。
     * タイトルディレクトリの/new/の下に日付ごとにディレクトリを作る。
     * 日付ごとに分けて、1日前まではチェックし、同じpostIDの画像を二度DLしないようにする。
     * ログイン時に例外が発生したら呼び出し元メソッドまで例外を飛ばすが 取得中に例外が発生したらそのタイトルを飛ばして次に向かう
     */
    public void getNewtitle() throws ParseException, URISyntaxException, IOException {
	HttpRequestManager.doLogin(mHttpClient, mId, mPassword);
	for (LoadTarget loadTarget : mLoadTarget) {
	    try {
		// 既存ファイルの調査＆保存ディレクトリの確定
		File saveDirectoryToday;
		File saveDirectoryYesterday;
		{
		    ZonedDateTime day = ZonedDateTime.now();
		    saveDirectoryToday = new File(new File(new File(mSaveAllBaseDirectory, loadTarget.mTitle), "new"),
			    String.format("%1$tF", day));
		    saveDirectoryYesterday = new File(new File(new File(mSaveAllBaseDirectory, loadTarget.mTitle),
			    "new"), String.format("%1$tF", day.minusDays(1)));
		}
		saveDirectoryToday.mkdirs();
		List<String> files;
		if (saveDirectoryYesterday.exists()) {
		    files = getAllPostIdsFromAnyDirectory(saveDirectoryToday, saveDirectoryYesterday);
		} else {
		    files = getAllPostIdsFromAnyDirectory(saveDirectoryToday);
		}
		System.out.println(String.format("getNewtitle %s(%s) start %d files exist", loadTarget.mTitle,
			loadTarget.mBaseUrl, files.size()));
		int result = HttpRequestManager.getLatestImages(mHttpClient, loadTarget.mBaseUrl, loadTarget.mTitle,
			saveDirectoryToday, files);
		System.out.println(String.format("getNewtitle end %d items download", result));
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    FU.SleepNC(5 * 1000);
	}
    }

    /**
     * configで読み込んだゲームタイトルの一日ごとのhot画像をDLする。 ログインは行わない。むしろクッキーをクリアする。
     * hotは1日一回、既にディレクトリが存在していたら処理を行わない
     */
    public void getAllHotTilte() {
	mCookieStore.clear();
	for (LoadTarget loadTarget : mLoadTarget) {
	    try {
		System.out
			.println(String.format("getAllHotTitle %s(%s) start", loadTarget.mTitle, loadTarget.mBaseUrl));
		int result = HttpRequestManager.getLatestHotImages(mHttpClient, loadTarget.mBaseUrl, loadTarget.mTitle,
			mSaveAllBaseDirectory);
		System.out.println(String.format("getAllHotTitle end %d items download", result));
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    FU.SleepNC(5 * 1000);
	}
    }

    /**
     * 指定したゲームIDのhotな画像を全てDLする
     */
    public void downloadOneTitleAllHotImages(String gameId, String gameTitle) throws IOException {
	System.out.println(String.format("%s(%s)の全てのhotなデータをダウンロード", gameTitle, gameId));
	mCookieStore.clear();
	ZonedDateTime day = ZonedDateTime.now();
	ZonedDateTime today = day.minusDays(0);
	ZonedDateTime yesterday = day.minusDays(1);
	while (true) {
	    int result = HttpRequestManager.getHotImages(mHttpClient, gameId, gameTitle, day, mSaveAllBaseDirectory);
	    System.out.println(String.format("downloadOneTitleAllHotImages %s(%s) day=%tF finish. %d items.",
		    gameTitle, gameId, day, result));
	    if (result == 0 && !day.equals(today) && !day.equals(yesterday)) {
		// 結果が0で昨日以前の場合はbreak。つまり今日はまだ作られていないだけかもしれないので目を瞑る
		break;
	    }
	    day = day.minusDays(1);
	    FU.SleepNC(5 * 1000);
	}
    }

    /**
     * 対象のディレクトリにあるpngファイルのファイル名のpostId の一覧を返す
     */
    private List<String> getAllPostIdsFromAnyDirectory(File... targetDirectorys) {
	List<String> result = new ArrayList<>();
	for (File targetDirectory : targetDirectorys) {
	    File[] files = targetDirectory.listFiles();
	    Pattern pat = Pattern.compile("(.{22})\\.png", Pattern.CASE_INSENSITIVE);
	    for (File file : files) {
		if (!file.exists()) {
		    continue;
		}
		if (!file.isFile()) {
		    continue;
		}
		if (!file.getName().endsWith(".png")) {
		    continue;
		}
		Matcher match = pat.matcher(file.getName());
		if (!match.find()) {
		    continue;
		}
		result.add(match.group(1));
	    }
	}
	return result;
    }
}
