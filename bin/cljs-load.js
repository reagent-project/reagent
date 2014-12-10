
var fs = require("fs");
var vm = require("vm");
var path = require("path");

var loadSrc = function (mainFile, outputDir, devModule) {
    var src = fs.readFileSync(mainFile);
    var googDir = path.join(outputDir, "goog");
    var optNone = false;
    if (outputDir) {
        optNone = fs.existsSync(path.join(googDir, "deps.js"));
    }

    if (optNone) {
        var cwd = process.cwd();
        if (!global.goog) global.goog = {};

        global.CLOSURE_IMPORT_SCRIPT = function (src) {
            require(path.resolve(path.resolve(
                cwd, path.join(googDir, src))));
            return true;
        };

        var f = path.join(googDir, "base.js");
        vm.runInThisContext(fs.readFileSync(f), f);
        require(path.resolve(cwd, mainFile));
        goog.require(devModule);
    } else {
        global.globalNodeRequire = require;

        vm.runInThisContext("(function (require) {"
                            + src
                            + "\n})(globalNodeRequire);", mainFile);
    }
    return optNone;
};

exports.load = loadSrc;
