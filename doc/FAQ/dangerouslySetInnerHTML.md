### Question

How can I use React's [dangerouslySetInnerHTML](https://reactjs.org/docs/dom-elements.html#dangerouslysetinnerhtml) feature?

### Answer

A minimal (contrived example):

```clj
[:div
 {:dangerouslySetInnerHTML
  (r/unsafe-html "<image  height=\"600\" src=\"https://static1.squarespace.com/static/58f9c2fbd2b85759c7e4ec2f/5923cbe4be6594d8a0b033a9/5a0154a6ec212d85ddf7941f/1511246183022/mfsprout_20160406_1234-Print.jpg?format=1500w\"/>")}]
```

See [Security](../Security.md).

***

Up:  [FAQ Index](../README.md)
