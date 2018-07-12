<?php
include_once("webid-auth/WebIdAuth.php");
include_once("webid-auth/WebIdData.php");

// add new beta accounts here
$beta_accounts = array(
	"http://kurzum.net/webid.ttl" => "kurzum",
	"http://kurzum.net/webid.ttl" => "kurzum"
);


// authentication
try
{
	$webidauth = WebIdAuth::create($_SERVER["SSL_CLIENT_CERT"]);
	if($webidauth->comparePublicKeys()) {
		$webid = new WebIdData($webidauth->webid_uri, $webidauth->webid_data);
		echo "SUCCESS\n";
		echo "Your Certificate and WebId are valid.\n";
		echo "WebId: ".$webidauth->webid_uri."\n";
		echo "Name: ".$webid->getFoafName()."\n";
		echo "Public Key: ".$webid->getPublicKey()."\n";
	} else {
		echo "Could not validate the signature of your Certificate with your WebId Public Key.\n";
	}
} catch(Exception $e) {
	echo $e;
}

// check if registered
if (!isset($beta_accounts[$webid->getUri()])){
	echo "not a beta_account, please write to spraetor@informatik.uni-leipzig.de to apply."; die;
}else {
	$username= $beta_accounts[$webid->getUri()];
}

// the url to main dataid
if(!isset($_GET['dataid'])|| $_GET['dataid']===0){
	echo "error, url of data id not set"; die;
}else{
	$dataidurl=$_GET['dataid'];
}


//parse dataid
$parser = ARC2::getTurtleParser();
$parser->parse($dataidurl);
$triples = $parser->getTriples();

// TODO get version and artifactid
$version = "1.0.0";
$artifactid = "test";

// dir 
$dir = "../releases/$username/$artifactid/";
mkdir($dir, 0777, true);

// write
/* Serializer instantiation */
$ser = ARC2::getTurtleSerializer();

/* Serialize a triples array */
$doc = $ser->getSerializedTriples($triples);

file_put_contents("$dir/$version.dataid.ttl");


