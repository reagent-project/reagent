
var fs = require("fs");
var vm = require("vm");
var path = require("path");

var run = function (src) {
    global.require = require;
    vm.runInThisContext(fs.readFileSync(src), src);
}

var imported = {};

var loadSrc = function (mainFile, outputDir, devFile) {
    var googDir = path.join(outputDir, "goog");
    var optNone = false;
    if (outputDir) {
        optNone = fs.existsSync(path.join(googDir, "deps.js"));
    }
    if (optNone) {
        var cwd = process.cwd();
        if (!global.goog) {
            global.goog = {};
        }
        global.CLOSURE_IMPORT_SCRIPT = function (src) {
            var s = path.resolve(path.resolve(cwd, path.join(googDir, src)));
            if (!(s in imported)) {
                imported[s] = true;
                run(s);
                return true;
            }
        };

        run(path.join(googDir, "base.js"));
        run(path.join(outputDir, "cljs_deps.js"));
        run(path.join(outputDir, devFile));
    } else {
        run(mainFile);
    }
    return optNone;
};

exports.load = loadSrc;
