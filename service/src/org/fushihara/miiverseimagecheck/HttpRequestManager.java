package org.fushihara.miiverseimagecheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.fushihara.util.FU;
import org.fushihara.util.Ref;

public class HttpRequestManager {
    /**
     * 特定のゲームの特定の日付の全てのホットな画像をDLする。ログインして通信するかはhttpClientのクッキーに依存する
     *
     * @param httpClient
     *            DLする為に使うhttpクライアント
     * @param gameId
     *            DLするゲームのURLに使われるID
     * @param gameTitle
     *            保存する画像を保存するディレクトリを作る時に使うゲーム名
     * @param targetDate
     *            DLする日付を指定する
     * @param saveBaseDirectory
     *            保存する基準のディレクトリを指定する。ここを基準に、/ゲーム名/hot/日付/以下に画像を保存する
     * @return 保存した画像の数。指定した日付のhotがまだ無い時は0を返す
     * @throws IOException
     */
    public static int getHotImages(CloseableHttpClient httpClient, String gameId, String gameTitle,
	    ZonedDateTime targetDate, File saveBaseDirectory) throws IOException {
	int offset = 0;
	List<PostData> result = new ArrayList<>();
	while (true) {
	    String url = getHotUrl(gameId, targetDate, offset);
	    HttpUriRequest get = RequestBuilder.get().setUri(url).build();
	    try (CloseableHttpResponse res = httpClient.execute(get)) {
		String source = EntityUtils.toString(res.getEntity());
		List<PostData> resultOne = getPostDataListFromRawHtml(source);
		if (resultOne.size() == 0) {
		    break;
		}
		resultOne.stream().filter(s -> !result.contains(s)).forEach(s -> result.add(s));
		offset += resultOne.size();
		FU.SleepNC(1 * 1000);
	    }
	}
	if (result.size() == 0) {
	    return 0;
	}
	// 保存するディレクトリを決定
	File saveDirectory = new File(new File(new File(saveBaseDirectory, gameTitle), "hot"), String.format("%1$tF",
		targetDate));
	int resultCount = downloadImages(httpClient, result, saveDirectory, DownloadImageSaveFileType.serialNumberOnly);
	return resultCount;
    }

    /**
     * 特定のゲームの最新のホットな画像をDLする。既にディレクトリが存在していたら何もせずに終了
     *
     * @param httpClient
     *            DLに使うhttpクライアント
     * @param gameId
     *            DLする対象のゲームID
     * @param gameTitle
     *            DLする対象のゲーム名
     * @param saveAllBaseDirectory
     *            保存する基準のディレクトリを指定する。ここを基準に、/ゲーム名/hot/日付/以下に画像を保存する
     * @return DLに成功した画像の個数。0の時は既にディレクトリが存在していたので終了した時
     * @throws IOException
     */
    public static int getLatestHotImages(CloseableHttpClient httpClient, String gameId, String gameTitle,
	    File saveAllBaseDirectory) throws IOException {
	int offset = 0;
	List<PostData> result = new ArrayList<>();
	File saveDirectory = null;
	while (true) {
	    String url = getHotUrl(gameId, null, offset);
	    HttpUriRequest get = RequestBuilder.get().setUri(url).build();
	    try (CloseableHttpResponse res = httpClient.execute(get)) {
		String source = EntityUtils.toString(res.getEntity());
		List<PostData> resultOne = getPostDataListFromRawHtml(source);
		if (offset == 0) {
		    ZonedDateTime latestDate = getLastHotDateFromHtml(source);
		    saveDirectory = new File(new File(new File(saveAllBaseDirectory, gameTitle), "hot"), String.format(
			    "%1$tF", latestDate));
		    if (saveDirectory.exists()) {
			return 0;
		    }
		}
		if (resultOne.size() == 0) {
		    break;
		}
		resultOne.stream().filter(s -> !result.contains(s)).forEach(s -> result.add(s));
		offset += resultOne.size();
		FU.SleepNC(1 * 1000);
	    }
	}
	if (result.size() == 0) {
	    return 0;
	}
	int res = downloadImages(httpClient, result, saveDirectory, DownloadImageSaveFileType.serialNumberOnly);
	return res;
    }

    /**
     * 指定したゲームの最新投稿を取得する。ログイン必須
     *
     * @param httpClient
     * @param gameId
     * @param gameTitle
     * @param saveDirectory
     *            画像を保存するディレクトリ。メソッド内で何か構築する事は無く、渡されたディレクトリにそのまま画像を保存する
     * @param existFiles
     *            既に存在しているpostIDの一覧
     */
    public static int getLatestImages(CloseableHttpClient httpClient, String gameId, String gameTitle,
	    File saveDirectory, List<String> existFiles) throws ClientProtocolException, IOException {
	List<PostData> result = new ArrayList<>();
	Pattern nextPagePattern = Pattern.compile("data-next-page-url=\"(.+?)\"");
	String url = String.format("https://miiverse.nintendo.net/titles/%s/new", gameId);
	while (true) {
	    HttpUriRequest get = RequestBuilder.get().setUri(url).build();
	    try (CloseableHttpResponse res = httpClient.execute(get)) {
		String source = EntityUtils.toString(res.getEntity());
		List<PostData> resultOne = getPostDataListFromRawHtml(source);
		if (resultOne.size() == 0) {
		    break;
		}
		final Ref<Boolean> loopEnd = new Ref<Boolean>(false);
		resultOne.stream().filter(s -> !result.contains(s)).filter(s -> {
		    if (existFiles.contains(s.getPostId())) {
			loopEnd.set(true);
			return false;
		    } else {
			return true;
		    }
		}).forEach(s -> result.add(s));
		if (loopEnd.get() == true) {
		    break;
		}
		// 次のurlはhtmlの中にある
		Matcher match = nextPagePattern.matcher(source);
		if (!match.find()) {
		    break;
		}
		url = "https://miiverse.nintendo.net" + match.group(1);
		FU.SleepNC(1 * 1000);
	    }
	}
	if (result.size() == 0) {
	    return 0;
	}
	Collections.reverse(result);
	int res = downloadImages(httpClient, result, saveDirectory, DownloadImageSaveFileType.staticDateAndSirialNumber);
	return res;
    }

    /***
     * 指定したhttpClientのクッキーマネージャーを使いログインする。既にログインしている場合対応。
     *
     * @param httpClient
     *            ログインに使うhttpClient
     * @param id
     *            ユーザーID
     * @param password
     *            パスワード
     */
    public static void doLogin(CloseableHttpClient httpClient, String id, String password) throws URISyntaxException,
	    ParseException, IOException {
	String source = "";
	{
	    HttpUriRequest login = RequestBuilder.post().setUri(new URI("https://miiverse.nintendo.net/auth/forward"))
		    .addParameter("location", "https://miiverse.nintendo.net/")
		    .addParameter("x", (int) (Math.random() * 100) + "")
		    .addParameter("y", (int) (Math.random() * 100) + "").build();
	    try (CloseableHttpResponse responce = httpClient.execute(login)) {
		source = EntityUtils.toString(responce.getEntity());
		if (source.contains("data-user-id=\"" + id + "\"")) {
		    // 既にログインしている可能性
		    return;
		}

	    }
	}
	{
	    String clientId = "";
	    String state = "";
	    Pattern p1 = Pattern.compile("name=\"client_id\" value=\"(.+?)\"");
	    Pattern p2 = Pattern.compile("name=\"state\" value=\"(.+?)\"");
	    Matcher m1 = p1.matcher(source);
	    Matcher m2 = p2.matcher(source);
	    if (!m1.find()) {
		throw new IOException("HTMLマッチ失敗 " + source.replaceAll("\r|\n|\t", ""));
	    }
	    clientId = m1.group(1);
	    if (!m2.find()) {
		throw new IOException("HTMLマッチ失敗 " + source.replaceAll("\r|\n|\t", ""));
	    }
	    state = m2.group(1);
	    HttpUriRequest login = RequestBuilder.post().setUri(new URI("https://id.nintendo.net/oauth/authorize"))
		    .addParameter("response_type", "code").addParameter("client_id", clientId)
		    .addParameter("state", state)
		    .addParameter("redirect_uri", "https://miiverse.nintendo.net/auth/callback")
		    .addParameter("nintendo_authenticate", "").addParameter("nintendo_authorize", "")
		    .addParameter("scope", "").addParameter("lang", "ja-JP").addParameter("username", id)
		    .addParameter("password", password).addParameter("rememberMe", "on").build();
	    try (CloseableHttpResponse responce = httpClient.execute(login)) {
		source = EntityUtils.toString(responce.getEntity());
		// data-user-id="fnnidtst01"
		if (!source.contains("data-user-id=\"" + id + "\"")) {
		    throw new IOException("ログイン失敗 " + source.replaceAll("\r|\n|\t", ""));
		}
	    }
	}

    }

    /**
     * 投稿データからイラストを指定のディレクトリにDLする
     *
     * @param httpClient
     *            通信に使用するhttpClient
     * @param postDatas
     *            投稿データの配列。この順番でファイル名に1 2 3と付く
     * @param saveDirectory
     *            保存対象のディレクトリ。作成もこのメソッドで行うので気にしなくて良い。作成に失敗したら例外
     * @throws IOException
     *             保存対象のディレクトリの作成に失敗、http通信に失敗。どちらにしろどうしようもない
     */
    private static int downloadImages(CloseableHttpClient httpClient, List<PostData> postDatas, File saveDirectory,
	    DownloadImageSaveFileType downloadImageSaveFileType) throws IOException {
	if (postDatas.size() == 0) {
	    return 0;
	}
	saveDirectory.mkdirs();
	if (!saveDirectory.exists()) {
	    throw new IOException("ディレクト作成に失敗:" + saveDirectory.getAbsolutePath());
	}
	if (saveDirectory.isFile()) {
	    throw new IOException("ディレクト作成に失敗(ファイルが存在していた):" + saveDirectory.getAbsolutePath());
	}
	int index = 0;
	int downloadCount = 0;
	ZonedDateTime now = ZonedDateTime.now();
	for (PostData postData : postDatas) {
	    if (!postData.hasIllust()) {
		continue;
	    }
	    index++;
	    HttpUriRequest get = RequestBuilder.get().setUri(postData.getIllustUrl()).build();
	    try (CloseableHttpResponse res = httpClient.execute(get)) {
		try (BufferedInputStream bis = new BufferedInputStream(res.getEntity().getContent())) {
		    File saveFile;
		    switch (downloadImageSaveFileType) {
		    case serialNumberOnly:
			saveFile = new File(saveDirectory, String.format("%03d-%s.png", index, postData.getPostId()));
			break;
		    case staticDateAndSirialNumber:
		    default:
			saveFile = new File(saveDirectory, String.format(
				"%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS_%2$03d-%3$s.png", now, index, postData.getPostId()));
			break;
		    }
		    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(saveFile))) {
			int inByte;
			while ((inByte = bis.read()) != -1) {
			    bos.write(inByte);
			}
		    }
		    if (index % 20 == 0) {
			FU.SleepNC(1 * 1000);
		    }
		}
	    }
	    downloadCount++;
	}
	return downloadCount;
    }

    private enum DownloadImageSaveFileType {
	serialNumberOnly, staticDateAndSirialNumber
    }

    /**
     * リクエストに使うURLを取得する
     *
     * @param gameId
     * @param date
     * @param offset
     * @return
     */
    private static String getHotUrl(String gameId, ZonedDateTime date, int offset) {
	if (date != null && offset != 0) {
	    return String
		    .format("https://miiverse.nintendo.net/titles/%s/hot?date=%tF&offset=%d", gameId, date, offset);
	} else if (date != null && offset == 0) {
	    return String.format("https://miiverse.nintendo.net/titles/%s/hot?date=%tF", gameId, date);
	} else if (date == null && offset != 0) {
	    return String.format("https://miiverse.nintendo.net/titles/%s/hot?offset=%d", gameId, offset);
	} else {
	    return String.format("https://miiverse.nintendo.net/titles/%s/hot", gameId);
	}
    }

    /**
     * hotページのHTMLから日付を取得し返す
     *
     * @param html
     * @return
     */
    private static ZonedDateTime getLastHotDateFromHtml(String html) {
	Pattern pat = Pattern.compile("class=\"button selected\">(\\d+)/(\\d+)/(\\d+)</a>");
	Matcher match = pat.matcher(html);
	if (match.find()) {
	    int year = Integer.parseInt(match.group(1));
	    int month = Integer.parseInt(match.group(2));
	    int date = Integer.parseInt(match.group(3));
	    return ZonedDateTime.of(year, month, date, 0, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
	} else {
	    throw new IllegalArgumentException("htmlから日付をマッチ出来ませんでした\n" + html.replaceAll("\t|\n|\r", ""));
	}
    }

    public static List<PostData> getPostDataListFromRawHtml(String rawHtml) {
	Pattern mP1 = Pattern.compile("^(.+?)\"");
	Pattern mP2 = Pattern.compile("<p class=\"post-content-memo\"><img src=\"(.+?)\"");
	List<PostData> result = new ArrayList<PostData>();
	// 投稿ごとに分割
	String[] htmls1 = rawHtml.split("<div id=\"post-");
	for (String htmls2 : htmls1) {
	    // idを取得
	    String postId;
	    {
		Matcher m = mP1.matcher(htmls2);
		if (!m.find()) {
		    continue;
		}
		postId = m.group(1);
	    }
	    PostData addData;
	    // class="post-memo" の有無を確認
	    if (htmls2.contains("class=\"post-memo\"")) {
		// イラストurlを取得
		String illustUrl;
		{
		    Matcher m = mP2.matcher(htmls2);
		    if (!m.find()) {
			continue;
		    }
		    illustUrl = m.group(1);
		}
		addData = new PostData(postId, illustUrl);
	    } else {
		addData = new PostData(postId);
	    }
	    if (!result.contains(addData)) {
		result.add(addData);
	    }
	}
	return result;
    }

    public static class PostData {
	/** like:AYMHAAACAAADVHkH-62vaQ */
	private final String mPostId;
	/** like:https://d3esbfg30x759i.cloudfront.net/pap/zlCfzTZfim8yeQjXfo */
	private final String mIllustUrl;
	private final boolean hasIllust;

	public PostData(String postId, String illustUrl) {
	    mPostId = postId;
	    mIllustUrl = illustUrl;
	    hasIllust = true;
	}

	public PostData(String postId) {
	    mPostId = postId;
	    mIllustUrl = null;
	    hasIllust = false;
	}

	public boolean hasIllust() {
	    return hasIllust;
	}

	public String getIllustUrl() {
	    return mIllustUrl;
	}

	public String getPostId() {
	    return mPostId;
	}

	@Override
	public int hashCode() {
	    final int prime = 31;
	    int result = 1;
	    result = prime * result + ((mPostId == null) ? 0 : mPostId.hashCode());
	    return result;
	}

	@Override
	public boolean equals(Object obj) {
	    if (this == obj)
		return true;
	    if (obj == null)
		return false;
	    if (getClass() != obj.getClass())
		return false;
	    PostData other = (PostData) obj;
	    if (mPostId == null) {
		if (other.mPostId != null)
		    return false;
	    } else if (!mPostId.equals(other.mPostId))
		return false;
	    return true;
	}

	@Override
	public String toString() {
	    if (hasIllust) {
		return mPostId + " " + mIllustUrl;
	    } else {
		return mPostId;
	    }
	}
    }

}
