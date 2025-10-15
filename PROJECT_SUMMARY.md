# PROJECT SUMMARY: Reagent

## Overview
Reagent is a minimalistic ClojureScript interface to React that provides a functional, reactive programming model for building web user interfaces. It uses Hiccup-style syntax (vectors) to represent React components and provides reactive atoms (ratoms) for state management with automatic re-rendering.

## Key Features
- **Hiccup-style templating**: Write HTML using Clojure vectors `[:div "Hello"]`
- **Reactive programming**: Built-in reactivity using ratoms that automatically trigger re-renders
- **React interoperability**: Easy integration with existing React components
- **Minimal overhead**: Thin wrapper around React with small footprint
- **Functional approach**: Immutable data structures and functional components

## Project Structure

### Core Source Files (`src/reagent/`)
- **`core.cljs`**: Main public API with 30+ functions including `atom`, `create-element`, `as-element`, `track`, `cursor`, `wrap`, `class-names`, `merge-props`, and React interop utilities
- **`core.clj`**: Macro definitions for ClojureScript compilation
- **`ratom.cljs`**: Reactive atom implementation with dependency tracking and batched updates
- **`dom.cljs`**: DOM rendering functions, React DOM integration
- **`debug.cljs`**: Development debugging utilities with assertion macros

### Implementation Details (`src/reagent/impl/`)
- **`template.cljs`**: Hiccup vector to React element conversion logic
- **`component.cljs`**: React component lifecycle and state management
- **`batching.cljs`**: Update batching system for performance optimization
- **`util.cljs`**: Utility functions for props merging and component helpers
- **`input.cljs`**: Input component handling for controlled components
- **`protocols.cljs`**: Core protocols for reactive atoms and components

### Configuration & Dependencies
- **`project.clj`**: Leiningen build configuration with ClojureScript setup
- **`deps.edn`**: tools.deps configuration for modern Clojure CLI workflows
- **`shadow-cljs.edn`**: Shadow CLJS build configuration with browser, test, and prerender targets
- **`bb.edn`**: Babashka task configuration for development workflows
- **`examples/`**: Sample applications demonstrating different Reagent patterns

## Key Dependencies
- **ClojureScript 1.11.132**: Core language platform
- **React 18.2.0-1**: Underlying React framework via cljsjs
- **React DOM 18.2.0-1**: DOM rendering support via cljsjs

## Core APIs and Usage Patterns

### Basic Component Creation
```clojure
(ns my-app.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; Simple component
(defn hello-world []
  [:div "Hello, world!"])

;; Component with parameters
(defn greeting [name]
  [:h1 "Hello, " name "!"])
```

### Reactive State Management
```clojure
;; Create reactive atom
(def app-state (r/atom {:counter 0}))

;; Component using reactive state
(defn counter []
  [:div
   [:p "Count: " (:counter @app-state)]
   [:button {:on-click #(swap! app-state update :counter inc)} "+"]])
```

### Advanced Reactive Features
```clojure
;; Computed values that update automatically
(def doubled-counter (r/track #(* 2 (:counter @app-state))))

;; Eager tracking (starts immediately)
(def eager-tracker (r/track! #(println "Counter changed:" (:counter @app-state))))

;; Cursor for nested state access
(def counter-cursor (r/cursor app-state [:counter]))

;; Wrap values with custom reset behavior
(def wrapped-value (r/wrap (:counter @app-state) 
                           swap! app-state assoc :counter))

;; Component lifecycle with ratoms
(defn with-local-state []
  (r/with-let [local-state (r/atom 0)]
    [:div 
     [:p "Local: " @local-state]
     [:button {:on-click #(swap! local-state inc)} "Increment"]]))

;; Recursive swap for complex state updates
(r/rswap! app-state update :counter inc)
```

### React Interoperability
```clojure
;; Use React components in Reagent
(def react-component (r/adapt-react-class js/SomeReactComponent))

;; Use Reagent components in React
(def reagent-for-react (r/reactify-component my-reagent-component))

;; Create React elements directly
(r/create-element "div" #js{:className "foo"} "Hello")

;; Create React classes with full lifecycle control
(def my-class 
  (r/create-class 
    {:component-did-mount (fn [this] (println "Mounted!"))
     :reagent-render (fn [] [:div "Custom class component"])}))

;; Utility functions for props and styling
(r/class-names "btn" "btn-primary" {:active true})
(r/merge-props {:class "base"} {:class "extra" :id "test"})

;; Safe HTML rendering
(r/unsafe-html "<strong>Bold text</strong>")
```

## Architecture Overview

### Reactive System
1. **RAtom**: Core reactive atom type that tracks dependencies
2. **Track**: Computed values that automatically update when dependencies change
3. **Batching**: Updates are batched and applied after render for performance
4. **Dependency Graph**: Automatic tracking of which components depend on which atoms

### Component Rendering
1. **Template Compilation**: Hiccup vectors converted to React elements
2. **Component Wrapping**: Reagent functions wrapped as React components
3. **State Integration**: React state integrated with Reagent ratoms
4. **Lifecycle Management**: React lifecycle methods available when needed

### Update Flow
```
Atom Change → Dependency Tracking → Batched Update → Component Re-render
```

## Development Workflow

### Setting Up Development
```bash
# Using Shadow CLJS (modern approach)
npx shadow-cljs watch client

# Using Babashka tasks
bb shadow      # Start Shadow CLJS watch mode
bb build-report # Generate build analysis report

# Running tests
lein doo chrome-headless test auto
npx shadow-cljs compile test  # Shadow CLJS test compilation
```

### REPL-Driven Development
- Start REPL with `lein repl` or `clj -M:dev`
- Connect to ClojureScript REPL for live coding
- Hot-reloading with Figwheel automatically updates components when code changes
- Reactive atoms automatically trigger re-renders when state changes

### Building for Production
```bash
lein build              # Creates optimized build
lein build-examples     # Builds example applications
```

## Implementation Patterns

### Component Design Patterns
1. **Pure Functions**: Components as pure functions of props and state
2. **Reactive State**: Use ratoms for local and global state
3. **Props Destructuring**: `(defn my-comp [{:keys [title content]}] ...)`
4. **Conditional Rendering**: Use `when`, `if`, and `case` directly in hiccup

### State Management Patterns
1. **Global App State**: Single ratom with nested maps
2. **Cursors**: For accessing nested state without re-rendering parent
3. **Computed Values**: Use `track` for derived state
4. **Local Component State**: Use `r/with-let` for component-local atoms

### Performance Optimization
1. **Batched Updates**: Updates automatically batched by Reagent with `flush` and `after-render` control
2. **Minimal Re-renders**: Only components using changed atoms re-render
3. **React Keys**: Use `:key` metadata for list items
4. **shouldComponentUpdate**: Use `React.memo` equivalent with `r/create-class`
5. **Manual Rendering Control**: Use `r/flush` to force immediate renders, `r/next-tick` and `r/after-render` for timing control
6. **Compiler Customization**: Create custom compilers with `r/create-compiler` for specialized rendering behavior

## Extension Points

### Custom Components
- Extend with React lifecycle methods using `r/create-class`
- Create reusable component libraries
- Build higher-order components

### State Management Extensions  
- Custom ratom-like types implementing `IReactiveAtom`
- Middleware for state changes and debugging
- Integration with external state management (Redux, etc.)

### React Ecosystem Integration
- Wrap third-party React components with `r/adapt-react-class`
- Export Reagent components for React with `r/reactify-component`
- Use React hooks through interop

### Development Tools
- Custom debugging and inspection tools
- Development-only components and utilities
- Performance monitoring and profiling extensions

## Testing Strategy
- Unit tests in `test/` directory using ClojureScript test framework
- Component testing with simulated user interactions
- Integration tests with full application state
- Use `doo` for automated browser testing

## Common Gotchas
1. **Deref in Render**: Always deref atoms (`@atom`) in component render functions
2. **Event Handlers**: Use anonymous functions `#(...)` or `partial` for event handlers
3. **Keys for Lists**: Always provide `:key` metadata for dynamic lists
4. **State Updates**: Use `swap!` or `reset!`, never mutate ratom contents directly
5. **Component Names**: Use kebab-case for component function names
