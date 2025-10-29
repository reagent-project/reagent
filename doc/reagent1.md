# Reagent 1

When using React 18 or older you can provide the
react dependencies using [Cljsjs](http://cljsjs.github.io/) React packages to your project:

```
[cljsjs/react "18.3.1-1"]
[cljsjs/react-dom "18.3.1-1"]
```

Note: Reagent 1 is tested against React 18, using the compatibility mode (i.e.,
not using `createRoot` / concurrent mode), but should be compatible with other
versions.
