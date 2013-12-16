#! /usr/bin/env node

/*
  This is a huge hack: convert React from js to cljs by embedding
  in (js* ), and making it safe for Closure advanced optimization
  by adding a lot of @expose annotations.
 */

var fs = require('fs');

var ns = "cloact.react";
var destFile = "src/cloact/React.cljs";

var srcfile = "bower_components/react/react-with-addons.js";
var React = require("../" + srcfile);
var src = "" + fs.readFileSync(srcfile);

// Names that might clash with module names
// XXX: meta might be broken now
var skipNames = ['var', 'object', 'base', 'map', 'meta', 'source', 'time'];

// Property names from DefaultDOMPropertyConfig.js
// https://github.com/facebook/react/blob/master/src/dom/DefaultDOMPropertyConfig.js
var propNames = ['allowFullScreen', 'autoComplete', 'autoFocus', 'autoPlay',
                 'charSet', 'encType', 'icon', 'preload', 'radioGroup', 'role',
                 'spellCheck', 'wmode',
                 'autoCapitalize',
                 'cx', 'cy', 'd', 'fx', 'fy', 'gradientTransform',
                 'gradientUnits', 'points', 'r', 'rx', 'ry', 'spreadMethod',
                 'stopColor', 'stopOpacity', 'strokeLinecap', 'strokeWidth',
                 'viewBox', 'x1', 'x2', 'x', 'y1', 'y2', 'y',
                 'componentConstructor', 'displayName'
                ];

var getNames = function (obj) {
    var res = [];
    for (var x in obj) {
        res.push(x);
    }
    return res;
};

var stripAnnotations = function (src) {
    // Stop bloody google closure complaining about jsdoc tags
    // by removing "@" in block comments.
    return src.replace(/\/\*([^*]|\*(?!\/))*\*\//gm, function (s) {
        return s.replace(/@/g, '');
    });
}

var quote = function (src) {
    return src.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

var getLiteralKeys = function (src) {
    var res = {};
    src.replace(/([a-zA-Z$_][a-zA-Z0-9$_]*):/gm, function (s, key) {
        res[key] = true;
    });
    return getNames(res);
}

var eventNames = function (keys) {
    return keys.filter(function (x) {
        return x.match(/^on[A-Z]/);
    });
}

var ReactCompositeComponentInterface = {
  mixins: null,
  propTypes: null,
  getDefaultProps: null,
  getInitialState: null,
  render: null,
  componentWillMount: null,
  componentDidMount: null,
  componentWillReceiveProps: null,
  shouldComponentUpdate: null,
  componentWillUpdate: null,
  componentDidUpdate: null,
  componentWillUnmount: null,
  updateComponent: null
}

var printCljs = function () {
    var stripped = stripAnnotations(src);
    var quoted = quote(stripped);
    var domNames = getNames(React.DOM);
    var evNames = eventNames(getLiteralKeys(src));
    var iNames = getNames(ReactCompositeComponentInterface);
    var names = [].concat(domNames, evNames, iNames, propNames);
    var fnames = names.filter(function (n) {
        return skipNames.indexOf(n) == -1;
    });

    res = ["(ns " + ns + ")",
           '(js* "', 
           '/**',
           ' * @fileoverview React.js packaged for clojurescript',
           ' * @suppress {nonStandardJsDocs|checkRegExp}',
           ' */',
           quoted,
           '(function () {',
           'var X = {};'].concat(fnames.map(function (x) {
               return '/** @expose */\nX.' + x + " = true;"
           })).concat([
               '})();',
               ns + ".React = (typeof(window) != 'undefined' ? window.React : global.React);",
               '")',
          ]);
    return res.join('\n');
}

fs.writeFileSync(destFile, printCljs());
