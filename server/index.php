<?php
//getパラメーターチェック
$title="";
$type="";
$date="";
$page="";
$raw="";
$currentDirectory=getcwd();


/** GETパラメーターのtitleの値とrootDirからタイトル情報を取得する。titleの値から絶対パスを取得し、rootDirより上にいかないようにする */
function isSafeValue($getParam,$rootDir){
	$val=realpath($rootDir."/".$getParam);
	if(strpos($val,$rootDir."/")!==0){
		return false;
	}
	if(!is_dir($val)){
		return false;
	}
	return true;
}
function getInt($target){
	if(preg_match("/^\\d+$/",$target)){
		return $target;
	}else{
		return 0;
	}
}
$title=isSafeValue(isset($_GET["title"])?$_GET["title"]:"",$currentDirectory                     )?$_GET["title"]:"";
$type =isSafeValue(isset($_GET["type"] )?$_GET["type"] :"",$currentDirectory."/".$title          )?$_GET["type"] :"";
$date =isSafeValue(isset($_GET["date"] )?$_GET["date"] :"",$currentDirectory."/".$title."/".$type)?$_GET["date"] :"";
$page=getInt(isset($_GET["page"])?$_GET["page"]:"");
//各種モードに切り分けする
if($date!=""){
	// new 20150603_190050_290-AYIHAAAEAABEVRTor9m4WQ.png
	// hot 002-AYIHAAAEAACHVRTosKn8XQ.png
	//日付があるので画像一覧を表示する。ページは指定無しの時はゼロ
	$filesRaw=scandir("{$currentDirectory}/{$title}/{$type}/{$date}");
	$files=[];
	foreach($filesRaw as $val){
		$filePath="{$currentDirectory}/{$title}/{$type}/{$date}/{$val}";
		if(!is_file($filePath)){
			continue;
		}
		if(substr($val,-4)!==".png"){
			continue;
		}
		$files[]=$val;
	}
	$ppv=100;//1ページあたりいくつファイルを表示するか
	//出力するhtmlの一覧を定義
	$pagingInfo="";// 合計10000個/10ページ/1ページ目
	$pagePrev="";//前のページへ。divの場合もあるしaの場合もある
	$pageUp="";//上のページ(日付一覧)へ。常にa
	$pageNext="";//次のページへ。divの場合もあるしaの場合もある
	$fileHtmls=[];//joinして画像一覧として出力する
	//ファイル数、ページ数に関する値を先に計算して値のチェックを行う
	$fileCount=count($files);
	$pageTotal=ceil($fileCount/$ppv);
	$pageNow=($page<2?"1":($pageTotal<$page?$pageTotal:$page));
	
	
	//出力するhtmlの変数に値を入れていく
	//ページ情報
	$pagingInfo.="合計{$fileCount}個/";
	$pagingInfo.="{$pageTotal}ページ/";
	$pagingInfo.="{$pageNow}ページ目";
	//ページング
	if($pageNow==="1"){
		$pagePrev="<div>＜前へ</div>";
	}else{
		$pagePrev="<a href=\"./".($pageNow-1)."\">＜前へ</a>";
	}
	$pageUp="<a href=\"./../\">↑上へ↑</a>";//変数に入れる必要性は謎
	if($pageNow===$pageTotal.""){
		$pageNext="<div>次へ＞</div>";
	}else{
		$pageNext="<a href=\"./".($pageNow+1)."\">次へ＞</a>";
	}
	
	
	//ファイルの一覧
	$fileIndex=-1;
	$beforeHour=0;//1時間ごとにh3を出力する
	if($type==="new"){
		$files=array_reverse($files);
	}
	foreach($files as $val){
		$fileIndex++;
		if($fileIndex<(($pageNow-1)*$ppv)){
			continue;
		}
		if($pageNow*$ppv-1<$fileIndex){
			break;
		}
		// <div class="one"><img src="20150603_190050_306-AYIHAAAEAACHVRTo0wPeDw.png" width="320px" height="120px"><br><a href="https://miiverse.nintendo.net/posts/AYIHAAAEAACHVRTo0wPeDw">20150603_190050_306-AYIHAAAEAACHVRTo0wPeDw.png</a></div>
		$fileTime=0;//ファイルが取得された日時。type==newの時のみ存在する
		$filePostId="";//miiverseの投稿ID。常に存在する
		$linkBody="";//リンク部分の文字列 10:00:00 No.1220 or Rank.100
		if($type==="new" && preg_match("{^(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})}",$val,$match)){
			//newの時、ファイル名に取得日時の情報があるのでそれを取り出す
			$fileTimeHour=mktime($match[4],0,0,$match[2],$match[3],$match[1]);
			$fileTime=mktime($match[4],$match[5],$match[6],$match[2],$match[3],$match[1]);
			if($beforeHour!==$fileTimeHour){
				$fileHtmls[]="<h3>".date("Y年m月d日(D)H時",$fileTimeHour)."</h3>";
				$beforeHour=$fileTimeHour;
			}
		}
		if(preg_match("{(.{22})\\.png$}",$val,$match)){
			$filePostId=$match[1];
		}
		if($type==="new"){
			$linkBody.=date("H:i:s",$fileTime)." ";
			$linkBody.="No.".($fileCount-$fileIndex)." ";
			$linkBody.=$filePostId;
		}else{
			$linkBody.="Rank.".($fileIndex+1)." ";
			$linkBody.=$filePostId;
		}
		$addText="";
		$addText.="<div class=\"one\">";
		$addText.="<img src=\"{$val}\" width=\"320px\" height=\"120px\">";
		$addText.="<br>";
		$addText.="<a href=\"https://miiverse.nintendo.net/posts/{$filePostId}\">{$linkBody}</a>";
		$addText.="</div>";
		$addText.="\n";
		$fileHtmls[]=$addText;
	}
?>
<!DOCTYPE html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title><?=("{$title}/{$type}/{$date}/{$pageNow}ページ目");?></title>
<style>
*{margin:0;padding:0;}
h3{color:red;}
.menu {
  margin: auto;
  display: table;
}
.menu>a,.menu>div {
  box-sizing: border-box;
  border: solid 1px #000;
  margin: 0;
  padding: 0;
  width: 100px;
  display: table-cell;
  text-align: center;
  height: 3em;
  vertical-align: middle;
}
.one{
  border-right: solid 1px #000;
  border-bottom: solid 1px #000;
  padding: 1px;
  display: inline-block;
  text-align: center;font-size: small;
  font-family: monospace;}
.one a{color: #000;}
</style>
<?=$pagingInfo;?><br>
<div class="menu">
<?=$pagePrev;?>
<?=$pageUp;?>
<?=$pageNext;?>
</div>
<?php foreach($fileHtmls as $val){print($val);}?>
<div class="menu">
<?=$pagePrev;?>
<?=$pageUp;?>
<?=$pageNext;?>
</div>
<?php
	
}else if($type!=""){
	//タイプがあるので日付一覧を表示する
	$dirRaw=scandir("{$currentDirectory}/{$title}/{$type}/");
	$dirs=[];
	foreach($dirRaw as $val){
		$dirPath="{$currentDirectory}/{$title}/{$type}/{$val}";
		if(!is_dir($dirPath)){
			continue;
		}
		if(substr($dirPath,-2)==="/."){
			continue;
		}
		if(substr($dirPath,-3)==="/.."){
			continue;
		}
		$dirs[]=$val;
	}
	$htmls=[];
	//html出力
	$htmls[]="<li><a href=\"./../../\">上へ</a></li>";
	// <a href="./xxxx/">2015-01-01</a> 2015-01-01 00:00:00<br>
	$dirs=array_reverse($dirs);
	foreach($dirs as $dir){
		$addText="";
		$dirTime=stat("{$currentDirectory}/{$title}/{$type}/{$dir}/");
		$addText.="<li>";
		$addText.="<a href=\"./{$dir}/\">{$dir}</a>";
		$addText.="<small>".date("Y/m/d(D)H:i:s",$dirTime["mtime"])."</small>";
		$addText.="<br>";
		$addText.="</li>";
		$addText.="";
		$htmls[]=$addText;
	}
?>
<!DOCTYPE html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title><?=("{$title}/{$type}");?></title>
<style>
ul>li>small{margin-left:1em;}
ul>li{font-family:monospace;font-size:large;line-height:2em;}
ul{padding-left:1em;}
</style>
<ul><?php foreach($htmls as $val){print($val);}?></ul>
<?php
}else{
	//タイトル/タイプの一覧を表示する
	$dirRaw=scandir("{$currentDirectory}");
	$dirs=[];
	foreach($dirRaw as $val){
		$dirPath="{$currentDirectory}/{$val}";
		if(!is_dir($dirPath)){
			continue;
		}
		if(substr($dirPath,-2)==="/."){
			continue;
		}
		if(substr($dirPath,-3)==="/.."){
			continue;
		}
		$dirs[]=$val;
	}
	$htmls=[];
	//html出力
	foreach($dirs as $dir){
		$addText="";
		$dirTime=stat("{$currentDirectory}/{$dir}");
		$addText.="<h2>{$dir}</h2>";
		$addText.="<a href=\"./{$dir}/hot/\">hot</a> ";
		$addText.="<a href=\"./{$dir}/new/\">new</a> ";
		$addText.="<small>".date("Y/m/d(D)H:i:s",$dirTime["mtime"])." up</small>";
		$addText.="<hr>";
		$addText.="\n";
		$htmls[]=$addText;
	}
?>
<!DOCTYPE html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title>Miiverse一覧</title>
<style>
h2{margin:0.4em auto;}
</style>
<?php foreach($htmls as $val){print($val);}?>
<?php
}
