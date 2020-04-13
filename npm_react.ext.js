// Extern file for React with Node module processing
//
// Compared to Cljsjs extern, this doesn't contain the public API, which
// can be optimized here. This just fixes the React
// internal methods and properties that are created dynamically.
//
// TODO: Package separately and remove these from Cljsjs externs?

/**
 * React event system creates plugins and event properties dynamically.
 * These externs are needed when consuming React as a JavaScript module
 * in light of new ClojureScript compiler additions (as of version 1.9.456).
 * See the following link for an example.
 * https://github.com/facebook/react/blob/c7129c/src/renderers/dom/shared/eventPlugins/SimpleEventPlugin.js#L43
 */
var ResponderEventPlugin;
var SimpleEventPlugin;
var TapEventPlugin;
var EnterLeaveEventPlugin;
var ChangeEventPlugin;
var SelectEventPlugin;
var BeforeInputEventPlugin;

var bubbled;
var captured;
var topAbort;
var topAnimationEnd;
var topAnimationIteration;
var topAnimationStart;
var topBlur;
var topCancel;
var topCanPlay;
var topCanPlayThrough;
var topClick;
var topClose;
var topContextMenu;
var topCopy;
var topCut;
var topDoubleClick;
var topDrag;
var topDragEnd;
var topDragEnter;
var topDragExit;
var topDragLeave;
var topDragOver;
var topDragStart;
var topDrop;
var topDurationChange;
var topEmptied;
var topEncrypted;
var topEnded;
var topError;
var topFocus;
var topInput;
var topInvalid;
var topKeyDown;
var topKeyPress;
var topKeyUp;
var topLoad;
var topLoadedData;
var topLoadedMetadata;
var topLoadStart;
var topMouseDown;
var topMouseMove;
var topMouseOut;
var topMouseOver;
var topMouseUp;
var topPaste;
var topPause;
var topPlay;
var topPlaying;
var topProgress;
var topRateChange;
var topReset;
var topScroll;
var topSeeked;
var topSeeking;
var topStalled;
var topSubmit;
var topSuspend;
var topTimeUpdate;
var topTouchCancel;
var topTouchEnd;
var topTouchMove;
var topTouchStart;
var topTransitionEnd;
var topVolumeChange;
var topWaiting;
var topWheel;

// https://github.com/facebook/react/blob/master/packages/shared/isTextInputElement.js#L13-L29
// Closure will rename these properties during optimization
// But these are used dynamically to check against element props so they must not be renamed.
var isTextInputElement = {};
isTextInputElement.supportedInputTypes = {
  color: true,
  date: true,
  datetime: true,
  "datetime-local": true,
  email: true,
  month: true,
  number: true,
  password: true,
  range: true,
  search: true,
  tel: true,
  text: true,
  time: true,
  url: true,
  week: true
};

// Context methods are created dynamically.
React.Context = function() {};
React.Context.prototype.Provider = function() {};
React.Context.prototype.Consumer = function() {};

// Value returned from createRef has dynamically set `current` property.
// var ReactRef = {};
// ReactRef.current = {};


// Rest are required due to Reagent implementation.

// Lifecycle methods need to be declared, as Reagent creates these dynamically

var React = {};
React.Component = function() {};
React.Component.prototype.componentWillMount = function() {};
React.Component.prototype.UNSAFE_componentWillMount = function() {};
React.Component.prototype.componentDidMount = function(element) {};
React.Component.prototype.componentWillReceiveProps = function(nextProps) {};
React.Component.prototype.UNSAFE_componentWillReceiveProps = function(nextProps) {};
React.Component.prototype.shouldComponentUpdate = function(nextProps, nextState) {};
React.Component.prototype.componentWillUpdate = function(nextProps, nextState) {};
React.Component.prototype.UNSAFE_componentWillUpdate = function(nextProps, nextState) {};
React.Component.prototype.componentDidUpdate = function(prevProps, prevState, rootNode) {};
React.Component.prototype.componentWillUnmount = function() {};
React.Component.prototype.componentDidCatch = function(error, info) {};
React.Component.prototype.getDerivedStateFromProps = function() {};
React.Component.prototype.getDerivedStateFromError = function() {};
React.Component.prototype.getSnapshotBeforeUpdate = function() {};
React.Component.prototype.getInitialState = function() {};
React.Component.prototype.getDefaultProps = function() {};
React.Component.prototype.getChildContext = function() {};

// Reagent creates render method statically.
// React.Component.prototype.render = function() {};

// React.Component.prototype.props;
// React.Component.prototype.state;
// React.Component.prototype.refs;
// React.Component.prototype.propTypes;
React.Component.prototype.context;
React.Component.prototype.contextTypes;
React.Component.prototype.contextType;
React.Component.prototype.mixins;
React.Component.prototype.childContextTypes;

// Reagent also creates the attributes dynamically (from clj maps)
// but mostly the attributes match DOM built-in attributes anyway.
React.ReactAttribute = function() {};
React.ReactAttribute.ref = {};
React.ReactAttribute.dangerouslySetInnerHTML = {};
React.ReactAttribute.dangerouslySetInnerHTML.__html = {};

