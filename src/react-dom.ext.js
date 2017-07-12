/**
 * @fileoverview Closure Compiler externs for Facebook ReactDOM.js DOM 0.14.0
 * @see http://reactjs.org
 * @externs
 */


/**
 * The ReactDOM global object.
 *
 * @type {!Object}
 * @const
 * @suppress {const|duplicate}
 */
var ReactDOM = {};


/**
 * The current version of ReactDOM.
 *
 * @type {string}
 * @const
 */
ReactDOM.version;


/**
 * @param {React.ReactComponent} container
 * @param {Element} mountPoint
 * @param {Function=} opt_callback
 * @return {React.ReactComponent}
 */
ReactDOM.render = function(container, mountPoint, opt_callback) {};


/**
 * @param {Element} container
 * @return {boolean}
 */
ReactDOM.unmountComponentAtNode = function(container) {};


/**
 * @param {React.ReactComponent} component
 * @return {Element}
 */
ReactDOM.findDOMNode = function(component) {};


/**
 * Call the provided function in a context within which calls to `setState`
 * and friends are batched such that components aren't updated unnecessarily.
 *
 * @param {Function} callback Function which calls `setState`, `forceUpdate`, etc.
 * @param {*=} opt_a Optional argument to pass to the callback.
 * @param {*=} opt_b Optional argument to pass to the callback.
 * @param {*=} opt_c Optional argument to pass to the callback.
 * @param {*=} opt_d Optional argument to pass to the callback.
 * @param {*=} opt_e Optional argument to pass to the callback.
 * @param {*=} opt_f Optional argument to pass to the callback.
 */
ReactDOM.unstable_batchedUpdates = function(callback, opt_a, opt_b, opt_c, opt_d, opt_e, opt_f) {};

/**
 * Renders a React component into the DOM in the supplied `container`.
 *
 * If the React component was previously rendered into `container`, this will
 * perform an update on it and only mutate the DOM as necessary to reflect the
 * latest React component.
 *
 * @param {React.ReactComponent} parentComponent The conceptual parent of this render tree.
 * @param {React.ReactElement} nextElement Component element to render.
 * @param {Element} container DOM element to render into.
 * @param {Function=} opt_callback function triggered on completion
 * @return {React.ReactComponent} Component instance rendered in `container`.
 */
ReactDOM.unstable_renderSubtreeIntoContainer = function(parentComponent, nextElement, container, opt_callback) {};
