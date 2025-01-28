module.exports = {
  mode: 'production',
  entry: './target/bundle-adv/resources/public/js/out/index.js',
  output: {
    path: __dirname + "/../../target/bundle-adv/resources/public/js/",
    filename: 'main.js'
  }
};
