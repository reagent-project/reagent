module.exports = {
  mode: 'production',
  entry: './target/lite-adv/resources/public/js/out/index.js',
  output: {
    path: __dirname + "/../../target/lite-adv/resources/public/js/",
    filename: 'main.js'
  }
};

