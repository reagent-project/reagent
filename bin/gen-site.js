#! /usr/bin/env node

var fs = require("fs");
var vm = require('vm');

var cssFiles = ['examples/todomvc/todos.css',
                'examples/todomvc/todosanim.css',
                'examples/simple/example.css',
                'site/demo.css'];

var srcFile = "target/cljs-client.js";
var src = fs.readFileSync(srcFile);

var clj_genpages = function (profile) {
    if (typeof demo === 'undefined') {
        vm.runInThisContext(src, srcFile);
    }
    return demo.genpages(profile);
}

var generate = function () {
    var pages = clj_genpages();
    Object.keys(pages).map(function (page) {
        fs.writeFileSync(page, pages[page]);
    });
    fs.writeFileSync("assets/demo.js", src);
    fs.writeFileSync("assets/demo.css",
                     cssFiles.map(function (x) {
                         return fs.readFileSync(x);
                     }).join("\n"));
    console.log('Wrote site');
};

var compileOk = function () {
    var msg = process.argv[2];
    if (msg && msg.match(/failed/)) {
        console.log("Compilation failed");
        // beep
        console.log('\u0007');
        return false;
    }
    return true;
};

if (compileOk()) {
    console.log('Writing site');
    try {
        generate();
    } catch (e) {
        console.log('\u0007');
        console.error(e.stack);
    }
}
