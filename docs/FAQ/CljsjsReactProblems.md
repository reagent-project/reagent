# Question

Reagent doesn't work after updating dependencies.

# Answer

If you see errors about accessing `React` or `ReactDOM` object or some React method after you have updated your dependencies, the problem is probably conflicting `cljsjs/react` or `cljsjs/react-dom` versions. Other dependencies than Reagent might bring in versions which don't work with Reagent, or a library which only depends on one of the packages might cause React and ReactDOM versions to conflict.

To fix this you should check `lein deps :tree` or `boot show -d`, and check which version of Cljsjs React packages you have.

There are three alternative solutions:

1. Update all the packages that require Cljsjs React packages to use same (or compatible) versions as Reagent
2. Add `:exclusion [cljsjs/react cljsjs/react-dom]` to problematic dependencies, so only Reagent
will have transitive dependency on React packages
3. Add direct `cljsjs/react` and `cljsjs/react-dom` dependencies to your project, which will override any transitive dependencies

Note: `cljsjs/react-dom-server` package is deprecated but Reagent still depends on empty package for compatibility.

Note: For more information on how Leiningen and Boot resolve dependencies using Maven-resolver, read: https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html
