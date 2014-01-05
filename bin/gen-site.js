#! /usr/bin/env node

var fs = require("fs");
var vm = require('vm');

var srcFile = "target/cljs-client.js";
var src = fs.readFileSync(srcFile);
vm.runInThisContext(src, srcFile);

console.log('Generating page');
var main = demo.genpage();

var head = ['<head>',
            '<meta charset="utf-8">',
            '<title>This is Cloact</title>',
            '<link rel="stylesheet" href="examples/todomvc/todos.css">',
            '<link rel="stylesheet" href="examples/todomvc/todosanim.css">',
            '<link rel="stylesheet" href="examples/simple/example.css">',
            '<link rel="stylesheet" href="site/demo.css">',
            '</head>'].join('\n');

var body = ['<body>',
            main,
            '<script type="text/javascript" src="site/demo.js"></script>',
            '<script type="text/javascript">',
            'demo.mountdemo();',
            '</script>',
            '</body>'].join('\n');

var html = ['<html>', head, body, '</html>'].join('\n');

console.log('Writing site');
fs.writeFileSync("index.html", html);
fs.writeFileSync("site/demo.js", src);
console.log('Wrote site');
