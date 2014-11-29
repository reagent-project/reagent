
var cljsLoad = require("./cljs-load");

var srcFile = "outsite/public/js/main.js";
var outputDirectory = "outsite/public/js/out/";
var moduleName = "devsetup";

var beep = "\u0007";

var gensite = function () {
    console.log("Loading " + srcFile);
    var optNone = cljsLoad.load(srcFile, outputDirectory, moduleName);
    sitetools.genpages({"opt-none": optNone});
}

var compileFail = function () {
  var msg = process.argv[process.argv.length - 1];
  if (msg && msg.match(/failed/)) {
    console.log("Compilation failed" + beep);
    return true;
  }
};

if (!compileFail()) {
  try {
    gensite();
  } catch (e) {
    console.log(e + beep);
    console.error(e.stack);
  }
}
