#! /usr/bin/env node

var fs = require("fs");
var vm = require('vm');

var srcFile = "target/cljs-client.js";
var src = fs.readFileSync(srcFile);
vm.runInThisContext(src, srcFile);

console.log('Generating page');
var main = demo.genpage();

var ts = '?' + Date.now();

var cssFiles = ['examples/todomvc/todos.css',
                'examples/todomvc/todosanim.css',
                'examples/simple/example.css',
                'site/demo.css'];

var head = ['<head>',
            '<meta charset="utf-8">',
            '<title>Cloact: Minimalistic React for ClojureScript</title>',
            '<link rel="stylesheet" href="site/democss.css' + ts + '">',
            '</head>'].join('\n');

var body = ['<body>',
            main,
            '<script type="text/javascript" src="site/demo.js' + ts + '"></script>',
            '<script type="text/javascript">',
            'setTimeout(demo.mountdemo, 200);',
            '</script>',
            '</body>'].join('\n');

var html = ['<!doctype html>', '<html>', head, body, '</html>'].join('\n');

console.log('Writing site');
fs.writeFileSync("index.html", html);
fs.writeFileSync("site/demo.js", src);
fs.writeFileSync("site/democss.css",
                 cssFiles.map(function (x) {
                     return fs.readFileSync(x);
                 }).join("\n"));
console.log('Wrote site');
