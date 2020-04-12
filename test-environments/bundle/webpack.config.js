module.exports = {
  mode: 'development',
  entry: './target/bundle/resources/public/js/out/index.js',
  output: {
    path: __dirname + "/target/bundle/resources/public/js/",
    filename: 'main.js'
  }
};
