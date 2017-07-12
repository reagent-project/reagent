/**
 * @fileoverview Closure Compiler externs for Facebook ReactDOMServer.js DOM 0.14.0
 * @see http://reactjs.org
 * @externs
 */

/**
 * The ReactDOMServer global object.
 *
 * @type {!Object}
 * @const
 */
var ReactDOMServer = {};


/**
 * The current version of ReactDOMServer.
 *
 * @type {string}
 * @const
 */
ReactDOMServer.version;

/**
 * Render a ReactElement to its initial HTML.
 *
 * @param {React.ReactElement} element
 * @return {string}
 */
ReactDOMServer.renderToString = function(element) {};


/**
 * Similar to renderToString, except this doesn't create extra DOM attributes
 * such as data-react-id, that React uses internally. This is useful if you want
 * to use React as a simple static page generator, as stripping away the extra
 * attributes can save lots of bytes.
 *
 * @param {React.ReactElement} element
 * @return {string}
 */
ReactDOMServer.renderToStaticMarkup = function(element) {};
